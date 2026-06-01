package com.brahmadeo.supertonic.tts.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.edit

data class RecentBook(
    val title: String,
    val path: String
)

object EbookManager {
    private const val PREFS_NAME = "EbookPrefs"
    private const val KEY_RECENT_BOOKS = "recent_books"
    private const val MAX_BOOKS = 15

    fun getRecentBooks(context: Context): List<RecentBook> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_RECENT_BOOKS, "[]") ?: "[]"
            val jsonArray = JSONArray(jsonString)
            val books = mutableListOf<RecentBook>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                books.add(RecentBook(obj.getString("title"), obj.getString("path")))
            }
            books
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun addBook(context: Context, title: String, path: String) {
        try {
            val books = getRecentBooks(context).toMutableList()
            books.removeAll { it.path == path }
            books.add(0, RecentBook(title, path))
            
            val trimmedBooks = if (books.size > MAX_BOOKS) books.take(MAX_BOOKS) else books
            
            val jsonArray = JSONArray()
            trimmedBooks.forEach {
                val obj = JSONObject()
                obj.put("title", it.title)
                obj.put("path", it.path)
                jsonArray.put(obj)
            }
            
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    putString(KEY_RECENT_BOOKS, jsonArray.toString())
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeBook(context: Context, path: String) {
        try {
            val books = getRecentBooks(context).toMutableList()
            books.removeAll { it.path == path }
            
            val jsonArray = JSONArray()
            books.forEach {
                val obj = JSONObject()
                obj.put("title", it.title)
                obj.put("path", it.path)
                jsonArray.put(obj)
            }
            
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    putString(KEY_RECENT_BOOKS, jsonArray.toString())
                    remove("last_read_$path")
                    remove("word_counts_$path")
                }

            // Also, if the file resides inside our private app directory, delete it.
            val file = File(path)
            if (file.exists() && file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun importBook(context: Context, uri: Uri): String? {
        try {
            val contentResolver = context.contentResolver
            
            // Try to get filename from content resolver
            var displayName: String? = null
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            // Try to get correct extension
            val mimeType = contentResolver.getType(uri)
            val uriString = uri.toString().lowercase()
            val extension = when {
                mimeType == "application/pdf" -> "pdf"
                mimeType == "application/epub+zip" -> "epub"
                displayName?.lowercase()?.endsWith(".pdf") == true -> "pdf"
                displayName?.lowercase()?.endsWith(".epub") == true -> "epub"
                uriString.endsWith(".pdf") || uriString.contains(".pdf?") -> "pdf"
                uriString.endsWith(".epub") || uriString.contains(".epub?") -> "epub"
                mimeType != null -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "epub"
                else -> "epub"
            }
            
            val fileName = "book_${System.currentTimeMillis()}.$extension"
            val destFile = File(context.filesDir, "ebooks/$fileName")
            destFile.parentFile?.mkdirs()

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            
            return destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun setLastReadChapter(context: Context, bookPath: String, chapterHref: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putString("last_read_$bookPath", chapterHref)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLastReadChapter(context: Context, bookPath: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString("last_read_$bookPath", null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getWordCounts(context: Context, bookPath: String): Map<String, Int> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("word_counts_$bookPath", "{}") ?: "{}"
            val jsonObj = JSONObject(jsonStr)
            val result = mutableMapOf<String, Int>()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = jsonObj.getInt(key)
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    fun saveWordCount(context: Context, bookPath: String, chapterHref: String, wordCount: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("word_counts_$bookPath", "{}") ?: "{}"
            val jsonObj = JSONObject(jsonStr)
            jsonObj.put(chapterHref, wordCount)
            prefs.edit {
                putString("word_counts_$bookPath", jsonObj.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
