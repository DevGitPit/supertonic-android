package com.brahmadeo.supertonic.tts.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.json.JSONException

class LexiconManagerTest {

    @Test
    fun testParseImportJson_standardFields() {
        val json = """
            [
              {
                "term": "LLMs",
                "replacement": "L L Ems",
                "ignoreCase": true,
                "isRegex": false
              }
            ]
        """.trimIndent()

        val result = LexiconManager.parseImportJson(json)
        assertEquals(1, result.items.size)
        assertEquals(0, result.skippedCount)

        val item = result.items[0]
        assertEquals("LLMs", item.term)
        assertEquals("L L Ems", item.replacement)
        assertTrue(item.ignoreCase)
        assertEquals(false, item.isRegex)
    }

    @Test
    fun testParseImportJson_withAliases() {
        val json = """
            [
              {
                "word": "read",
                "pronunciation": "red",
                "ignoreCase": false
              },
              {
                "word": "cat",
                "ipa": "kat"
              }
            ]
        """.trimIndent()

        val result = LexiconManager.parseImportJson(json)
        assertEquals(2, result.items.size)
        assertEquals(0, result.skippedCount)

        assertEquals("read", result.items[0].term)
        assertEquals("red", result.items[0].replacement)
        assertEquals(false, result.items[0].ignoreCase)

        assertEquals("cat", result.items[1].term)
        assertEquals("kat", result.items[1].replacement)
        assertTrue(result.items[1].ignoreCase) // Default to true
    }

    @Test
    fun testParseImportJson_missingRequiredFields() {
        val json = """
            [
              {
                "term": "LLMs"
              },
              {
                "replacement": "L L Ems"
              },
              {
                "term": "valid",
                "replacement": "value"
              },
              {
                "word": "another_valid",
                "ipa": "replacement_val"
              },
              {
                "other": "garbage"
              }
            ]
        """.trimIndent()

        val result = LexiconManager.parseImportJson(json)
        assertEquals(2, result.items.size)
        assertEquals(3, result.skippedCount)

        assertEquals("valid", result.items[0].term)
        assertEquals("another_valid", result.items[1].term)
    }

    @Test
    fun testParseImportJson_invalidJsonString() {
        val json = "{ invalid json }"
        try {
            LexiconManager.parseImportJson(json)
            fail("Expected JSONException")
        } catch (e: JSONException) {
            // Expected
        }
    }
}
