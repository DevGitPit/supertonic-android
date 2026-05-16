package com.brahmadeo.supertonic.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.io.File
import java.util.ArrayList

/**
 * Activity that handles the CHECK_TTS_DATA intent.
 * This is required by some apps (like Tasker) to verify that the TTS engine is functional
 * and to discover which languages are supported.
 */
class CheckDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val savedLang = prefs.getString("selected_lang", "en") ?: "en"
        val isV3Ready = com.brahmadeo.supertonic.tts.utils.AssetManager.isV3Ready(this)
        val modelVersion = when {
            savedLang == "en" -> "v1"
            isV3Ready -> "v3"
            else -> "v2"
        }

        val availableVoices = ArrayList<String>()
        val unavailableVoices = ArrayList<String>()

        if (modelVersion == "v3") {
            val v3Dir = File(filesDir, "v3/onnx")
            if (v3Dir.exists()) {
                availableVoices.add("kor-KOR")
                availableVoices.add("spa-ESP")
                availableVoices.add("por-PRT")
                availableVoices.add("fra-FRA")
            } else {
                unavailableVoices.add("kor-KOR")
                unavailableVoices.add("spa-ESP")
                unavailableVoices.add("por-PRT")
                unavailableVoices.add("fra-FRA")
            }
        } else if (modelVersion == "v1") {
            // English (v1) is bundled with the app and copied on first run
            availableVoices.add("eng-USA")
        } else {
            // Check if multilingual models (v2) are present
            val v2Dir = File(filesDir, "v2/onnx")
            if (v2Dir.exists()) {
                availableVoices.add("kor-KOR")
                availableVoices.add("spa-ESP")
                availableVoices.add("por-PRT")
                availableVoices.add("fra-FRA")
            } else {
                // These could be downloaded via the app's UI
                unavailableVoices.add("kor-KOR")
                unavailableVoices.add("spa-ESP")
                unavailableVoices.add("por-PRT")
                unavailableVoices.add("fra-FRA")
            }
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
