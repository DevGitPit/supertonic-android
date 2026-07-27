package com.brahmadeo.supertonic.tts.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

data class LexiconItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var term: String,
    var replacement: String,
    var ignoreCase: Boolean = true,
    var isRegex: Boolean = false
)

data class LexiconImportResult(
    val items: List<LexiconItem>,
    val skippedCount: Int
)

object LexiconManager {
    private const val FILE_NAME = "user_lexicon.json"
    private var cachedRules: List<LexiconItem> = emptyList()
    @Volatile private var isLoaded = false

    fun load(context: Context): List<LexiconItem> {
        // Always reload from file if not loaded or if requested, 
        // but for performance we cache.
        if (isLoaded) return cachedRules
        
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            cachedRules = emptyList()
            isLoaded = true
            return cachedRules
        }

        val items = mutableListOf<LexiconItem>()
        try {
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(LexiconItem(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    term = obj.getString("term"),
                    replacement = obj.getString("replacement"),
                    ignoreCase = obj.optBoolean("ignoreCase", true),
                    isRegex = obj.optBoolean("isRegex", false)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedRules = items
        isLoaded = true
        return items
    }

    fun save(context: Context, items: List<LexiconItem>) {
        cachedRules = items.toList() // Update cache immediately
        isLoaded = true
        
        try {
            val jsonArray = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("term", item.term)
                obj.put("replacement", item.replacement)
                obj.put("ignoreCase", item.ignoreCase)
                obj.put("isRegex", item.isRegex)
                jsonArray.put(obj)
            }
            
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun apply(text: String): String {
        // If not loaded, we can't apply rules safely without context to load them.
        // Consumers must ensure load(context) is called at app start.
        if (cachedRules.isEmpty()) return text
        
        var processed = text
        
        for (item in cachedRules) {
            if (item.term.isBlank()) continue
            
            val flags = if (item.ignoreCase) Pattern.CASE_INSENSITIVE else 0
            
            try {
                val pattern = if (item.isRegex) {
                    Pattern.compile(item.term, flags)
                } else {
                    // Whole word matching with quoting
                    Pattern.compile("\\b${Pattern.quote(item.term)}\\b", flags)
                }
                processed = pattern.matcher(processed).replaceAll(item.replacement)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return processed
    }
    
    // Force reload (useful when returning from LexiconActivity)
    fun reload(context: Context) {
        isLoaded = false
        load(context)
    }

    fun parseImportJson(jsonString: String): LexiconImportResult {
        val items = mutableListOf<LexiconItem>()
        var skippedCount = 0
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i)
                if (obj == null) {
                    skippedCount++
                    continue
                }

                val term = when {
                    obj.has("term") -> obj.optString("term")
                    obj.has("word") -> obj.optString("word")
                    else -> null
                }

                val replacement = when {
                    obj.has("replacement") -> obj.optString("replacement")
                    obj.has("pronunciation") -> obj.optString("pronunciation")
                    obj.has("ipa") -> obj.optString("ipa")
                    else -> null
                }

                if (!term.isNullOrBlank() && !replacement.isNullOrBlank()) {
                    items.add(LexiconItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        term = term.trim(),
                        replacement = replacement.trim(),
                        ignoreCase = obj.optBoolean("ignoreCase", true),
                        isRegex = obj.optBoolean("isRegex", false)
                    ))
                } else {
                    skippedCount++
                }
            }
        } catch (e: Exception) {
            throw e
        }
        return LexiconImportResult(items, skippedCount)
    }
}
