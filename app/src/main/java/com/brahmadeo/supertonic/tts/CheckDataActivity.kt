package com.brahmadeo.supertonic.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.brahmadeo.supertonic.tts.utils.AssetManager
import com.brahmadeo.supertonic.tts.utils.ModelVersion
import java.io.File
import java.util.ArrayList

/**
 * Activity that handles the CHECK_TTS_DATA intent.
 * Android system TTS settings (and apps like Tasker) call this to discover
 * which locales the engine supports. Returns the union of voices across
 * every installed model (v1 English, v2 multilingual, v3 31-lang), not just
 * the user's currently selected language.
 */
class CheckDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val availableVoices = ArrayList<String>()
        val unavailableVoices = ArrayList<String>()

        fun voiceTag(langCode: String): String? {
            val (iso3, country) = ModelVersion.LANG_ISO3[langCode] ?: return null
            return "$iso3-$country"
        }

        fun addLangs(version: ModelVersion, target: ArrayList<String>) {
            version.supportedLangs.forEach { lang -> voiceTag(lang)?.let { target.add(it) } }
        }

        val v1Installed = AssetManager.isV1Ready(this) && File(filesDir, "${ModelVersion.V1.dirName}/onnx").exists()
        val v2Installed = AssetManager.isV2Ready(this) && File(filesDir, "${ModelVersion.V2.dirName}/onnx").exists()
        val v3Installed = AssetManager.isV3Ready(this) && File(filesDir, "${ModelVersion.V3.dirName}/onnx").exists()

        if (v3Installed) {
            // v3 covers all 31 langs (English routed to v1 at synthesis time)
            addLangs(ModelVersion.V3, availableVoices)
        } else {
            if (v1Installed) addLangs(ModelVersion.V1, availableVoices) else addLangs(ModelVersion.V1, unavailableVoices)
            if (v2Installed) addLangs(ModelVersion.V2, availableVoices) else addLangs(ModelVersion.V2, unavailableVoices)
        }

        val result = if (availableVoices.isNotEmpty()) {
            TextToSpeech.Engine.CHECK_VOICE_DATA_PASS
        } else {
            TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL
        }

        val returnIntent = Intent()
        returnIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices)
        returnIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, unavailableVoices)

        setResult(result, returnIntent)
        finish()
    }
}
