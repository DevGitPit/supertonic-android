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
import com.brahmadeo.supertonic.tts.utils.ModelVersion
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
            val modelVersion = resolveModelVersion(savedLang)

            val modelPath = File(filesDir, "${modelVersion.dirName}/onnx").absolutePath
            val libPath = applicationInfo.nativeLibraryDir + "/libonnxruntime.so"

            SupertonicTTS.initialize(modelPath, libPath)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun resolveModelVersion(savedLang: String): ModelVersion = ModelVersion.resolve(
        savedLang = savedLang,
        isV2Ready = AssetManager.isV2Ready(this),
        isV3Ready = AssetManager.isV3Ready(this),
    )

    private fun resolveModelVersion(): ModelVersion {
        val savedLang = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).getString("selected_lang", "en") ?: "en"
        return resolveModelVersion(savedLang)
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang == null) return TextToSpeech.LANG_NOT_SUPPORTED
        val normalizedLang = ModelVersion.normalize(lang)

        fun available(dir: File): Int = when {
            !dir.exists() -> TextToSpeech.LANG_MISSING_DATA
            !country.isNullOrEmpty() -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            else -> TextToSpeech.LANG_AVAILABLE
        }

        if (normalizedLang == "en" && AssetManager.isV1Ready(this)) {
            return available(File(filesDir, "${ModelVersion.V1.dirName}/onnx"))
        }
        if (AssetManager.isV3Ready(this) && normalizedLang in ModelVersion.V3.supportedLangs) {
            return available(File(filesDir, "${ModelVersion.V3.dirName}/onnx"))
        }
        if (AssetManager.isV2Ready(this) && normalizedLang in ModelVersion.V2.supportedLangs) {
            return available(File(filesDir, "${ModelVersion.V2.dirName}/onnx"))
        }

        val anyKnown = normalizedLang in ModelVersion.V3.supportedLangs ||
            normalizedLang in ModelVersion.V2.supportedLangs ||
            normalizedLang == "en"
        return if (anyKnown) TextToSpeech.LANG_MISSING_DATA else TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onGetLanguage(): Array<String> {
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val selectedLang = prefs.getString("selected_lang", "en") ?: "en"
        val (iso3, country) = ModelVersion.LANG_ISO3[selectedLang] ?: ("eng" to "USA")
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
            val modelVersion = resolveModelVersion(ModelVersion.normalize(langPrefix))
            val file = File(filesDir, "${modelVersion.dirName}/voice_styles/$styleName.json")
            if (file.exists()) return TextToSpeech.SUCCESS
        }
        return TextToSpeech.ERROR
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val selected = prefs.getString("selected_voice", "F3.json") ?: "F3.json"
        val voiceName = if (selected.endsWith(".json")) selected.substringBeforeLast(".") else selected
        val prefix = ModelVersion.normalize(lang)
        return "$prefix-supertonic-$voiceName"
    }

    override fun onGetVoices(): List<Voice> {
        val voicesList = mutableListOf<Voice>()
        val voiceNames = listOf("M1", "M2", "M3", "M4", "M5", "F1", "F2", "F3", "F4", "F5")

        fun addVoicesFor(version: ModelVersion) {
            val onnxDir = File(filesDir, "${version.dirName}/onnx")
            if (!onnxDir.exists()) return
            version.supportedLangs.forEach { langCode ->
                val locale = ModelVersion.LANG_LOCALES[langCode] ?: return@forEach
                voiceNames.forEach { name ->
                    voicesList.add(
                        Voice(
                            "$langCode-supertonic-$name",
                            locale,
                            Voice.QUALITY_VERY_HIGH,
                            Voice.LATENCY_NORMAL,
                            false,
                            setOf()
                        )
                    )
                }
            }
        }

        if (AssetManager.isV3Ready(this)) {
            addVoicesFor(ModelVersion.V3)
        } else {
            if (AssetManager.isV1Ready(this)) addVoicesFor(ModelVersion.V1)
            if (AssetManager.isV2Ready(this)) addVoicesFor(ModelVersion.V2)
        }

        return voicesList
    }

    override fun onStop() {
        SupertonicTTS.setCancelled(true)
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
        val requestedLang = ModelVersion.normalize(request.language)
        val prefs = attributionContext.getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)

        val voiceLang = if (requestedVoice != null && requestedVoice.contains("-supertonic-"))
            ModelVersion.normalize(requestedVoice.substringBefore("-supertonic-"))
        else
            requestedLang
        val modelVersion = resolveModelVersion(voiceLang)

        val voiceFile = if (requestedVoice != null && requestedVoice.contains("-supertonic-")) {
            val fileName = requestedVoice.substringAfter("-supertonic-")
            // Sanitize fileName to prevent path traversal
            File(fileName).name + ".json"
        } else {
            prefs.getString("selected_voice", "F3.json") ?: "F3.json"
        }

        val voiceStyleDir = File(filesDir, "${modelVersion.dirName}/voice_styles")
        var stylePath = File(voiceStyleDir, voiceFile).absolutePath

        // Ensure stylePath is within the intended directory
        if (!File(stylePath).canonicalPath.startsWith(voiceStyleDir.canonicalPath)) {
            stylePath = File(voiceStyleDir, "F3.json").absolutePath
        }

        // Handle Voice Mixing (Only if mixing is compatible with modelVersion)
        val isMixing = prefs.getBoolean("is_mixing_enabled", false)
        if (isMixing) {
            val voice2 = prefs.getString("selected_voice_2", "M2.json") ?: "M2.json"
            val stylePath2 = File(voiceStyleDir, voice2).absolutePath
            val alpha = prefs.getFloat("mix_alpha", 0.5f)

            if (File(stylePath).exists() && File(stylePath2).exists()) {
                stylePath = "$stylePath;$stylePath2;$alpha"
            }
        }

        val steps = prefs.getInt("diffusion_steps", 5)

        // Ensure engine is initialized for the correct model
        if (SupertonicTTS.getSoC() == -1) {
             val modelPath = File(filesDir, "${modelVersion.dirName}/onnx").absolutePath
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