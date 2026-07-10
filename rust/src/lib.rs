use jni::JNIEnv;
use jni::objects::{JClass, JString, JObject, JValue};
use jni::sys::{jlong, jint, jfloat, jbyteArray};
use android_logger::Config;
use log::LevelFilter;
use std::time::Instant;
use std::path::{Path, PathBuf};

mod helper;
mod lang;
mod thermal;
mod filter;

use helper::{load_text_to_speech, load_voice_style, load_and_mix_voice_styles, TextToSpeech};
use thermal::{UnifiedThermalManager, SocClass};
use filter::FilterPipeline;

struct SupertonicEngine {
    tts: TextToSpeech,
    thermal: UnifiedThermalManager,
    last_rtf: f32,
}

// VULN-003 fix: Path sanitization helper
fn sanitize_path(path_str: &str) -> String {
    // 1. Handle null bytes which can terminate paths prematurely in some OS calls
    let sanitized_str = path_str.replace('\0', "");
    
    // 2. Basic URL decoding for common traversal patterns if encoded
    let sanitized_str = sanitized_str
        .replace("%2e", ".")
        .replace("%2E", ".")
        .replace("%2f", "/")
        .replace("%2F", "/")
        .replace("%5c", "\\")
        .replace("%5C", "\\");

    let path = Path::new(&sanitized_str);
    let mut sanitized = PathBuf::new();
    
    for component in path.components() {
        match component {
            std::path::Component::Normal(c) => sanitized.push(c),
            // Allow root but nothing that goes "up" or "stays" in a way that escapes
            std::path::Component::RootDir => sanitized.push(std::path::Component::RootDir),
            _ => {} // Explicitly ignore .. and .
        }
    }
    sanitized.to_string_lossy().into_owned()
}

#[no_mangle]
pub extern "system" fn Java_com_brahmadeo_supertonic_tts_SupertonicTTS_init(
    mut env: JNIEnv,
    _class: JClass,
    model_path: JString,
    _lib_path: JString, // Kept for signature compatibility
    ort_threads: jint,
    xnn_threads: jint,
) -> jlong {
    android_logger::init_once(
        Config::default().with_max_level(LevelFilter::Info),
    );

    let model_path: String = match env.get_string(&model_path) {
        Ok(s) => s.into(),
        Err(_) => {
            log::error!("Failed to get model_path string from JNI");
            return 0;
        }
    };
    
    // VULN-004 fix: Redact path in logs
    let model_name = Path::new(&model_path).file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_else(|| "unknown".to_string());
    log::info!("Initializing Supertonic Engine (ORT: {}, XNN: {}) with model: {}", ort_threads, xnn_threads, model_name);

    if !ort::init().with_name("supertonic-tts").commit() {
        log::warn!("ORT environment already initialized");
    }

    let tts = match load_text_to_speech(&model_path, false, true, ort_threads as usize, xnn_threads as usize) {
        Ok(t) => t,
        Err(e) => {
            log::error!("Failed to load TTS: {:?}", e);
            return 0;
        }
    };

    let thermal = UnifiedThermalManager::new();

    let engine = SupertonicEngine {
        tts,
        thermal,
        last_rtf: 1.0,
    };

    Box::into_raw(Box::new(engine)) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_brahmadeo_supertonic_tts_SupertonicTTS_synthesize(
    mut env: JNIEnv,
    instance: JObject,
    ptr: jlong,
    text: JString,
    lang: JString,
    style_path: JString,
    speed: jfloat,
    buffer_seconds: jfloat,
    steps: jint,
    gain: jfloat,
    sibilance_mode: jint,
) -> jbyteArray {
    // VULN-001 fix: Pointer validation
    if ptr == 0 {
        log::error!("Native synthesize called with null pointer");
        return std::ptr::null_mut();
    }

    let engine = unsafe { &mut *(ptr as *mut SupertonicEngine) };
    
    let text: String = match env.get_string(&text) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut()
    };
    let lang: String = match env.get_string(&lang) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut()
    };
    let style_path: String = match env.get_string(&style_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut()
    };

    engine.thermal.update(buffer_seconds, engine.last_rtf);

    let style = if style_path.contains(';') {
        let parts: Vec<&str> = style_path.split(';').collect();
        if parts.len() == 3 {
            let p1 = sanitize_path(parts[0]);
            let p2 = sanitize_path(parts[1]);
            let alpha = parts[2].parse::<f32>().unwrap_or(0.5);
            match load_and_mix_voice_styles(&p1, &p2, alpha) {
                Ok(s) => s,
                Err(e) => {
                    log::error!("Failed to mix voice styles: {:?}", e);
                    return std::ptr::null_mut();
                }
            }
        } else {
            log::error!("Invalid mix format. Expected: path1;path2;alpha");
            return std::ptr::null_mut();
        }
    } else {
        let p = sanitize_path(&style_path);
        match load_voice_style(&[p], false) {
            Ok(s) => s,
            Err(e) => {
                log::error!("Failed to load voice style: {:?}", e);
                return std::ptr::null_mut();
            }
        }
    };

    let start = Instant::now();
    let mut last_progress_call = Instant::now();
    
    let sample_rate = engine.tts.sample_rate as f32;
    let mut audio_filter = FilterPipeline::new(sibilance_mode, sample_rate);
    let mut audio_filter_callback = audio_filter.clone();

    let synthesis_result = engine.tts.call(&text, &lang, &style, steps as usize, speed, 0.1, |curr, total, audio_chunk| {
        // Check for cancellation
        let is_cancelled = match env.call_method(&instance, "isCancelled", "()Z", &[]) {
            Ok(v) => v.z().unwrap_or(false),
            Err(_) => false,
        };
        if is_cancelled {
            return false;
        }

        // Send audio chunk if available
        if let Some(audio) = audio_chunk {
            let mut pcm_data = Vec::with_capacity(audio.len() * 2);
            for &sample in audio {
                let filtered = audio_filter_callback.process(sample);
                let clamped = (filtered * gain).max(-1.0).min(1.0);
                let val = (clamped * 32767.0) as i16;
                pcm_data.extend_from_slice(&val.to_le_bytes());
            }
            
            if let Ok(output) = env.new_byte_array(pcm_data.len() as i32) {
                let i8_data: Vec<i8> = pcm_data.iter().map(|&b| b as i8).collect();
                let _ = env.set_byte_array_region(&output, 0, &i8_data);
                let _ = env.call_method(
                    &instance,
                    "notifyAudioChunk",
                    "([B)V",
                    &[JValue::Object(&output)],
                );
            }
        }

        // Only call Progress JNI every 100ms or at start/end/chunk
        if curr == 0 || curr == total || audio_chunk.is_some() || last_progress_call.elapsed().as_millis() > 100 {
            let _ = env.call_method(
                &instance,
                "notifyProgress",
                "(II)V",
                &[JValue::Int(curr as i32), JValue::Int(total as i32)],
            );
            last_progress_call = Instant::now();
        }
        true
    });

    match synthesis_result {
        Ok((wav_data, duration)) => {
            let elapsed = start.elapsed().as_secs_f32();
            if duration > 0.0 {
                engine.last_rtf = duration / elapsed;
                log::info!("Inference RTF: {:.2}x ({}s audio in {}s)", engine.last_rtf, duration, elapsed);
            }

            let mut pcm_data = Vec::with_capacity(wav_data.len() * 2);
            for &sample in &wav_data {
                let filtered = audio_filter.process(sample);
                let clamped = (filtered * gain).max(-1.0).min(1.0);
                let val = (clamped * 32767.0) as i16;
                pcm_data.extend_from_slice(&val.to_le_bytes());
            }

            match env.new_byte_array(pcm_data.len() as i32) {
                Ok(output) => {
                    let i8_data: Vec<i8> = pcm_data.iter().map(|&b| b as i8).collect();
                    if env.set_byte_array_region(&output, 0, &i8_data).is_ok() {
                        output.into_raw()
                    } else {
                        std::ptr::null_mut()
                    }
                }
                Err(_) => std::ptr::null_mut()
            }
        }
        Err(e) => {
            log::error!("Synthesis failed: {:?}", e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_brahmadeo_supertonic_tts_SupertonicTTS_getSocClass(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jint {
    if ptr == 0 { return -1; }
    let engine = unsafe { &mut *(ptr as *mut SupertonicEngine) };
    match engine.thermal.get_soc_class() {
        SocClass::Flagship => 3,
        SocClass::HighEnd => 2,
        SocClass::MidRange => 1,
        SocClass::LowEnd => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_brahmadeo_supertonic_tts_SupertonicTTS_getSampleRate(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jint {
    if ptr == 0 { return 24000; }
    let engine = unsafe { &mut *(ptr as *mut SupertonicEngine) };
    engine.tts.sample_rate as jint
}

#[no_mangle]
pub extern "system" fn Java_com_brahmadeo_supertonic_tts_SupertonicTTS_reset(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    if ptr != 0 {
        let engine = unsafe { &mut *(ptr as *mut SupertonicEngine) };
        // Reset thermal state or other buffers if needed
        engine.last_rtf = 1.0;
        log::info!("Engine state reset (JNI Handshake)");
    }
}

#[no_mangle]
pub extern "system" fn Java_com_brahmadeo_supertonic_tts_SupertonicTTS_isXnnpackEnabled(
    _env: JNIEnv,
    _class: JClass,
) -> bool {
    #[cfg(feature = "xnnpack")]
    {
        true
    }
    #[cfg(not(feature = "xnnpack"))]
    {
        false
    }
}

#[no_mangle]
pub extern "system" fn Java_com_brahmadeo_supertonic_tts_SupertonicTTS_close(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    if ptr != 0 {
        unsafe {
            // VULN-001 fix: Safety is mostly managed on Kotlin side by nulling ptr,
            // but here we just ensure we don't crash if called with 0 (done above).
            let _ = Box::from_raw(ptr as *mut SupertonicEngine);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_brahmadeo_supertonic_tts_SupertonicTTS_nativeChunkText(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
    lang: JString,
) -> jni::sys::jstring {
    let text: String = match env.get_string(&text) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let lang: String = match env.get_string(&lang) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let chunks = helper::chunk_text(&text, &lang, None);
    let joined = chunks.join("\u{001E}");

    match env.new_string(&joined) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
