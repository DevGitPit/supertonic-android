package com.brahmadeo.supertonic.tts.utils

import com.brahmadeo.supertonic.tts.SupertonicTTS
import com.brahmadeo.supertonic.tts.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedTest {

    @Test
    fun testPlaybackSpeedClamping() {
        // Exercise production validation with out-of-range, boundary, and in-range inputs
        assertEquals(PlaybackSpeedUtils.MIN_PLAYBACK_SPEED, PlaybackSpeedUtils.clampPlaybackSpeed(-1.0f), 0.001f)
        assertEquals(PlaybackSpeedUtils.MIN_PLAYBACK_SPEED, PlaybackSpeedUtils.clampPlaybackSpeed(0.0f), 0.001f)
        assertEquals(PlaybackSpeedUtils.MIN_PLAYBACK_SPEED, PlaybackSpeedUtils.clampPlaybackSpeed(0.2f), 0.001f)
        assertEquals(0.5f, PlaybackSpeedUtils.clampPlaybackSpeed(PlaybackSpeedUtils.MIN_PLAYBACK_SPEED), 0.001f)

        assertEquals(0.8f, PlaybackSpeedUtils.clampPlaybackSpeed(0.8f), 0.001f)
        assertEquals(1.0f, PlaybackSpeedUtils.clampPlaybackSpeed(1.0f), 0.001f)
        assertEquals(1.5f, PlaybackSpeedUtils.clampPlaybackSpeed(1.5f), 0.001f)
        assertEquals(2.0f, PlaybackSpeedUtils.clampPlaybackSpeed(2.0f), 0.001f)
        assertEquals(2.5f, PlaybackSpeedUtils.clampPlaybackSpeed(2.5f), 0.001f)

        assertEquals(3.0f, PlaybackSpeedUtils.clampPlaybackSpeed(PlaybackSpeedUtils.MAX_PLAYBACK_SPEED), 0.001f)
        assertEquals(PlaybackSpeedUtils.MAX_PLAYBACK_SPEED, PlaybackSpeedUtils.clampPlaybackSpeed(3.5f), 0.001f)
        assertEquals(PlaybackSpeedUtils.MAX_PLAYBACK_SPEED, PlaybackSpeedUtils.clampPlaybackSpeed(4.5f), 0.001f)
        assertEquals(PlaybackSpeedUtils.MAX_PLAYBACK_SPEED, PlaybackSpeedUtils.clampPlaybackSpeed(10.0f), 0.001f)
    }

    @Test
    fun testNeuralSynthesisSpeedSafetyClamping() {
        // Exercise production neural synthesis speed clamping ([SupertonicTTS.MIN_SPEED, SupertonicTTS.MAX_NEURAL_SPEED])
        // Clamping to [0.5f, 1.4f] ensures the neural flow-matching model
        // duration predictor does not collapse phoneme frames (preventing word skipping).
        assertEquals(SupertonicTTS.MIN_SPEED, PlaybackSpeedUtils.clampSynthesisSpeed(-0.5f), 0.001f)
        assertEquals(SupertonicTTS.MIN_SPEED, PlaybackSpeedUtils.clampSynthesisSpeed(0.1f), 0.001f)
        assertEquals(0.5f, PlaybackSpeedUtils.clampSynthesisSpeed(SupertonicTTS.MIN_SPEED), 0.001f)

        assertEquals(1.0f, PlaybackSpeedUtils.clampSynthesisSpeed(1.0f), 0.001f)
        assertEquals(1.25f, PlaybackSpeedUtils.clampSynthesisSpeed(1.25f), 0.001f)
        assertEquals(1.4f, PlaybackSpeedUtils.clampSynthesisSpeed(SupertonicTTS.MAX_NEURAL_SPEED), 0.001f)

        assertEquals(SupertonicTTS.MAX_NEURAL_SPEED, PlaybackSpeedUtils.clampSynthesisSpeed(1.5f), 0.001f)
        assertEquals(SupertonicTTS.MAX_NEURAL_SPEED, PlaybackSpeedUtils.clampSynthesisSpeed(2.0f), 0.001f)
        assertEquals(SupertonicTTS.MAX_NEURAL_SPEED, PlaybackSpeedUtils.clampSynthesisSpeed(2.5f), 0.001f)
        assertEquals(SupertonicTTS.MAX_NEURAL_SPEED, PlaybackSpeedUtils.clampSynthesisSpeed(5.0f), 0.001f)
    }

    @Test
    fun testSpeedCategoryBoundary() {
        // Exercise production classification logic directly against HIGH_SPEED_THRESHOLD
        assertFalse(PlaybackSpeedUtils.isHighSpeed(0.5f))
        assertFalse(PlaybackSpeedUtils.isHighSpeed(0.8f))
        assertFalse(PlaybackSpeedUtils.isHighSpeed(0.9f))
        assertFalse(PlaybackSpeedUtils.isHighSpeed(1.0f))
        assertFalse(PlaybackSpeedUtils.isHighSpeed(1.25f))
        assertFalse(PlaybackSpeedUtils.isHighSpeed(PlaybackSpeedUtils.HIGH_SPEED_THRESHOLD))

        assertTrue(PlaybackSpeedUtils.isHighSpeed(PlaybackSpeedUtils.HIGH_SPEED_THRESHOLD + 0.01f))
        assertTrue(PlaybackSpeedUtils.isHighSpeed(1.55f))
        assertTrue(PlaybackSpeedUtils.isHighSpeed(1.75f))
        assertTrue(PlaybackSpeedUtils.isHighSpeed(2.0f))
        assertTrue(PlaybackSpeedUtils.isHighSpeed(2.5f))
    }

    @Test
    fun testSliderRangeIntervals() {
        // Assert against production slider constants
        val minSpeed = PlaybackSpeedUtils.SLIDER_MIN_SPEED
        val maxSpeed = PlaybackSpeedUtils.SLIDER_MAX_SPEED
        val steps = PlaybackSpeedUtils.SLIDER_STEPS
        val intervals = steps + 1
        val stepSize = (maxSpeed - minSpeed) / intervals

        assertEquals(0.8f, minSpeed, 0.001f)
        assertEquals(2.5f, maxSpeed, 0.001f)
        assertEquals(33, steps)
        assertEquals(0.05f, stepSize, 0.0001f)

        // Test common snap values
        val stepAt = { index: Int -> minSpeed + index * stepSize }
        assertEquals(0.80f, stepAt(0), 0.001f)
        assertEquals(0.90f, stepAt(2), 0.001f)
        assertEquals(1.00f, stepAt(4), 0.001f)
        assertEquals(1.10f, stepAt(6), 0.001f)
        assertEquals(1.50f, stepAt(14), 0.001f)
        assertEquals(2.00f, stepAt(24), 0.001f)
        assertEquals(2.50f, stepAt(34), 0.001f)
    }

    @Test
    fun testDefaultSpeedResetAndPresets() {
        // Assert against production default speed and presets list
        val defaultSpeed = MainViewModel.DEFAULT_SPEED
        val presets = PlaybackSpeedUtils.SPEED_PRESETS

        assertTrue(presets.contains(defaultSpeed))
        assertEquals(1.10f, defaultSpeed, 0.0001f)
    }

    @Test
    fun testPlaybackSpeedClampingWithNaN() {
        // Assert that Float.NaN returns the default finite fallback before coerceIn
        assertEquals(PlaybackSpeedUtils.DEFAULT_PLAYBACK_SPEED, PlaybackSpeedUtils.clampPlaybackSpeed(Float.NaN), 0.001f)
        // Assert that Float.NaN returns an explicit fallback before coerceIn
        assertEquals(1.5f, PlaybackSpeedUtils.clampPlaybackSpeed(Float.NaN, 1.5f), 0.001f)
        assertEquals(1.0f, PlaybackSpeedUtils.clampPlaybackSpeed(Float.NaN, 1.0f), 0.001f)
    }

    @Test
    fun testStartupRestorationValidationAndClamping() {
        // Startup restoration path: validate NaN before clamping persisted speed
        val restoreSpeed = { persistedSpeed: Float, fallback: Float ->
            val validated = if (persistedSpeed.isNaN()) fallback else persistedSpeed
            PlaybackSpeedUtils.clampPlaybackSpeed(validated)
        }

        // When persisted speed is NaN, it should resolve to finite fallback
        assertEquals(1.1f, restoreSpeed(Float.NaN, 1.1f), 0.001f)
        assertEquals(PlaybackSpeedUtils.DEFAULT_PLAYBACK_SPEED, restoreSpeed(Float.NaN, PlaybackSpeedUtils.DEFAULT_PLAYBACK_SPEED), 0.001f)

        // When persisted speed is out of bounds, it should clamp to safe bounds
        assertEquals(PlaybackSpeedUtils.MAX_PLAYBACK_SPEED, restoreSpeed(5.0f, 1.1f), 0.001f)
        assertEquals(PlaybackSpeedUtils.MIN_PLAYBACK_SPEED, restoreSpeed(-2.0f, 1.1f), 0.001f)

        // Valid persisted speed is retained
        assertEquals(1.25f, restoreSpeed(1.25f, 1.1f), 0.001f)
    }
}
