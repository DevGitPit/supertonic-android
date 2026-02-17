package com.brahmadeo.supertonic.tts.service

import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.brahmadeo.supertonic.tts.SupertonicTTS
import com.brahmadeo.supertonic.tts.utils.LanguageDetector
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class SupertonicTextToSpeechService : TextToSpeechService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var initJob: Job? = null

    companion object {
        const val VOLUME_BOOST_FACTOR = 2.5f
    }

    private fun applyVolumeBoost(pcmData: ByteArray, gain: Float): ByteArray {
        if (gain == 1.0f) return pcmData
        val size = pcmData.size
        val boosted = ByteArray(size)
        val inBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val outBuffer = ByteBuffer.wrap(boosted).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val count = size / 2
        for (i in 0 until count) {
            val sample = inBuffer.get(i)
            var scaled = (sample * gain).toInt()
            if (scaled > 32767) scaled = 32767
            if (scaled < -32768) scaled = -32768
            outBuffer.put(i, scaled.toShort())
        }
        return boosted
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("SupertonicTTS", "Service created")
        com.brahmadeo.supertonic.tts.utils.LexiconManager.load(this)
        
        initJob = serviceScope.launch(Dispatchers.IO) {
            copyAssets()
            val prefs = getSharedPreferences("SupertonicPrefs", android.content.Context.MODE_PRIVATE)
            val savedLang = prefs.getString("selected_lang", "en") ?: "en"
            val modelVersion = if (savedLang == "en") "v1" else "v2"

            val modelPath = File(filesDir, "$modelVersion/onnx").absolutePath
            val libPath = applicationInfo.nativeLibraryDir + "/libonnxruntime.so"

            SupertonicTTS.initialize(modelPath, libPath)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val language = lang?.lowercase(Locale.ROOT) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        val prefs = getSharedPreferences("SupertonicPrefs", android.content.Context.MODE_PRIVATE)
        val selectedLang = prefs.getString("selected_lang", "en") ?: "en"

        // Map selectedLang to ISO 3-letter codes if necessary or just prefixes
        val allowedPrefixes = when(selectedLang) {
            "ko" -> listOf("ko", "kor")
            "es" -> listOf("es", "spa")
            "pt" -> listOf("pt", "por")
            "fr" -> listOf("fr", "fra", "fre")
            else -> listOf("en", "eng")
        }

        val isSupported = allowedPrefixes.any { language.startsWith(it) }

        if (!isSupported) return TextToSpeech.LANG_NOT_SUPPORTED

        return if (country != null && country.isNotEmpty()) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            TextToSpeech.LANG_AVAILABLE
        }
    }

    override fun onGetLanguage(): Array<String> {
        val prefs = getSharedPreferences("SupertonicPrefs", android.content.Context.MODE_PRIVATE)
        val selectedLang = prefs.getString("selected_lang", "en") ?: "en"
        
        return when(selectedLang) {
            "ko" -> arrayOf("kor", "KOR", "")
            "es" -> arrayOf("spa", "ESP", "")
            "pt" -> arrayOf("por", "PRT", "")
            "fr" -> arrayOf("fra", "FRA", "")
            else -> arrayOf("eng", "USA", "")
        }
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onLoadVoice(voiceName: String?): Int {
        if (voiceName == null) return TextToSpeech.ERROR
        // Broaden prefix check to handle all Supertonic voices
        if (voiceName.contains("-supertonic-")) {
            val styleName = voiceName.substringAfter("-supertonic-")
            val file = File(filesDir, "voice_styles/$styleName.json")
            if (file.exists()) return TextToSpeech.SUCCESS
        }
        return TextToSpeech.ERROR
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        val prefs = getSharedPreferences("SupertonicPrefs", android.content.Context.MODE_PRIVATE)
        val selected = prefs.getString("selected_voice", "M1.json") ?: "M1.json"
        val voiceName = if (selected.endsWith(".json")) selected.substringBeforeLast(".") else selected
        
        val language = lang?.lowercase(Locale.ROOT) ?: "en"
        val prefix = when {
            language.startsWith("ko") || language.startsWith("kor") -> "ko"
            language.startsWith("es") || language.startsWith("spa") -> "es"
            language.startsWith("pt") || language.startsWith("por") -> "pt"
            language.startsWith("fr") || language.startsWith("fra") || language.startsWith("fre") -> "fr"
            else -> "en"
        }
        return "$prefix-supertonic-$voiceName"
    }

    override fun onGetVoices(): List<Voice> {
        val prefs = getSharedPreferences("SupertonicPrefs", android.content.Context.MODE_PRIVATE)
        val selectedLang = prefs.getString("selected_lang", "en") ?: "en"

        val voicesList = mutableListOf<Voice>()

        // Only broadcast voices for the currently selected language
        val locale = when(selectedLang) {
            "ko" -> Locale.KOREA
            "es" -> Locale.forLanguageTag("es-ES")
            "pt" -> Locale.forLanguageTag("pt-PT")
            "fr" -> Locale.FRANCE
            else -> Locale.US
        }

        val voiceNames = listOf("M1", "M2", "M3", "M4", "M5", "F1", "F2", "F3", "F4", "F5")
        val langPrefix = locale.language

        voiceNames.forEach { name ->
            voicesList.add(Voice("$langPrefix-supertonic-$name", locale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, setOf()))
        }

        return voicesList
    }

    override fun onStop() {
        SupertonicTTS.setCancelled(true)
    }

    private fun normalizeLanguage(lang: String?): String {
        if (lang == null) return "en"
        val l = lang.lowercase(Locale.ROOT)
        return when {
            l.startsWith("en") -> "en"
            l.startsWith("ko") -> "ko"
            l.startsWith("kor") -> "ko"
            l.startsWith("es") -> "es"
            l.startsWith("spa") -> "es"
            l.startsWith("pt") -> "pt"
            l.startsWith("por") -> "pt"
            l.startsWith("fr") -> "fr"
            l.startsWith("fra") -> "fr"
            l.startsWith("fre") -> "fr"
            else -> "en"
        }
    }

    private val textNormalizer = com.brahmadeo.supertonic.tts.utils.TextNormalizer()

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return
        SupertonicTTS.setCancelled(false)
        runBlocking {
            withTimeoutOrNull(5000) {
                initJob?.join()
            }
        }
        val rawText = request.charSequenceText?.toString() ?: return
        val effectiveSpeed = (request.speechRate / 100.0f).coerceIn(0.5f, 2.5f)
        callback.start(SupertonicTTS.getAudioSampleRate(), android.media.AudioFormat.ENCODING_PCM_16BIT, 1)
        
        val requestedVoice = request.voiceName
        val prefs = getSharedPreferences("SupertonicPrefs", android.content.Context.MODE_PRIVATE)
        
        var voiceFile = if (requestedVoice != null && requestedVoice.contains("-supertonic-")) {
            requestedVoice.substringAfter("-supertonic-") + ".json"
        } else {
            prefs.getString("selected_voice", "M1.json") ?: "M1.json"
        }

        val savedLang = prefs.getString("selected_lang", "en") ?: "en"
        val modelVersion = if (savedLang == "en") "v1" else "v2"

        var stylePath = File(filesDir, "$modelVersion/voice_styles/$voiceFile").absolutePath
        
        // Handle Voice Mixing
        val isMixing = prefs.getBoolean("is_mixing_enabled", false)
        if (isMixing) {
            val voice2 = prefs.getString("selected_voice_2", "M2.json") ?: "M2.json"
            val stylePath2 = File(filesDir, "$modelVersion/voice_styles/$voice2").absolutePath
            val alpha = prefs.getFloat("mix_alpha", 0.5f)
            
            if (File(stylePath).exists() && File(stylePath2).exists()) {
                stylePath = "$stylePath;$stylePath2;$alpha"
            }
        }

        val steps = prefs.getInt("diffusion_steps", 5)

        if (SupertonicTTS.getSoC() == -1) {
             val modelPath = File(filesDir, "$modelVersion/onnx").absolutePath
             val libPath = applicationInfo.nativeLibraryDir + "/libonnxruntime.so"
             SupertonicTTS.initialize(modelPath, libPath)
        }
        
        try {
            val requestLang = normalizeLanguage(request.language)
            val sentences = textNormalizer.splitIntoSentences(rawText, requestLang)
            var success = true
            for (sentence in sentences) {
                if (SupertonicTTS.isCancelled()) { success = false; break }

                // Granular per-sentence detection
                // val sentenceLang = LanguageDetector.detect(sentence, requestLang)
                val sentenceLang = requestLang
                val normalizedText = textNormalizer.normalize(sentence, sentenceLang)

                val audioData = SupertonicTTS.generateAudio(normalizedText, sentenceLang, stylePath, effectiveSpeed, 0.0f, steps, null)
                
                if (audioData != null && audioData.isNotEmpty()) {
                    val boostedData = applyVolumeBoost(audioData, VOLUME_BOOST_FACTOR)
                    var offset = 0
                    while (offset < boostedData.size) {
                        val length = Math.min(4096, boostedData.size - offset)
                        callback.audioAvailable(boostedData, offset, length)
                        offset += length
                    }
                }
            }
            if (success) callback.done() else callback.error()
        } finally {
            // Isolation handled in SupertonicTTS
        }
    }

    private fun copyAssets() {
        val filesDir = filesDir
        val assetManager = assets

        fun copyDir(assetPath: String, targetDir: File) {
            if (!targetDir.exists()) targetDir.mkdirs()
            val files = assetManager.list(assetPath) ?: return
            for (filename in files) {
                val fullAssetPath = "$assetPath/$filename"
                val subFiles = assetManager.list(fullAssetPath)
                if (subFiles != null && subFiles.isNotEmpty()) {
                    copyDir(fullAssetPath, File(targetDir, filename))
                } else {
                    val file = File(targetDir, filename)
                    try {
                        assetManager.open(fullAssetPath).use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        }
                    } catch (e: IOException) { }
                }
            }
        }

        copyDir("v1", File(filesDir, "v1"))
        copyDir("v2", File(filesDir, "v2"))
    }
}