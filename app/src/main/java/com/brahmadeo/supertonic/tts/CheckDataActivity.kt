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
        val preferredEngine = prefs.getString("preferred_model_engine", "default") ?: "default"
        val modelVersion = com.brahmadeo.supertonic.tts.utils.AssetManager.resolveModelVersion(preferredEngine, savedLang)

        val availableVoices = ArrayList<String>()
        val unavailableVoices = ArrayList<String>()

        if (modelVersion == "v1") {
            // English (v1)
            val v1Dir = File(filesDir, "v1/onnx")
            if (v1Dir.exists()) {
                availableVoices.add("eng-USA")
            } else {
                unavailableVoices.add("eng-USA")
            }
        } else if (modelVersion == "v2") {
            // Multilingual models (v2)
            val v2Dir = File(filesDir, "v2/onnx")
            val v2Langs = listOf("eng-USA", "kor-KOR", "spa-ESP", "por-PRT", "fra-FRA")
            if (v2Dir.exists()) {
                availableVoices.addAll(v2Langs)
            } else {
                unavailableVoices.addAll(v2Langs)
            }
        } else {
            // Multilingual models (v3)
            val v3Dir = File(filesDir, "v3/onnx")
            val v3Langs = listOf("eng-USA", "kor-KOR", "spa-ESP", "por-PRT", "fra-FRA", "jpn-JPN", "deu-DEU")
            if (v3Dir.exists()) {
                availableVoices.addAll(v3Langs)
            } else {
                unavailableVoices.addAll(v3Langs)
            }
        }

        val result = if (modelVersion == "v1" || availableVoices.isNotEmpty()) {
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
