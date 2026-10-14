package com.brahmadeo.supertonic.tts.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelEngineResolutionTest {

    @Test
    fun testIsLanguageSupported() {
        // v1: English only
        assertTrue(AssetManager.isLanguageSupported("v1", "en"))
        assertTrue(AssetManager.isLanguageSupported("v1", "en-US"))
        assertFalse(AssetManager.isLanguageSupported("v1", "fr"))
        assertFalse(AssetManager.isLanguageSupported("v1", "hi"))
        assertFalse(AssetManager.isLanguageSupported("v1", "ko"))

        // v2: en, fr, pt, es, ko
        assertTrue(AssetManager.isLanguageSupported("v2", "en"))
        assertTrue(AssetManager.isLanguageSupported("v2", "fr"))
        assertTrue(AssetManager.isLanguageSupported("v2", "pt"))
        assertTrue(AssetManager.isLanguageSupported("v2", "es"))
        assertTrue(AssetManager.isLanguageSupported("v2", "ko"))
        assertFalse(AssetManager.isLanguageSupported("v2", "hi"))
        assertFalse(AssetManager.isLanguageSupported("v2", "ja"))
        assertFalse(AssetManager.isLanguageSupported("v2", "de"))

        // v3: supports all languages
        assertTrue(AssetManager.isLanguageSupported("v3", "en"))
        assertTrue(AssetManager.isLanguageSupported("v3", "fr"))
        assertTrue(AssetManager.isLanguageSupported("v3", "hi"))
        assertTrue(AssetManager.isLanguageSupported("v3", "ja"))
        assertTrue(AssetManager.isLanguageSupported("v3", "ar"))
    }

    @Test
    fun testSanitizeLanguageForModel() {
        // When model is v1, non-English must sanitize to "en"
        assertEquals("en", AssetManager.sanitizeLanguageForModel("v1", "en"))
        assertEquals("en", AssetManager.sanitizeLanguageForModel("v1", "hi"))
        assertEquals("en", AssetManager.sanitizeLanguageForModel("v1", "fr"))

        // When model is v2, v2 languages remain intact, others sanitize to "en"
        assertEquals("fr", AssetManager.sanitizeLanguageForModel("v2", "fr"))
        assertEquals("ko", AssetManager.sanitizeLanguageForModel("v2", "ko"))
        assertEquals("en", AssetManager.sanitizeLanguageForModel("v2", "hi"))
        assertEquals("en", AssetManager.sanitizeLanguageForModel("v2", "ja"))

        // When model is v3, all languages remain intact
        assertEquals("hi", AssetManager.sanitizeLanguageForModel("v3", "hi"))
        assertEquals("ja", AssetManager.sanitizeLanguageForModel("v3", "ja"))
        assertEquals("en", AssetManager.sanitizeLanguageForModel("v3", "en"))
    }

    @Test
    fun testResolveModelVersion() {
        // Default engine resolution
        assertEquals("v1", AssetManager.resolveModelVersion("default", "en"))
        assertEquals("v2", AssetManager.resolveModelVersion("default", "fr"))
        assertEquals("v2", AssetManager.resolveModelVersion("default", "ko"))
        assertEquals("v3", AssetManager.resolveModelVersion("default", "hi"))
        assertEquals("v3", AssetManager.resolveModelVersion("default", "ja"))

        // Empty engine resolution defaults to language model version
        assertEquals("v1", AssetManager.resolveModelVersion("", "en"))
        assertEquals("v3", AssetManager.resolveModelVersion("", "hi"))

        // Fixed v1 engine
        assertEquals("v1", AssetManager.resolveModelVersion("v1", "en"))
        // If v1 doesn't support Hindi, fallback to appropriate version (v3)
        assertEquals("v3", AssetManager.resolveModelVersion("v1", "hi"))

        // Fixed v2 engine
        assertEquals("v2", AssetManager.resolveModelVersion("v2", "en"))
        assertEquals("v2", AssetManager.resolveModelVersion("v2", "fr"))
        // If v2 doesn't support Hindi, fallback to appropriate version (v3)
        assertEquals("v3", AssetManager.resolveModelVersion("v2", "hi"))

        // Fixed v3 engine
        assertEquals("v3", AssetManager.resolveModelVersion("v3", "en"))
        assertEquals("v3", AssetManager.resolveModelVersion("v3", "fr"))
        assertEquals("v3", AssetManager.resolveModelVersion("v3", "hi"))
    }
}
