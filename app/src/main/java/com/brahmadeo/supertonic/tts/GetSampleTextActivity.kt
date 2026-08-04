package com.brahmadeo.supertonic.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Handles the ACTION_GET_SAMPLE_TEXT intent.
 * This allows the user to Play a sample in the system Settings > Accessibility > Text-to-Speech Output screen,
 * which is useful for confirming functionality and for modulating the Speech Rate and Pitch.
 *
 * Note that we generally _don't_ need to provide our own sample text for every language,
 * since Android Settings has defaults that it can fall back to.
 */
class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Often (though not necessarily?) a 2-letter code,
        // per https://android.googlesource.com/platform/packages/apps/Settings.git/+/7c598253ff60f06f8e6fe046f18fd88e9daa72d3/src/com/android/settings/tts/TextToSpeechSettings.java#486
        // and https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html#getLanguage()
        val language = intent?.getStringExtra("language")?.lowercase(Locale.ROOT).orEmpty()

        val result = Intent()
        if (language.startsWith("en")) {
            result.putExtra(
                TextToSpeech.Engine.EXTRA_SAMPLE_TEXT,
                "This is an example of speech synthesis using Supertonic."
            )
        }
        setResult(TextToSpeech.LANG_AVAILABLE, result)

        finish()
    }
}
