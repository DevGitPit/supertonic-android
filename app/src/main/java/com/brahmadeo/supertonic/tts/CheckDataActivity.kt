package com.brahmadeo.supertonic.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.brahmadeo.supertonic.tts.utils.AssetManager
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
        val modelVersion = AssetManager.resolveModelVersion(preferredEngine, savedLang)

        val availableVoices = ArrayList<String>()
        val unavailableVoices = ArrayList<String>()

        if (modelVersion == "v1") {
            // English (v1)
            if (AssetManager.isVersionReady(this, "v1")) {
                availableVoices.add("eng-USA")
            } else {
                unavailableVoices.add("eng-USA")
            }
        } else if (modelVersion == "v2") {
            // Multilingual models (v2)
            val v2Langs = listOf("eng-USA", "kor-KOR", "spa-ESP", "por-PRT", "fra-FRA")
            if (AssetManager.isVersionReady(this, "v2")) {
                availableVoices.addAll(v2Langs)
            } else {
                unavailableVoices.addAll(v2Langs)
            }
        } else {
            // Multilingual models (v3)
            val v3Langs = AssetManager.V3_LANGUAGES
                .filter { it != "na" }
                .map { lang ->
                    when (lang) {
                        "en" -> "eng-USA"
                        "ko" -> "kor-KOR"
                        "ja" -> "jpn-JPN"
                        "ar" -> "ara-SAU"
                        "bg" -> "bul-BGR"
                        "cs" -> "ces-CZE"
                        "da" -> "dan-DNK"
                        "de" -> "deu-DEU"
                        "el" -> "ell-GRC"
                        "es" -> "spa-ESP"
                        "et" -> "est-EST"
                        "fi" -> "fin-FIN"
                        "fr" -> "fra-FRA"
                        "hi" -> "hin-IND"
                        "hr" -> "hrv-HRV"
                        "hu" -> "hun-HUN"
                        "id" -> "ind-IDN"
                        "it" -> "ita-ITA"
                        "lt" -> "lit-LTU"
                        "lv" -> "lav-LVA"
                        "nl" -> "nld-NLD"
                        "pl" -> "pol-POL"
                        "pt" -> "por-PRT"
                        "ro" -> "ron-ROU"
                        "ru" -> "rus-RUS"
                        "sk" -> "slk-SVK"
                        "sl" -> "slv-SVN"
                        "sv" -> "swe-SWE"
                        "tr" -> "tur-TUR"
                        "uk" -> "ukr-UKR"
                        "vi" -> "vie-VNM"
                        else -> {
                            try {
                                val loc = java.util.Locale.forLanguageTag(lang)
                                "${loc.isO3Language}-${loc.isO3Language.uppercase()}"
                            } catch (_: Exception) {
                                "$lang-$lang"
                            }
                        }
                    }
                }
            if (AssetManager.isVersionReady(this, "v3")) {
                availableVoices.addAll(v3Langs)
            } else {
                unavailableVoices.addAll(v3Langs)
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
