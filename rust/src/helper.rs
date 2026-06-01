// ============================================================================ 
// TTS Helper Module - All utility functions and structures
// ============================================================================ 

use ndarray::{Array, Array3};
use serde::{Deserialize, Serialize};
use serde_json;
use std::fs::File;
use std::io::BufReader;
use std::path::Path;
use anyhow::{Result, Context, bail};
use crate::lang::get_normalizer;

// Available languages for multilingual TTS
pub const AVAILABLE_LANGS: &[&str] = &["en", "ko", "ja", "ar", "bg", "cs", "da", "de", "el", "es", "et", "fi", "fr", "hi", "hr", "hu", "id", "it", "lt", "lv", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "vi", "na"];

pub fn is_valid_lang(lang: &str) -> bool {
    AVAILABLE_LANGS.contains(&lang)
}
use hound::{WavWriter, WavSpec, SampleFormat};
use rand_distr::{Distribution, Normal};
use regex::Regex;

// ============================================================================ 
// Configuration Structures
// ============================================================================ 

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub ae: AEConfig,
    pub ttl: TTLConfig,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AEConfig {
    pub sample_rate: i32,
    pub base_chunk_size: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TTLConfig {
    pub chunk_compress_factor: i32,
    pub latent_dim: i32,
}

/// Load configuration from JSON file
pub fn load_cfgs<P: AsRef<Path>>(onnx_dir: P) -> Result<Config> {
    let cfg_path = onnx_dir.as_ref().join("tts.json");
    let file = File::open(cfg_path)?;
    let reader = BufReader::new(file);
    let cfgs: Config = serde_json::from_reader(reader)?;
    Ok(cfgs)
}

// ============================================================================ 
// Voice Style Data Structure
// ============================================================================ 

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VoiceStyleData {
    pub style_ttl: StyleComponent,
    pub style_dp: StyleComponent,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StyleComponent {
    pub data: Vec<Vec<Vec<f32>>>,
    pub dims: Vec<usize>,
    #[serde(rename = "type")]
    pub dtype: String,
}

// ============================================================================ 
// Unicode Text Processor
// ============================================================================ 

pub struct UnicodeProcessor {
    indexer: Vec<i64>,
}

impl UnicodeProcessor {
    pub fn new<P: AsRef<Path>>(unicode_indexer_json_path: P) -> Result<Self> {
        let file = File::open(unicode_indexer_json_path)?;
        let reader = BufReader::new(file);
        let indexer: Vec<i64> = serde_json::from_reader(reader)?;
        Ok(UnicodeProcessor { indexer })
    }

    pub fn call(&self, text_list: &[String], lang_list: &[String]) -> Result<(Vec<Vec<i64>>, Array3<f32>)> {
        let mut processed_texts: Vec<String> = Vec::new();
        for (text, lang) in text_list.iter().zip(lang_list.iter()) {
            processed_texts.push(preprocess_text(text, lang)?);
        }

        let text_ids_lengths: Vec<usize> = processed_texts
            .iter()
            .map(|t| t.chars().count())
            .collect();

        let max_len = *text_ids_lengths.iter().max().unwrap_or(&0);

        let mut text_ids = Vec::new();
        for text in &processed_texts {
            let mut row = vec![0i64; max_len];
            let unicode_vals = text_to_unicode_values(text);
            for (j, &val) in unicode_vals.iter().enumerate() {
                if val < self.indexer.len() {
                    let id = self.indexer[val];
                    row[j] = if id == -1 { 0 } else { id };
                } else {
                    row[j] = 0; // Default to space for unknown
                }
            }
            text_ids.push(row);
        }

        let text_mask = get_text_mask(&text_ids_lengths);

        Ok((text_ids, text_mask))
    }
}

pub fn preprocess_text(text: &str, lang: &str) -> Result<String> {
    // Validate language
    if !is_valid_lang(lang) {
        bail!("Invalid language: {}. Available: {:?}", lang, AVAILABLE_LANGS);
    }
    let normalizer = get_normalizer(lang);
    let mut preprocessed = normalizer.preprocess(text);
    if normalizer.should_wrap_tags() {
        preprocessed = format!("<{}>{}</{}>", lang, preprocessed, lang);
    }
    Ok(preprocessed)
}

pub fn text_to_unicode_values(text: &str) -> Vec<usize> {
    text.chars().map(|c| c as usize).collect()
}

pub fn length_to_mask(lengths: &[usize], max_len: Option<usize>) -> Array3<f32> {
    let bsz = lengths.len();
    let max_len = max_len.unwrap_or_else(|| *lengths.iter().max().unwrap_or(&0));

    let mut mask = Array3::<f32>::zeros((bsz, 1, max_len));
    for (i, &len) in lengths.iter().enumerate() {
        for j in 0..len.min(max_len) {
            mask[[i, 0, j]] = 1.0;
        }
    }
    mask
}

pub fn get_text_mask(text_ids_lengths: &[usize]) -> Array3<f32> {
    let max_len = *text_ids_lengths.iter().max().unwrap_or(&0);
    length_to_mask(text_ids_lengths, Some(max_len))
}

/// Sample noisy latent from normal distribution and apply mask
pub fn sample_noisy_latent(
    duration: &[f32],
    sample_rate: i32,
    base_chunk_size: i32,
    chunk_compress: i32,
    latent_dim: i32,
) -> (Array3<f32>, Array3<f32>) {
    let bsz = duration.len();
    let max_dur = duration.iter().fold(0.0f32, |a, &b| a.max(b));

    let wav_len_max = (max_dur * sample_rate as f32) as usize;
    let wav_lengths: Vec<usize> = duration
        .iter()
        .map(|&d| (d * sample_rate as f32) as usize)
        .collect();

    let chunk_size = (base_chunk_size * chunk_compress) as usize;
    let latent_len = (wav_len_max + chunk_size - 1) / chunk_size;
    let latent_dim_val = (latent_dim * chunk_compress) as usize;

    let mut noisy_latent = Array3::<f32>::zeros((bsz, latent_dim_val, latent_len));

    // Standard temperature (1.0) for the diffusion sampling process
    let normal = Normal::new(0.0, 1.0).unwrap();
    let mut rng = rand::thread_rng();

    for b in 0..bsz {
        for d in 0..latent_dim_val {
            for t in 0..latent_len {
                noisy_latent[[b, d, t]] = normal.sample(&mut rng);
            }
        }
    }

    let latent_lengths: Vec<usize> = wav_lengths
        .iter()
        .map(|&len| (len + chunk_size - 1) / chunk_size)
        .collect();

    let latent_mask = length_to_mask(&latent_lengths, Some(latent_len));

    // Apply mask
    for b in 0..bsz {
        for d in 0..latent_dim_val {
            for t in 0..latent_len {
                noisy_latent[[b, d, t]] *= latent_mask[[b, 0, t]];
            }
        }
    }

    (noisy_latent, latent_mask)
}

// ============================================================================
// WAV File I/O
// ============================================================================

#[allow(dead_code)]
pub fn write_wav_file<P: AsRef<Path>>(
    filename: P,
    audio_data: &[f32],
    sample_rate: i32,
) -> Result<()> {
    let spec = WavSpec {
        channels: 1,
        sample_rate: sample_rate as u32,
        bits_per_sample: 16,
        sample_format: SampleFormat::Int,
    };

    let mut writer = WavWriter::create(filename, spec)?;

    for &sample in audio_data {
        let clamped = sample.max(-1.0).min(1.0);
        let val = (clamped * 32767.0) as i16;
        writer.write_sample(val)?;
    }

    writer.finalize()?;
    Ok(())
}

// ============================================================================ 
// Text Chunking
// ============================================================================ 

pub fn chunk_text(text: &str, lang: &str, max_len: Option<usize>) -> Vec<String> {
    let normalizer = get_normalizer(lang);
    let max_len = max_len.unwrap_or_else(|| normalizer.max_chunk_len());
    let text = text.trim();
    
    if text.is_empty() {
        return vec![String::new()];
    }

    // Split by paragraphs
    static PARA_RE: std::sync::OnceLock<Regex> = std::sync::OnceLock::new();
    let para_re = PARA_RE.get_or_init(|| Regex::new(r"\n\s*\n").unwrap());
    let paragraphs_raw: Vec<&str> = para_re.split(text).collect();
    let mut paragraphs = Vec::new();
    let mut current_para = String::new();

    for para in paragraphs_raw {
        let p = para.trim();
        if p.is_empty() {
            continue;
        }

        if !current_para.is_empty() {
            if current_para.len() + p.len() + 2 > max_len || p.len() >= 80 {
                paragraphs.push(current_para.clone());
                current_para.clear();
            } else {
                let ends_with_punc = current_para.ends_with('.') || current_para.ends_with('!') || current_para.ends_with('?') || current_para.ends_with('।');
                if !ends_with_punc {
                    current_para.push_str(" .");
                }
                current_para.push(' ');
                current_para.push_str(p);
                continue;
            }
        }

        if p.len() < 80 {
            current_para.push_str(p);
        } else {
            paragraphs.push(p.to_string());
        }
    }
    if !current_para.is_empty() {
        paragraphs.push(current_para);
    }

    let mut chunks = Vec::new();

    for para in &paragraphs {
        let para = para.trim();
        if para.is_empty() {
            continue;
        }

        if para.len() <= max_len {
            chunks.push(para.to_string());
            continue;
        }

        // Split by sentences
        let sentences = split_sentences(para, lang);
        let mut current = String::new();
        let mut current_len = 0;

        for sentence in sentences {
            let sentence = sentence.trim();
            if sentence.is_empty() {
                continue;
            }

            let sentence_len = sentence.len();
            if sentence_len > max_len {
                // If sentence is longer than max_len, split by comma or space
                if !current.is_empty() {
                    chunks.push(current.trim().to_string());
                    current.clear();
                    current_len = 0;
                }

                // Try splitting by comma
                let parts: Vec<&str> = sentence.split(',').collect();
                for part in parts {
                    let part = part.trim();
                    if part.is_empty() {
                        continue;
                    }

                    let part_len = part.len();
                    if part_len > max_len {
                        // Split by space as last resort
                        let words: Vec<&str> = part.split_whitespace().collect();
                        let mut word_chunk = String::new();
                        let mut word_chunk_len = 0;

                        for word in words {
                            let word_len = word.len();
                            if word_chunk_len + word_len + 1 > max_len && !word_chunk.is_empty() {
                                chunks.push(word_chunk.trim().to_string());
                                word_chunk.clear();
                                word_chunk_len = 0;
                            }

                            if !word_chunk.is_empty() {
                                word_chunk.push(' ');
                                word_chunk_len += 1;
                            }
                            word_chunk.push_str(word);
                            word_chunk_len += word_len;
                        }

                        if !word_chunk.is_empty() {
                            chunks.push(word_chunk.trim().to_string());
                        }
                    } else {
                        if current_len + part_len + 1 > max_len && !current.is_empty() {
                            chunks.push(current.trim().to_string());
                            current.clear();
                            current_len = 0;
                        }

                        if !current.is_empty() {
                            current.push_str(", ");
                            current_len += 2;
                        }
                        current.push_str(part);
                        current_len += part_len;
                    }
                }
                continue;
            }

            if current_len + sentence_len + 1 > max_len && !current.is_empty() {
                chunks.push(current.trim().to_string());
                current.clear();
                current_len = 0;
            }

            if !current.is_empty() {
                current.push(' ');
                current_len += 1;
            }
            current.push_str(sentence);
            current_len += sentence_len;
        }

        if !current.is_empty() {
            chunks.push(current.trim().to_string());
        }
    }

    if chunks.is_empty() {
        vec![String::new()]
    } else {
        chunks
    }
}

fn split_sentences(text: &str, lang: &str) -> Vec<String> {
    get_normalizer(lang).split_sentences(text)
}

// ============================================================================
// Utility Functions
// ============================================================================

#[allow(dead_code)]
pub fn timer<F, T>(name: &str, f: F) -> Result<T>
where
    F: FnOnce() -> Result<T>,
{
    let start = std::time::Instant::now();
    println!("{}...", name);
    let result = f()?;
    let elapsed = start.elapsed().as_secs_f64();
    println!("  -> {} completed in {:.2} sec", name, elapsed);
    Ok(result)
}

#[allow(dead_code)]
pub fn sanitize_filename(text: &str, max_len: usize) -> String {
    // Take first max_len characters (Unicode code points, not bytes)
    text.chars()
        .take(max_len)
        .map(|c| {
            // is_alphanumeric() works with all Unicode letters and digits
            if c.is_alphanumeric() {
                c
            } else {
                '_'
            }
        })
        .collect()
}

// ============================================================================ 
// ONNX Runtime Integration
// ============================================================================ 

use ort::{
    session::{builder::GraphOptimizationLevel, Session},
    value::Value,
};

#[cfg(feature = "xnnpack")]
use ort::execution_providers::{CPUExecutionProvider, XNNPACKExecutionProvider};

pub struct Style {
    pub ttl: Array3<f32>,
    pub dp: Array3<f32>,
}

pub struct TextToSpeech {
    cfgs: Config,
    text_processor: UnicodeProcessor,
    dp_ort: Session,
    text_enc_ort: Session,
    vector_est_ort: Session,
    vocoder_ort: Session,
    pub sample_rate: i32,
}

impl TextToSpeech {
    pub fn new(
        cfgs: Config,
        text_processor: UnicodeProcessor,
        dp_ort: Session,
        text_enc_ort: Session,
        vector_est_ort: Session,
        vocoder_ort: Session,
    ) -> Self {
        let sample_rate = cfgs.ae.sample_rate;
        TextToSpeech {
            cfgs,
            text_processor,
            dp_ort,
            text_enc_ort,
            vector_est_ort,
            vocoder_ort,
            sample_rate,
        }
    }

    fn _infer(
        &mut self,
        text_list: &[String],
        lang_list: &[String],
        style: &Style,
        total_step: usize,
        speed: f32,
    ) -> Result<(Vec<f32>, Vec<f32>)> {
        let bsz = text_list.len();

        // Process text
        let (text_ids, text_mask) = self.text_processor.call(text_list, lang_list)?;
        
        let text_ids_array = {
            let text_ids_shape = (bsz, text_ids[0].len());
            let mut flat = Vec::new();
            for row in &text_ids {
                flat.extend_from_slice(row);
            }
            Array::from_shape_vec(text_ids_shape, flat)?
        };

        let text_ids_value = Value::from_array(text_ids_array)?;
        let text_mask_value = Value::from_array(text_mask.clone())?;
        let style_dp_value = Value::from_array(style.dp.clone())?;

        // Predict duration
        let dp_outputs = self.dp_ort.run(ort::inputs!{
            "text_ids" => &text_ids_value,
            "style_dp" => &style_dp_value,
            "text_mask" => &text_mask_value
        })?;

        let duration_data = dp_outputs["duration"].try_extract_tensor::<f32>()?;
        let mut duration: Vec<f32> = duration_data.1.to_vec();
        
        // Apply speed factor to duration
        for dur in duration.iter_mut() {
            *dur /= speed;
        }

        // Encode text
        let style_ttl_value = Value::from_array(style.ttl.clone())?;
        let text_enc_outputs = self.text_enc_ort.run(ort::inputs!{
            "text_ids" => &text_ids_value,
            "style_ttl" => &style_ttl_value,
            "text_mask" => &text_mask_value
        })?;

        let text_emb_data = text_enc_outputs["text_emb"].try_extract_tensor::<f32>()?;
        let text_emb_shape = text_emb_data.0;
        let text_emb = Array3::from_shape_vec(
            (text_emb_shape[0] as usize, text_emb_shape[1] as usize, text_emb_shape[2] as usize),
            text_emb_data.1.to_vec()
        )?;

        // Sample noisy latent
        let (mut xt, latent_mask) = sample_noisy_latent(
            &duration,
            self.sample_rate,
            self.cfgs.ae.base_chunk_size,
            self.cfgs.ttl.chunk_compress_factor,
            self.cfgs.ttl.latent_dim,
        );

        // Prepare constant arrays
        let total_step_array = Array::from_elem(bsz, total_step as f32);

        // Denoising loop
        for step in 0..total_step {
            let current_step_array = Array::from_elem(bsz, step as f32);

            let xt_value = Value::from_array(xt.clone())?;
            let text_emb_value = Value::from_array(text_emb.clone())?;
            let latent_mask_value = Value::from_array(latent_mask.clone())?;
            let text_mask_value2 = Value::from_array(text_mask.clone())?;
            let current_step_value = Value::from_array(current_step_array)?;
            let total_step_value = Value::from_array(total_step_array.clone())?;

            let vector_est_outputs = self.vector_est_ort.run(ort::inputs!{
                "noisy_latent" => &xt_value,
                "text_emb" => &text_emb_value,
                "style_ttl" => &style_ttl_value,
                "latent_mask" => &latent_mask_value,
                "text_mask" => &text_mask_value2,
                "current_step" => &current_step_value,
                "total_step" => &total_step_value
            })?;

            let denoised_data = vector_est_outputs["denoised_latent"].try_extract_tensor::<f32>()?;
            let denoised_shape = denoised_data.0;
            xt = Array3::from_shape_vec(
                (denoised_shape[0] as usize, denoised_shape[1] as usize, denoised_shape[2] as usize),
                denoised_data.1.to_vec()
            )?;
        }

        // Generate waveform
        let final_latent_value = Value::from_array(xt)?;
        let vocoder_outputs = self.vocoder_ort.run(ort::inputs!{
            "latent" => &final_latent_value
        })?;

        let wav_data = vocoder_outputs["wav_tts"].try_extract_tensor::<f32>()?;
        let wav: Vec<f32> = wav_data.1.to_vec();

        Ok((wav, duration))
    }

    pub fn call<F>(
        &mut self,
        text: &str,
        lang: &str,
        style: &Style,
        total_step: usize,
        speed: f32,
        silence_duration: f32,
        mut callback: F,
    ) -> Result<(Vec<f32>, f32)> 
    where F: FnMut(usize, usize, Option<&[f32]>) -> bool {
        let normalizer = get_normalizer(lang);
        let max_len = normalizer.max_chunk_len();
        let chunks = chunk_text(text, lang, Some(max_len));
        let num_chunks = chunks.len();
        
        let mut wav_cat: Vec<f32> = Vec::new();
        let mut dur_cat: f32 = 0.0;

        for (i, chunk) in chunks.iter().enumerate() {
            // Notify start of chunk (audio is None)
            if !callback(i, num_chunks, None) {
                return Err(anyhow::anyhow!("Synthesis cancelled by user"));
            }
            
            let (wav, duration) = self._infer(&[chunk.clone()], &[lang.to_string()], style, total_step, speed)?;

            // Truncate audio based on predicted duration to remove trailing silence
            let dur = duration[0];
            let sample_count = (dur * self.sample_rate as f32) as usize;
            let wav_chunk = if sample_count < wav.len() {
                &wav[..sample_count]
            } else {
                &wav[..]
            };

            // Send audio chunk
            if !callback(i, num_chunks, Some(wav_chunk)) {
                 return Err(anyhow::anyhow!("Synthesis cancelled by user"));
            }

            if i == 0 {
                wav_cat.extend_from_slice(wav_chunk);
                dur_cat = dur;
            } else {
                let silence_len = (silence_duration * self.sample_rate as f32) as usize;
                let silence = vec![0.0f32; silence_len];
                
                wav_cat.extend_from_slice(&silence);
                wav_cat.extend_from_slice(wav_chunk);
                dur_cat += silence_duration + dur;
            }
        }
        callback(num_chunks, num_chunks, None);

        Ok((wav_cat, dur_cat))
    }

    #[allow(dead_code)]
    pub fn batch(
        &mut self,
        text_list: &[String],
        lang_list: &[String],
        style: &Style,
        total_step: usize,
        speed: f32,
    ) -> Result<(Vec<f32>, Vec<f32>)> {
        self._infer(text_list, lang_list, style, total_step, speed)
    }
}

// ============================================================================ 
// Component Loading Functions
// ============================================================================ 

/// Load voice style from JSON files
pub fn load_voice_style(voice_style_paths: &[String], verbose: bool) -> Result<Style> {
    let bsz = voice_style_paths.len();

    // Read first file to get dimensions
    let first_file = File::open(&voice_style_paths[0])
        .context("Failed to open voice style file")?;
    let first_reader = BufReader::new(first_file);
    let first_data: VoiceStyleData = serde_json::from_reader(first_reader)?;

    let ttl_dims = &first_data.style_ttl.dims;
    let dp_dims = &first_data.style_dp.dims;

    let ttl_dim1 = ttl_dims[1];
    let ttl_dim2 = ttl_dims[2];
    let dp_dim1 = dp_dims[1];
    let dp_dim2 = dp_dims[2];

    // Pre-allocate arrays with full batch size
    let ttl_size = bsz * ttl_dim1 * ttl_dim2;
    let dp_size = bsz * dp_dim1 * dp_dim2;
    let mut ttl_flat = vec![0.0f32; ttl_size];
    let mut dp_flat = vec![0.0f32; dp_size];

    // Fill in the data
    for (i, path) in voice_style_paths.iter().enumerate() {
        let file = File::open(path).context("Failed to open voice style file")?;
        let reader = BufReader::new(file);
        let data: VoiceStyleData = serde_json::from_reader(reader)?;

        // Flatten TTL data
        let ttl_offset = i * ttl_dim1 * ttl_dim2;
        let mut idx = 0;
        for batch in &data.style_ttl.data {
            for row in batch {
                for &val in row {
                    ttl_flat[ttl_offset + idx] = val;
                    idx += 1;
                }
            }
        }

        // Flatten DP data
        let dp_offset = i * dp_dim1 * dp_dim2;
        idx = 0;
        for batch in &data.style_dp.data {
            for row in batch {
                for &val in row {
                    dp_flat[dp_offset + idx] = val;
                    idx += 1;
                }
            }
        }
    }

    let ttl_style = Array3::from_shape_vec((bsz, ttl_dim1, ttl_dim2), ttl_flat)?;
    let dp_style = Array3::from_shape_vec((bsz, dp_dim1, dp_dim2), dp_flat)?;

    if verbose {
        println!("Loaded {} voice styles\n", bsz);
    }

    Ok(Style {
        ttl: ttl_style,
        dp: dp_style,
    })
}

/// Load and mix two voice styles
pub fn load_and_mix_voice_styles(path1: &str, path2: &str, alpha: f32) -> Result<Style> {
    let s1 = load_voice_style(&[path1.to_string()], false)?;
    let s2 = load_voice_style(&[path2.to_string()], false)?;

    if s1.ttl.dim() != s2.ttl.dim() || s1.dp.dim() != s2.dp.dim() {
        anyhow::bail!("Voice style dimensions mismatch");
    }

    let ttl = &s1.ttl * (1.0 - alpha) + &s2.ttl * alpha;
    let dp = &s1.dp * (1.0 - alpha) + &s2.dp * alpha;

    Ok(Style { ttl, dp })
}

/// Create an ONNX session with the specified execution providers
fn create_session(model_path: &str, use_xnnpack: bool, ort_threads: usize, _xnn_threads: usize) -> Result<Session> {
    #[allow(unused_mut)]
    let mut builder = Session::builder()?
        .with_optimization_level(GraphOptimizationLevel::Level3)?
        // OPTIMIZATION: Disable spinning to save battery and reduce heat on Android.
        // This ensures that when one thread pool is idle (e.g., ORT pool while XNNPACK is working),
        // it doesn't consume any CPU cycles.
        .with_config_entry("session.intra_op.allow_spinning", "0")?
        .with_config_entry("session.inter_op.allow_spinning", "0")?
        .with_intra_threads(ort_threads)?;

    if use_xnnpack {
        #[cfg(feature = "xnnpack")]
        {
            if let Some(xnn_threads_nz) = std::num::NonZeroUsize::new(_xnn_threads) {
                builder = builder.with_execution_providers([
                    XNNPACKExecutionProvider::default()
                        .with_intra_op_num_threads(xnn_threads_nz)
                        .build(),
                    CPUExecutionProvider::default().build(),
                ])?;
            } else {
                builder = builder.with_execution_providers([
                    XNNPACKExecutionProvider::default()
                        .with_intra_op_num_threads(std::num::NonZeroUsize::MIN)
                        .build(),
                    CPUExecutionProvider::default().build(),
                ])?;
            }
        }
    }

    builder.commit_from_file(model_path).context(format!("Failed to load model: {}", model_path))
}

/// Load TTS components
pub fn load_text_to_speech(onnx_dir: &str, use_gpu: bool, use_xnnpack: bool, ort_threads: usize, xnn_threads: usize) -> Result<TextToSpeech> {
    if use_gpu {
        anyhow::bail!("GPU mode is not supported yet");
    }
    
    if use_xnnpack {
        log::info!("Using XNNPACK ({}) with ORT ({}) threads", xnn_threads, ort_threads);
    } else {
        log::info!("Using CPU for inference with {} threads", ort_threads);
    }

    let cfgs = load_cfgs(onnx_dir)?;

    let dp_path = format!("{}/duration_predictor.onnx", onnx_dir);
    let text_enc_path = format!("{}/text_encoder.onnx", onnx_dir);
    let vector_est_path = format!("{}/vector_estimator.onnx", onnx_dir);
    let vocoder_path = format!("{}/vocoder.onnx", onnx_dir);

    let dp_ort = create_session(&dp_path, use_xnnpack, ort_threads, xnn_threads)?;
    let text_enc_ort = create_session(&text_enc_path, use_xnnpack, ort_threads, xnn_threads)?;
    let vector_est_ort = create_session(&vector_est_path, use_xnnpack, ort_threads, xnn_threads)?;
    let vocoder_ort = create_session(&vocoder_path, use_xnnpack, ort_threads, xnn_threads)?;

    let unicode_indexer_path = format!("{}/unicode_indexer.json", onnx_dir);
    let text_processor = UnicodeProcessor::new(&unicode_indexer_path)?;

    Ok(TextToSpeech::new(
        cfgs,
        text_processor,
        dp_ort,
        text_enc_ort,
        vector_est_ort,
        vocoder_ort,
    ))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::lang::remove_unmatched_quotes;

    #[test]
    fn test_preprocess_text_french_apostrophe() {
        // Test with right-single-quote (typographic apostrophe U+2019)
        let input_typographic = "l’oiseau s’envole";
        let result_typographic = preprocess_text(input_typographic, "fr").unwrap();
        assert_eq!(result_typographic, "<fr>l'oiseau s'envole.</fr>");

        // Test with straight apostrophe U+0027
        let input_straight = "l'oiseau s'envole";
        let result_straight = preprocess_text(input_straight, "fr").unwrap();
        assert_eq!(result_straight, "<fr>l'oiseau s'envole.</fr>");
    }

    #[test]
    fn test_preprocess_text_english_normalization() {
        // Test English-specific expansion and ending punctuation
        let input = "hello @ world";
        let result = preprocess_text(input, "en").unwrap();
        assert_eq!(result, "hello at world.");

        // Test if a trailing period is correctly preserved without duplication
        let input_period = "hello world.";
        let result_period = preprocess_text(input_period, "en").unwrap();
        assert_eq!(result_period, "hello world.");
    }

    #[test]
    fn test_preprocess_text_korean_no_period() {
        // Test Korean text normalization - it should not have trailing period
        let input = "안녕하세요";
        let result = preprocess_text(input, "ko").unwrap();
        assert!(!result.contains('.'));
        assert!(result.starts_with("<ko>"));
        assert!(result.ends_with("</ko>"));
    }

    #[test]
    fn test_remove_unmatched_quotes() {
        // Test unmatched double quote at start
        let input_double_start = "\"I wanted to go to the store,";
        assert_eq!(remove_unmatched_quotes(input_double_start), "I wanted to go to the store,");

        // Test unmatched double quote at end
        let input_double_end = "He talked to her first.\"";
        assert_eq!(remove_unmatched_quotes(input_double_end), "He talked to her first.");

        // Test unmatched double quote in text split with other matched quotes
        let input_mixed = "He talked to her first.\" \"Yes, he did.\"";
        assert_eq!(remove_unmatched_quotes(input_mixed), "He talked to her first. \"Yes, he did.\"");

        // Test fully balanced double quotes (should not modify)
        let input_balanced = "\"He talked to her first.\"";
        assert_eq!(remove_unmatched_quotes(input_balanced), "\"He talked to her first.\"");

        // Test unmatched single quote at start
        let input_single_start = "'I wanted to go to the store,";
        assert_eq!(remove_unmatched_quotes(input_single_start), "I wanted to go to the store,");

        // Test unmatched single quote at end
        let input_single_end = "He talked to her first.'";
        assert_eq!(remove_unmatched_quotes(input_single_end), "He talked to her first.");

        // Test that internal apostrophes are NOT removed/touched
        let input_apostrophes = "don't l'oiseau s'envole";
        assert_eq!(remove_unmatched_quotes(input_apostrophes), "don't l'oiseau s'envole");

        // Test combined unmatched single quotes with apostrophes
        let input_apostrophes_unmatched = "'don't l'oiseau s'envole";
        assert_eq!(remove_unmatched_quotes(input_apostrophes_unmatched), "don't l'oiseau s'envole");

        // Test user's specific chunk 1 and chunk 2 split example
        let chunk1 = "Fletcher was silent for a moment, then he said, \"The big guy who was riding with Gibson. Who is he?\" \"No Idea.\" \"He doesn't have ID?\" \"No.\" \"Any luggage? A backpack at least?\" \"Just this.\" Vidic took a pistol from his waistband and handed it to Fletcher. \"A Glock 17. The FBI's weapon of choice.";
        let chunk1_expected = "Fletcher was silent for a moment, then he said, \"The big guy who was riding with Gibson. Who is he?\" \"No Idea.\" \"He doesn't have ID?\" \"No.\" \"Any luggage? A backpack at least?\" \"Just this.\" Vidic took a pistol from his waistband and handed it to Fletcher. A Glock 17. The FBI's weapon of choice.";
        assert_eq!(remove_unmatched_quotes(chunk1), chunk1_expected);

        let chunk2 = "Make of that what you will.\"";
        let chunk2_expected = "Make of that what you will.";
        assert_eq!(remove_unmatched_quotes(chunk2), chunk2_expected);

        // Test quote classification heuristic fixes (comma as prefix, opening parenthesis as suffix/next boundary)
        let test_comma_prefix = "said, \"Hello\" \"World";
        // Under correct classification:
        // - "Hello" is balanced (quote 1 is opening, quote 2 is closing).
        // - "World" is unmatched (only opening quote), so it gets stripped.
        assert_eq!(remove_unmatched_quotes(test_comma_prefix), "said, \"Hello\" World");

        let test_paren_suffix = "\"Hello\"( \"World";
        // Under correct classification:
        // - "Hello" is balanced (quote 2 is followed by ( but is closing, quote 1 is opening).
        // - "World" is unmatched (only opening quote), so it gets stripped.
        assert_eq!(remove_unmatched_quotes(test_paren_suffix), "\"Hello\"( World");
    }

    #[test]
    fn test_preprocess_text_unmatched_quotes() {
        // Test double quote cleanup in preprocess_text
        let input = "He talked to her first.\"";
        let result = preprocess_text(input, "en").unwrap();
        assert_eq!(result, "He talked to her first.");

        // Test single quote cleanup in preprocess_text
        let input_single = "He talked to her first.'";
        let result_single = preprocess_text(input_single, "en").unwrap();
        assert_eq!(result_single, "He talked to her first.");
    }

    #[test]
    fn test_split_sentences_french() {
        let input = "Bonjour M. Dupont. Comment allez-vous ?";
        let sentences = split_sentences(input, "fr");
        assert_eq!(sentences.len(), 2);
        assert_eq!(sentences[0].trim(), "Bonjour M. Dupont.");
        assert_eq!(sentences[1].trim(), "Comment allez-vous ?");
        
        let input2 = "Le 1er. jour de l'année. C'est le 2e. test.";
        let sentences2 = split_sentences(input2, "fr");
        assert_eq!(sentences2.len(), 2);
        assert_eq!(sentences2[0].trim(), "Le 1er. jour de l'année.");
        assert_eq!(sentences2[1].trim(), "C'est le 2e. test.");
    }

    #[test]
    fn test_preprocess_text_french_punc_spacing() {
        // Test that spaces before punctuation are handled and sentences still split correctly
        let input = "Bonjour ! Comment allez-vous ?";
        // preprocess_text should normalize this.
        let result = preprocess_text(input, "fr").unwrap();
        // Since preprocess_text adds <fr> tags and common_preprocess/fr_preprocess remove space before !
        assert_eq!(result, "<fr>Bonjour! Comment allez-vous?</fr>");
        
        // Test that it doesn't fuse when there's a space after
        let sentences = split_sentences("Bonjour ! Comment allez-vous ?", "fr");
        assert_eq!(sentences.len(), 2);
    }

    #[test]
    fn test_preprocess_text_french_guillemets() {
        let input = "« Bonjour »";
        let result = preprocess_text(input, "fr").unwrap();
        assert_eq!(result, "<fr>\"Bonjour\"</fr>");
    }

    #[test]
    fn test_preprocess_text_v3_languages() {
        // Test Japanese / V3 tag wrapping
        let result_ja = preprocess_text("こんにちは", "ja").unwrap();
        assert_eq!(result_ja, "<ja>こんにちは.</ja>");

        // Test Japanese / V3 preserving existing Japanese full stop
        let result_ja_punct = preprocess_text("こんにちは。", "ja").unwrap();
        assert_eq!(result_ja_punct, "<ja>こんにちは。</ja>");

        // Test Arabic / V3 tag wrapping
        let result_ar = preprocess_text("مرحبا", "ar").unwrap();
        assert_eq!(result_ar, "<ar>مرحبا.</ar>");

        // Test Hindi / V3 tag wrapping and check that purna viram (U+0964) is treated as punctuation
        let result_hi = preprocess_text("पहला पड़ाव था- UAE।", "hi").unwrap();
        assert_eq!(result_hi, "<hi>पहला पड़ाव था- UAE।</hi>");

        // Test that invalid language code fails
        assert!(preprocess_text("hello", "invalid").is_err());
    }

    #[test]
    fn test_split_sentences_hindi() {
        let input = "पहला पड़ाव था- UAE। मोदी यहां करीब 3 घंटे रुके। राष्ट्रपति शेख मोहम्मद बिन जायद से मुलाकात की।";
        let sentences = split_sentences(input, "hi");
        assert_eq!(sentences.len(), 3);
        assert_eq!(sentences[0].trim(), "पहला पड़ाव था- UAE।");
        assert_eq!(sentences[1].trim(), "मोदी यहां करीब 3 घंटे रुके।");
        assert_eq!(sentences[2].trim(), "राष्ट्रपति शेख मोहम्मद बिन जायद से मुलाकात की।");
    }

    #[test]
    fn test_english_chapter_chunking() {
        let file_path = "../app/src/test/resources/part1_text.txt";
        let text = std::fs::read_to_string(file_path).expect("Failed to read part1_text.txt");
        let start = std::time::Instant::now();
        let chunks = chunk_text(&text, "en", None);
        let duration = start.elapsed();
        println!("Rust Chunked {} chars into {} chunks in {:?}", text.len(), chunks.len(), duration);
        assert!(!chunks.is_empty());
        for (i, chunk) in chunks.iter().enumerate().take(5) {
            println!("Rust Chunk {}: {}", i, chunk);
        }
    }
}

