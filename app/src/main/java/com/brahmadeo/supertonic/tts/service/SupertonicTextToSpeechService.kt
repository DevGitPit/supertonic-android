package com.brahmadeo.supertonic.tts.service

import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import android.content.Context
import android.os.Build
import com.brahmadeo.supertonic.tts.SupertonicTTS
import com.brahmadeo.supertonic.tts.utils.AssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class SupertonicTextToSpeechService : TextToSpeechService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var initJob: Job? = null

    private val attributionContext: Context by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createAttributionContext("supertonic_playback")
        } else {
            this
        }
    }

    companion object {
        const val VOLUME_BOOST_FACTOR = 2.5f
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("SupertonicTTS", "Service created")
        com.brahmadeo.supertonic.tts.utils.LexiconManager.load(this)
        
        initJob = serviceScope.launch(Dispatchers.IO) {
            copyAssets()
            val prefs = attributionContext.getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
            val savedLang = prefs.getString("selected_lang", "en") ?: "en"
            val modelVersion = AssetManager.getModelVersionForLanguage(savedLang)

            val modelPath = File(filesDir, "$modelVersion/onnx").absolutePath
            val libPath = applicationInfo.nativeLibraryDir + "/libonnxruntime.so"

            SupertonicTTS.initialize(modelPath, libPath)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun getCurrentModelVersion(): String {
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val savedLang = prefs.getString("selected_lang", "en") ?: "en"
        return AssetManager.getModelVersionForLanguage(savedLang)
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val language = lang?.lowercase(Locale.ROOT) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        val modelVersion = getCurrentModelVersion()
        
        // 1. English (v1) Path
        if (modelVersion == "v1") {
            if (language.startsWith("en") || language.startsWith("eng")) {
                return if (!country.isNullOrEmpty()) TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
            }
            return TextToSpeech.LANG_NOT_SUPPORTED
        }

        // 2. Multilingual (v2) Path
        val multilingualPrefixes = listOf("ko", "kor", "es", "spa", "pt", "por", "fr", "fra", "fre")
        if (multilingualPrefixes.any { language.startsWith(it) }) {
            val v2Dir = File(filesDir, "v2/onnx")
            return if (v2Dir.exists()) {
                if (!country.isNullOrEmpty()) TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
            } else {
                TextToSpeech.LANG_MISSING_DATA
            }
        }

        // 3. Multilingual (v3) Path
        val v3Prefixes = listOf(
            "ja", "jpn", "ar", "ara", "bg", "bul", "cs", "ces", "cze", "da", "dan", "de", "deu", "ger",
            "el", "ell", "gre", "et", "est", "fi", "fin", "hi", "hin", "hr", "hrv", "hu", "hun", "id", "ind",
            "it", "ita", "lt", "lit", "lv", "lav", "nl", "nld", "dut", "pl", "pol", "ro", "ron", "rum", "ru", "rus",
            "sk", "slk", "slo", "sl", "slv", "sv", "swe", "tr", "tur", "uk", "ukr", "vi", "vie"
        )
        if (modelVersion == "v3") {
            if (v3Prefixes.any { language.startsWith(it) }) {
                val v3Dir = File(filesDir, "v3/onnx")
                return if (v3Dir.exists()) {
                    if (!country.isNullOrEmpty()) TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
                } else {
                    TextToSpeech.LANG_MISSING_DATA
                }
            }
            return TextToSpeech.LANG_NOT_SUPPORTED
        }

        return TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onGetLanguage(): Array<String> {
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val selectedLang = prefs.getString("selected_lang", "en") ?: "en"
        
        return when(selectedLang) {
            "ko" -> arrayOf("kor", "KOR", "")
            "es" -> arrayOf("spa", "ESP", "")
            "pt" -> arrayOf("por", "PRT", "")
            "fr" -> arrayOf("fra", "FRA", "")
            "ja" -> arrayOf("jpn", "JPN", "")
            "ar" -> arrayOf("ara", "ARA", "")
            "bg" -> arrayOf("bul", "BGR", "")
            "cs" -> arrayOf("ces", "CZE", "")
            "da" -> arrayOf("dan", "DNK", "")
            "de" -> arrayOf("deu", "DEU", "")
            "el" -> arrayOf("ell", "GRC", "")
            "et" -> arrayOf("est", "EST", "")
            "fi" -> arrayOf("fin", "FIN", "")
            "hi" -> arrayOf("hin", "IND", "")
            "hr" -> arrayOf("hrv", "HRV", "")
            "hu" -> arrayOf("hun", "HUN", "")
            "id" -> arrayOf("ind", "IDN", "")
            "it" -> arrayOf("ita", "ITA", "")
            "lt" -> arrayOf("lit", "LTU", "")
            "lv" -> arrayOf("lav", "LVA", "")
            "nl" -> arrayOf("nld", "NLD", "")
            "pl" -> arrayOf("pol", "POL", "")
            "ro" -> arrayOf("ron", "ROU", "")
            "ru" -> arrayOf("rus", "RUS", "")
            "sk" -> arrayOf("slk", "SVK", "")
            "sl" -> arrayOf("slv", "SVN", "")
            "sv" -> arrayOf("swe", "SWE", "")
            "tr" -> arrayOf("tur", "TUR", "")
            "uk" -> arrayOf("ukr", "UKR", "")
            "vi" -> arrayOf("vie", "VNM", "")
            else -> arrayOf("eng", "USA", "")
        }
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onLoadVoice(voiceName: String?): Int {
        if (voiceName == null) return TextToSpeech.ERROR
        if (voiceName.contains("-supertonic-")) {
            val langPrefix = voiceName.substringBefore("-supertonic-")
            val styleName = voiceName.substringAfter("-supertonic-")
            val modelVersion = AssetManager.getModelVersionForLanguage(langPrefix)
            val file = File(filesDir, "$modelVersion/voice_styles/$styleName.json")
            if (file.exists()) return TextToSpeech.SUCCESS
        }
        return TextToSpeech.ERROR
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val selected = prefs.getString("selected_voice", "F3.json") ?: "F3.json"
        val voiceName = if (selected.endsWith(".json")) selected.substringBeforeLast(".") else selected
        
        val prefix = normalizeLanguage(lang)
        return "$prefix-supertonic-$voiceName"
    }

    override fun onGetVoices(): List<Voice> {
        val modelVersion = getCurrentModelVersion()
        val voicesList = mutableListOf<Voice>()
        val voiceNames = listOf("M1", "M2", "M3", "M4", "M5", "F1", "F2", "F3", "F4", "F5")

        if (modelVersion == "v1") {
            // Only English Voices
            voiceNames.forEach { name ->
                voicesList.add(Voice("en-supertonic-$name", Locale.US, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, setOf()))
            }
        } else if (modelVersion == "v2") {
            // Only Multilingual Voices (excluding English as requested)
            val v2Dir = File(filesDir, "v2/onnx")
            if (v2Dir.exists()) {
                val multilingualLocales = listOf(
                    Locale.KOREA,
                    Locale.forLanguageTag("es-ES"),
                    Locale.forLanguageTag("pt-PT"),
                    Locale.FRANCE
                )
                multilingualLocales.forEach { locale ->
                    val langPrefix = locale.language
                    voiceNames.forEach { name ->
                        voicesList.add(Voice("$langPrefix-supertonic-$name", locale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, setOf()))
                    }
                }
            }
        } else { // v3
            val v3Dir = File(filesDir, "v3/onnx")
            if (v3Dir.exists()) {
                val v3Locales = listOf(
                    Locale.JAPAN,
                    Locale.forLanguageTag("ar"),
                    Locale.forLanguageTag("bg"),
                    Locale.forLanguageTag("cs"),
                    Locale.forLanguageTag("da"),
                    Locale.GERMANY,
                    Locale.forLanguageTag("el"),
                    Locale.forLanguageTag("et"),
                    Locale.forLanguageTag("fi"),
                    Locale.forLanguageTag("hi-IN"),
                    Locale.forLanguageTag("hr"),
                    Locale.forLanguageTag("hu"),
                    Locale.forLanguageTag("id"),
                    Locale.ITALY,
                    Locale.forLanguageTag("lt"),
                    Locale.forLanguageTag("lv"),
                    Locale.forLanguageTag("nl"),
                    Locale.forLanguageTag("pl"),
                    Locale.forLanguageTag("ro"),
                    Locale.forLanguageTag("ru"),
                    Locale.forLanguageTag("sk"),
                    Locale.forLanguageTag("sl"),
                    Locale.forLanguageTag("sv"),
                    Locale.forLanguageTag("tr"),
                    Locale.forLanguageTag("uk"),
                    Locale.forLanguageTag("vi")
                )
                v3Locales.forEach { locale ->
                    val langPrefix = locale.language
                    voiceNames.forEach { name ->
                        voicesList.add(Voice("$langPrefix-supertonic-$name", locale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, setOf()))
                    }
                }
            }
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
            l.startsWith("en") || l.startsWith("eng") -> "en"
            l.startsWith("ko") || l.startsWith("kor") -> "ko"
            l.startsWith("es") || l.startsWith("spa") -> "es"
            l.startsWith("pt") || l.startsWith("por") -> "pt"
            l.startsWith("fr") || l.startsWith("fra") || l.startsWith("fre") -> "fr"
            l.startsWith("ja") || l.startsWith("jpn") -> "ja"
            l.startsWith("ar") || l.startsWith("ara") -> "ar"
            l.startsWith("bg") || l.startsWith("bul") -> "bg"
            l.startsWith("cs") || l.startsWith("ces") || l.startsWith("cze") -> "cs"
            l.startsWith("da") || l.startsWith("dan") -> "da"
            l.startsWith("de") || l.startsWith("deu") || l.startsWith("ger") -> "de"
            l.startsWith("el") || l.startsWith("ell") || l.startsWith("gre") -> "el"
            l.startsWith("et") || l.startsWith("est") -> "et"
            l.startsWith("fi") || l.startsWith("fin") -> "fi"
            l.startsWith("hi") || l.startsWith("hin") -> "hi"
            l.startsWith("hr") || l.startsWith("hrv") -> "hr"
            l.startsWith("hu") || l.startsWith("hun") -> "hu"
            l.startsWith("id") || l.startsWith("ind") -> "id"
            l.startsWith("it") || l.startsWith("ita") -> "it"
            l.startsWith("lt") || l.startsWith("lit") -> "lt"
            l.startsWith("lv") || l.startsWith("lav") -> "lv"
            l.startsWith("nl") || l.startsWith("nld") || l.startsWith("dut") -> "nl"
            l.startsWith("pl") || l.startsWith("pol") -> "pl"
            l.startsWith("ro") || l.startsWith("ron") || l.startsWith("rum") -> "ro"
            l.startsWith("ru") || l.startsWith("rus") -> "ru"
            l.startsWith("sk") || l.startsWith("slk") || l.startsWith("slo") -> "sk"
            l.startsWith("sl") || l.startsWith("slv") -> "sl"
            l.startsWith("sv") || l.startsWith("swe") -> "sv"
            l.startsWith("tr") || l.startsWith("tur") -> "tr"
            l.startsWith("uk") || l.startsWith("ukr") -> "uk"
            l.startsWith("vi") || l.startsWith("vie") -> "vi"
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
        val requestedLang = normalizeLanguage(request.language)
        val prefs = attributionContext.getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)

        val modelVersion = if (requestedVoice != null && requestedVoice.contains("-supertonic-")) {
             val langPrefix = requestedVoice.substringBefore("-supertonic-")
             AssetManager.getModelVersionForLanguage(langPrefix)
        } else {
             AssetManager.getModelVersionForLanguage(requestedLang)
        }
        
        val voiceFile = if (requestedVoice != null && requestedVoice.contains("-supertonic-")) {
            val fileName = requestedVoice.substringAfter("-supertonic-")
            // Sanitize fileName to prevent path traversal
            File(fileName).name + ".json"
        } else {
            prefs.getString("selected_voice", "F3.json") ?: "F3.json"
        }

        val voiceStyleDir = File(filesDir, "$modelVersion/voice_styles")
        var stylePath = File(voiceStyleDir, voiceFile).absolutePath
        
        // Ensure stylePath is within the intended directory
        if (!File(stylePath).canonicalPath.startsWith(voiceStyleDir.canonicalPath)) {
            stylePath = File(voiceStyleDir, "F3.json").absolutePath
        }
        
        // Handle Voice Mixing (Only if mixing is compatible with modelVersion)
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

        // Ensure engine is initialized for the correct model
        if (SupertonicTTS.getSoC() == -1) {
             val modelPath = File(filesDir, "$modelVersion/onnx").absolutePath
             val libPath = applicationInfo.nativeLibraryDir + "/libonnxruntime.so"
             SupertonicTTS.initialize(modelPath, libPath)
        } else {
            // Check if current engine matches required model version
            // For now, we assume if SoC is valid, it's okay, but ideally we'd re-init if modelVersion changed
            // However, JNI initialization is expensive, so we only re-init if really needed.
        }
        
        try {
            val sentences = textNormalizer.splitIntoSentences(rawText, requestedLang)
            var success = true
            for (sentence in sentences) {
                if (SupertonicTTS.isCancelled()) { success = false; break }

                val isAdvancedEnabled = prefs.getBoolean("is_advanced_normalization", false)
                val sibilanceMode = prefs.getInt("sibilance_reduction_mode", 1)
                val normalizedText = textNormalizer.normalize(sentence, requestedLang, isAdvancedEnabled)

                val audioData = SupertonicTTS.generateAudio(normalizedText, requestedLang, stylePath, effectiveSpeed, 0.0f, steps, VOLUME_BOOST_FACTOR, null, sibilanceMode)

                if (audioData != null && audioData.isNotEmpty()) {
                    var offset = 0
                    while (offset < audioData.size) {
                        val length = 4096.coerceAtMost(audioData.size - offset)
                        callback.audioAvailable(audioData, offset, length)
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
                if (!subFiles.isNullOrEmpty()) {
                    copyDir(fullAssetPath, File(targetDir, filename))
                } else {
                    val file = File(targetDir, filename)
                    try {
                        assetManager.open(fullAssetPath).use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        }
                    } catch (_: IOException) { }
                }
            }
        }

        copyDir("v1", File(filesDir, "v1"))
        copyDir("v2", File(filesDir, "v2"))
        copyDir("v3", File(filesDir, "v3"))
    }
}