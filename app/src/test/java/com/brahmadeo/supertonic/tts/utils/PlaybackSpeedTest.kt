package com.brahmadeo.supertonic.tts.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedTest {

    @Test
    fun testPlaybackSpeedClamping() {
        val clampSpeed = { speed: Float -> speed.coerceIn(0.5f, 3.0f) }

        assertEquals(0.5f, clampSpeed(0.2f), 0.001f)
        assertEquals(0.8f, clampSpeed(0.8f), 0.001f)
        assertEquals(1.0f, clampSpeed(1.0f), 0.001f)
        assertEquals(1.5f, clampSpeed(1.5f), 0.001f)
        assertEquals(2.0f, clampSpeed(2.0f), 0.001f)
        assertEquals(2.5f, clampSpeed(2.5f), 0.001f)
        assertEquals(3.0f, clampSpeed(3.0f), 0.001f)
        assertEquals(3.0f, clampSpeed(4.5f), 0.001f)
    }

    @Test
    fun testNeuralSynthesisSpeedSafetyClamping() {
        // Clamping to [0.5f, 1.4f] ensures the neural flow-matching model
        // duration predictor does not collapse phoneme frames (preventing word skipping).
        val safeModelSpeed = { speed: Float -> speed.coerceIn(0.5f, 1.4f) }

        assertEquals(0.5f, safeModelSpeed(0.1f), 0.001f)
        assertEquals(1.0f, safeModelSpeed(1.0f), 0.001f)
        assertEquals(1.25f, safeModelSpeed(1.25f), 0.001f)
        assertEquals(1.4f, safeModelSpeed(1.5f), 0.001f)
        assertEquals(1.4f, safeModelSpeed(2.0f), 0.001f)
        assertEquals(1.4f, safeModelSpeed(2.5f), 0.001f)
    }

    @Test
    fun testSpeedCategoryBoundary() {
        val isHighSpeed = { speed: Float -> speed > 1.5f }

        assertFalse(isHighSpeed(0.8f))
        assertFalse(isHighSpeed(0.9f))
        assertFalse(isHighSpeed(1.0f))
        assertFalse(isHighSpeed(1.25f))
        assertFalse(isHighSpeed(1.5f))

        assertTrue(isHighSpeed(1.55f))
        assertTrue(isHighSpeed(1.75f))
        assertTrue(isHighSpeed(2.0f))
        assertTrue(isHighSpeed(2.5f))
    }

    @Test
    fun testSliderRangeIntervals() {
        val minSpeed = 0.8f
        val maxSpeed = 2.5f
        val steps = 33
        val intervals = steps + 1
        val stepSize = (maxSpeed - minSpeed) / intervals

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
        val defaultSpeed = 1.10f
        val presets = listOf(0.8f, 1.0f, 1.10f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f)

        assertTrue(presets.contains(defaultSpeed))
        assertEquals(1.10f, defaultSpeed, 0.0001f)
    }
}
