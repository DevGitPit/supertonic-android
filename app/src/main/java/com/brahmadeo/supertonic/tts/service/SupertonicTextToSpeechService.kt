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

        val V2_LANG_CODES = setOf("ko", "es", "pt", "fr")

        // All languages supported by the v3 model
        val V3_LANG_LOCALES: Map<String, Locale> = mapOf(
            "ar" to Locale.forLanguageTag("ar-SA"),
            "bg" to Locale.forLanguageTag("bg-BG"),
            "hr" to Locale.forLanguageTag("hr-HR"),
            "cs" to Locale.forLanguageTag("cs-CZ"),
            "da" to Locale.forLanguageTag("da-DK"),
            "nl" to Locale.forLanguageTag("nl-NL"),
            "en" to Locale.US,
            "et" to Locale.forLanguageTag("et-EE"),
            "fi" to Locale.forLanguageTag("fi-FI"),
            "fr" to Locale.forLanguageTag("fr-FR"),
            "de" to Locale.forLanguageTag("de-DE"),
            "el" to Locale.forLanguageTag("el-GR"),
            "hi" to Locale.forLanguageTag("hi-IN"),
            "hu" to Locale.forLanguageTag("hu-HU"),
            "id" to Locale.forLanguageTag("id-ID"),
            "it" to Locale.forLanguageTag("it-IT"),
            "ja" to Locale.JAPAN,
            "ko" to Locale.KOREA,
            "lv" to Locale.forLanguageTag("lv-LV"),
            "lt" to Locale.forLanguageTag("lt-LT"),
            "pl" to Locale.forLanguageTag("pl-PL"),
            "pt" to Locale.forLanguageTag("pt-PT"),
            "ro" to Locale.forLanguageTag("ro-RO"),
            "ru" to Locale.forLanguageTag("ru-RU"),
            "sk" to Locale.forLanguageTag("sk-SK"),
            "sl" to Locale.forLanguageTag("sl-SI"),
            "es" to Locale.forLanguageTag("es-ES"),
            "sv" to Locale.forLanguageTag("sv-SE"),
            "tr" to Locale.forLanguageTag("tr-TR"),
            "uk" to Locale.forLanguageTag("uk-UA"),
            "vi" to Locale.forLanguageTag("vi-VN"),
        )

        // ISO 639-2 (3-letter) -> [iso3, country] for onGetLanguage()
        private val LANG_ISO3 = mapOf(
            "en" to Pair("eng", "USA"),
            "ar" to Pair("ara", "SAU"),
            "bg" to Pair("bul", "BGR"),
            "hr" to Pair("hrv", "HRV"),
            "cs" to Pair("ces", "CZE"),
            "da" to Pair("dan", "DNK"),
            "nl" to Pair("nld", "NLD"),
            "et" to Pair("est", "EST"),
            "fi" to Pair("fin", "FIN"),
            "fr" to Pair("fra", "FRA"),
            "de" to Pair("deu", "DEU"),
            "el" to Pair("ell", "GRC"),
            "hi" to Pair("hin", "IND"),
            "hu" to Pair("hun", "HUN"),
            "id" to Pair("ind", "IDN"),
            "it" to Pair("ita", "ITA"),
            "ja" to Pair("jpn", "JPN"),
            "ko" to Pair("kor", "KOR"),
            "lv" to Pair("lav", "LVA"),
            "lt" to Pair("lit", "LTU"),
            "pl" to Pair("pol", "POL"),
            "pt" to Pair("por", "PRT"),
            "ro" to Pair("ron", "ROU"),
            "ru" to Pair("rus", "RUS"),
            "sk" to Pair("slk", "SVK"),
            "sl" to Pair("slv", "SVN"),
            "es" to Pair("spa", "ESP"),
            "sv" to Pair("swe", "SWE"),
            "tr" to Pair("tur", "TUR"),
            "uk" to Pair("ukr", "UKR"),
            "vi" to Pair("vie", "VNM"),
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("SupertonicTTS", "Service created")
        com.brahmadeo.supertonic.tts.utils.LexiconManager.load(this)
        
        initJob = serviceScope.launch(Dispatchers.IO) {
            copyAssets()
            val prefs = attributionContext.getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
            val savedLang = prefs.getString("selected_lang", "en") ?: "en"
            val modelVersion = when {
                savedLang == "en" -> "v1"
                com.brahmadeo.supertonic.tts.utils.AssetManager.isV3Ready(this@SupertonicTextToSpeechService) -> "v3"
                else -> "v2"
            }

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
        return when {
            savedLang == "en" -> "v1"
            com.brahmadeo.supertonic.tts.utils.AssetManager.isV3Ready(this) -> "v3"
            else -> "v2"
        }
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val language = lang?.lowercase(Locale.ROOT) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        val modelVersion = getCurrentModelVersion()
        val normalizedLang = normalizeLanguage(language)

        fun available(dir: File): Int = when {
            !dir.exists() -> TextToSpeech.LANG_MISSING_DATA
            !country.isNullOrEmpty() -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            else -> TextToSpeech.LANG_AVAILABLE
        }

        return when (modelVersion) {
            "v1" -> if (normalizedLang == "en") available(File(filesDir, "v1/onnx")) else TextToSpeech.LANG_NOT_SUPPORTED
            "v2" -> if (normalizedLang in V2_LANG_CODES) available(File(filesDir, "v2/onnx")) else TextToSpeech.LANG_NOT_SUPPORTED
            "v3" -> if (normalizedLang in V3_LANG_LOCALES) available(File(filesDir, "v3/onnx")) else TextToSpeech.LANG_NOT_SUPPORTED
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> {
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val selectedLang = prefs.getString("selected_lang", "en") ?: "en"
        val (iso3, country) = LANG_ISO3[selectedLang] ?: Pair("eng", "USA")
        return arrayOf(iso3, country, "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onLoadVoice(voiceName: String?): Int {
        if (voiceName == null) return TextToSpeech.ERROR
        if (voiceName.contains("-supertonic-")) {
            val langPrefix = voiceName.substringBefore("-supertonic-")
            val styleName = voiceName.substringAfter("-supertonic-")
            val modelVersion = when {
                langPrefix.startsWith("en") -> "v1"
                com.brahmadeo.supertonic.tts.utils.AssetManager.isV3Ready(this) -> "v3"
                else -> "v2"
            }
            val file = File(filesDir, "$modelVersion/voice_styles/$styleName.json")
            if (file.exists()) return TextToSpeech.SUCCESS
        }
        return TextToSpeech.ERROR
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val selected = prefs.getString("selected_voice", "F3.json") ?: "F3.json"
        val voiceName = if (selected.endsWith(".json")) selected.substringBeforeLast(".") else selected
        
        val language = lang?.lowercase(Locale.ROOT) ?: "en"
        val prefix = normalizeLanguage(language)
        return "$prefix-supertonic-$voiceName"
    }

    override fun onGetVoices(): List<Voice> {
        val modelVersion = getCurrentModelVersion()
        val voicesList = mutableListOf<Voice>()
        val voiceNames = listOf("M1", "M2", "M3", "M4", "M5", "F1", "F2", "F3", "F4", "F5")

        if (modelVersion == "v3") {
            val v3Dir = File(filesDir, "v3/onnx")
            if (v3Dir.exists()) {
                V3_LANG_LOCALES.forEach { (langCode, locale) ->
                    voiceNames.forEach { name ->
                        voicesList.add(Voice("$langCode-supertonic-$name", locale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, setOf()))
                    }
                }
            }
        } else if (modelVersion == "v1") {
            // Only English Voices
            voiceNames.forEach { name ->
                voicesList.add(Voice("en-supertonic-$name", Locale.US, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, setOf()))
            }
        } else {
            // Only Multilingual Voices
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
            l.startsWith("ar") || l.startsWith("ara") -> "ar"
            l.startsWith("bg") || l.startsWith("bul") -> "bg"
            l.startsWith("hr") || l.startsWith("hrv") -> "hr"
            l.startsWith("cs") || l.startsWith("ces") || l.startsWith("cze") -> "cs"
            l.startsWith("da") || l.startsWith("dan") -> "da"
            l.startsWith("nl") || l.startsWith("nld") || l.startsWith("dut") -> "nl"
            l.startsWith("et") || l.startsWith("est") -> "et"
            l.startsWith("fi") || l.startsWith("fin") -> "fi"
            l.startsWith("fr") || l.startsWith("fra") || l.startsWith("fre") -> "fr"
            l.startsWith("de") || l.startsWith("deu") || l.startsWith("ger") -> "de"
            l.startsWith("el") || l.startsWith("ell") || l.startsWith("gre") -> "el"
            l.startsWith("hi") || l.startsWith("hin") -> "hi"
            l.startsWith("hu") || l.startsWith("hun") -> "hu"
            l.startsWith("id") || l.startsWith("ind") -> "id"
            l.startsWith("it") || l.startsWith("ita") -> "it"
            l.startsWith("ja") || l.startsWith("jpn") -> "ja"
            l.startsWith("ko") || l.startsWith("kor") -> "ko"
            l.startsWith("lv") || l.startsWith("lav") -> "lv"
            l.startsWith("lt") || l.startsWith("lit") -> "lt"
            l.startsWith("pl") || l.startsWith("pol") -> "pl"
            l.startsWith("pt") || l.startsWith("por") -> "pt"
            l.startsWith("ro") || l.startsWith("ron") || l.startsWith("rum") -> "ro"
            l.startsWith("ru") || l.startsWith("rus") -> "ru"
            // Slovak (slk/slo) must precede Slovenian (sl/slv) to avoid prefix collision
            l.startsWith("sk") || l.startsWith("slk") || l.startsWith("slo") -> "sk"
            l.startsWith("sl") || l.startsWith("slv") -> "sl"
            l.startsWith("es") || l.startsWith("spa") -> "es"
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

        val isV3Ready = com.brahmadeo.supertonic.tts.utils.AssetManager.isV3Ready(this)
        val modelVersion = when {
            requestedVoice != null && requestedVoice.contains("-supertonic-") &&
                requestedVoice.substringBefore("-supertonic-").startsWith("en") -> "v1"
            requestedLang == "en" -> "v1"
            isV3Ready -> "v3"
            else -> "v2"
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
                val normalizedText = textNormalizer.normalize(sentence, requestedLang, isAdvancedEnabled)

                val audioData = SupertonicTTS.generateAudio(normalizedText, requestedLang, stylePath, effectiveSpeed, 0.0f, steps, VOLUME_BOOST_FACTOR, null)

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
    }
}