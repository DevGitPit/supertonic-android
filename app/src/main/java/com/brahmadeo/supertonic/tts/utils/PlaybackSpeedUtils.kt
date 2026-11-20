package com.brahmadeo.supertonic.tts.utils

import com.brahmadeo.supertonic.tts.SupertonicTTS

object PlaybackSpeedUtils {
    const val MIN_PLAYBACK_SPEED = 0.5f
    const val MAX_PLAYBACK_SPEED = 3.0f

    const val DEFAULT_PLAYBACK_SPEED = 1.1f

    const val HIGH_SPEED_THRESHOLD = 1.5f

    const val SLIDER_MIN_SPEED = 0.8f
    const val SLIDER_MAX_SPEED = 2.5f
    const val SLIDER_STEPS = 33

    val SPEED_PRESETS = listOf(0.8f, 1.0f, 1.10f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f)

    fun clampPlaybackSpeed(speed: Float, fallback: Float = DEFAULT_PLAYBACK_SPEED): Float {
        if (speed.isNaN()) return fallback
        return speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
    }

    fun clampSynthesisSpeed(speed: Float): Float =
        speed.coerceIn(SupertonicTTS.MIN_SPEED, SupertonicTTS.MAX_NEURAL_SPEED)

    fun isHighSpeed(speed: Float): Boolean =
        speed > HIGH_SPEED_THRESHOLD
}
