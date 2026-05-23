package com.brahmadeo.supertonic.tts.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

object AssetManager {
    fun getModelVersionForLanguage(lang: String): String {
        val cleanLang = lang.lowercase().substringBefore("-").substringBefore("_")
        return when (cleanLang) {
            "en" -> "v1"
            "fr", "pt", "es", "ko" -> "v2"
            else -> "v3"
        }
    }

    private const val TAG = "AssetManager"
    private const val BASE_URL_V1 = "https://huggingface.co/Supertone/supertonic/resolve/main"
    private const val BASE_URL_V2 = "https://huggingface.co/Supertone/supertonic-2/resolve/main"
    private const val BASE_URL_V3 = "https://huggingface.co/Supertone/supertonic-3/resolve/main"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_RETRIES = 3
    private const val BUFFER_SIZE = 65_536


    private val V1_FILES = listOf(
        "onnx/duration_predictor.onnx",
        "onnx/text_encoder.onnx",
        "onnx/vector_estimator.onnx",
        "onnx/vocoder.onnx",
        "onnx/tts.json",
        "onnx/unicode_indexer.json",
        // V1 voices
        "voice_styles/M1.json", "voice_styles/M2.json", "voice_styles/M3.json", "voice_styles/M4.json", "voice_styles/M5.json",
        "voice_styles/F1.json", "voice_styles/F2.json", "voice_styles/F3.json", "voice_styles/F4.json", "voice_styles/F5.json"
    )

    private val V2_FILES = listOf(
        "onnx/duration_predictor.onnx",
        "onnx/text_encoder.onnx",
        "onnx/vector_estimator.onnx",
        "onnx/vocoder.onnx",
        "onnx/tts.json",
        "onnx/unicode_indexer.json",
        // V2 voices (same names, different files)
        "voice_styles/M1.json", "voice_styles/M2.json", "voice_styles/M3.json", "voice_styles/M4.json", "voice_styles/M5.json",
        "voice_styles/F1.json", "voice_styles/F2.json", "voice_styles/F3.json", "voice_styles/F4.json", "voice_styles/F5.json"
    )

    private val V3_FILES = V2_FILES

    fun isV1Ready(context: Context): Boolean = checkReady(context, "v1", V1_FILES)
    fun isV2Ready(context: Context): Boolean = checkReady(context, "v2", V2_FILES)
    fun isV3Ready(context: Context): Boolean = checkReady(context, "v3", V3_FILES)

    fun isVersionReady(context: Context, version: String): Boolean {
        return when (version) {
            "v1" -> isV1Ready(context)
            "v2" -> isV2Ready(context)
            "v3" -> isV3Ready(context)
            else -> false
        }
    }

    private fun checkReady(context: Context, version: String, files: List<String>): Boolean {
        val baseDir = File(context.filesDir, version)
        if (!baseDir.exists()) return false
        return files.all { File(baseDir, it).exists() }
    }

    suspend fun downloadV1(context: Context, onProgress: (String, Float, Long, Long) -> Unit) {
        downloadVersion(context, "v1", BASE_URL_V1, V1_FILES, onProgress)
    }

    suspend fun downloadV2(context: Context, onProgress: (String, Float, Long, Long) -> Unit) {
        downloadVersion(context, "v2", BASE_URL_V2, V2_FILES, onProgress)
    }

    suspend fun downloadV3(context: Context, onProgress: (String, Float, Long, Long) -> Unit) {
        downloadVersion(context, "v3", BASE_URL_V3, V3_FILES, onProgress)
    }

    fun deleteVersion(context: Context, version: String) {
        val baseDir = File(context.filesDir, version)
        if (baseDir.exists()) {
            baseDir.deleteRecursively()
        }
    }

    /**
     * Probes a remote file's total size without downloading it. Uses a one-byte range request
     * (Range: bytes=0-0) because HEAD responses from HuggingFace omit Content-Length for
     * redirected URLs, while a GET with a range header returns the full size in Content-Range.
     */
    private suspend fun probeFileSize(urlString: String): Long {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val conn = (URI(urlString).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Range", "bytes=0-0")
                }
                try {
                    conn.connect()
                    val responseCode = conn.responseCode
                    if (responseCode == 429) {
                        val retryAfter = conn.getHeaderField("Retry-After")?.toLongOrNull() ?: 60L
                        Log.w(TAG, "Rate limited probing $urlString, waiting ${retryAfter}s")
                        delay(retryAfter * 1_000)
                        lastException = Exception("HTTP 429 for $urlString")
                        return@repeat
                    }
                    val contentRange = conn.getHeaderField("Content-Range")
                    if (contentRange != null) {
                        val total = contentRange.substringAfterLast('/').trim().toLongOrNull()
                        if (total != null && total > 0) return total
                    }
                    return conn.contentLengthLong.takeIf { it > 0 } ?: 0L
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Probe attempt ${attempt + 1}/$MAX_RETRIES failed for $urlString: ${e.message}")
                if (attempt < MAX_RETRIES - 1) delay(1_000L * (attempt + 1))
            }
        }
        throw lastException ?: Exception("Probe failed after $MAX_RETRIES attempts")
    }

    private suspend fun downloadFileWithResume(
        url: String,
        targetFile: File,
        onChunk: (bytesWritten: Long) -> Unit
    ) {
        val partFile = File(targetFile.parent, "${targetFile.name}.part")

        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val resumeOffset = if (partFile.exists()) partFile.length() else 0L
                val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    if (resumeOffset > 0) setRequestProperty("Range", "bytes=$resumeOffset-")
                }
                try {
                    val responseCode = conn.responseCode
                    if (responseCode == 429) {
                        val retryAfter = conn.getHeaderField("Retry-After")?.toLongOrNull() ?: 60L
                        Log.w(TAG, "Rate limited downloading $url, waiting ${retryAfter}s")
                        delay(retryAfter * 1_000)
                        throw Exception("HTTP 429 for $url")
                    }
                    if (responseCode == 416) {
                        Log.w(TAG, "Range not satisfiable for $url, discarding partial file and retrying from start")
                        partFile.delete()
                        throw Exception("HTTP 416 for $url")
                    }
                    val appending = responseCode == HttpURLConnection.HTTP_PARTIAL
                    if (responseCode != HttpURLConnection.HTTP_OK && !appending) {
                        throw Exception("HTTP $responseCode for $url")
                    }
                    conn.inputStream.use { input ->
                        FileOutputStream(partFile, appending).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                onChunk(bytesRead.toLong())
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    throw java.io.IOException("Failed to rename ${partFile.name} to ${targetFile.absolutePath}")
                }
                return
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt + 1}/$MAX_RETRIES failed for $url: ${e.message}")
                if (attempt < MAX_RETRIES - 1) delay(1_000L * (attempt + 1))
            }
        }
        throw lastException ?: Exception("Download failed after $MAX_RETRIES attempts")
    }

    private suspend fun downloadVersion(
        context: Context,
        version: String,
        baseUrl: String,
        files: List<String>,
        onProgress: (String, Float, Long, Long) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val baseDir = File(context.filesDir, version)
            if (!baseDir.exists()) baseDir.mkdirs()

            // Pre-pass: probe every file's size upfront so the progress bar has a stable
            // denominator from the start (avoids jumpy / incorrect percentages mid-download).
            var totalBytes = 0L
            files.forEach { relativePath ->
                val targetFile = File(baseDir, relativePath)
                if (targetFile.exists()) {
                    totalBytes += targetFile.length()
                } else {
                    val len = probeFileSize("$baseUrl/$relativePath")
                    if (len > 0) totalBytes += len
                }
            }
            Log.d(TAG, "Pre-computed total size: $totalBytes bytes")

            var cumulativeBytesDownloaded = 0L

            files.forEach { relativePath ->
                val targetFile = File(baseDir, relativePath)
                if (targetFile.exists()) {
                    cumulativeBytesDownloaded += targetFile.length()
                    onProgress(
                        "Checking ${targetFile.name}",
                        (cumulativeBytesDownloaded.toFloat() / totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f),
                        cumulativeBytesDownloaded,
                        totalBytes
                    )
                    return@forEach
                }

                targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

                val url = "$baseUrl/$relativePath"
                val fileName = targetFile.name
                Log.d(TAG, "Downloading $url to ${targetFile.absolutePath}")
                onProgress(
                    "Downloading $fileName",
                    (cumulativeBytesDownloaded.toFloat() / totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f),
                    cumulativeBytesDownloaded,
                    totalBytes
                )

                var lastProgressUpdate = 0L
                downloadFileWithResume(url, targetFile) { chunkBytes ->
                    cumulativeBytesDownloaded += chunkBytes
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 100) {
                        onProgress(
                            "Downloading $fileName",
                            (cumulativeBytesDownloaded.toFloat() / totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f),
                            cumulativeBytesDownloaded,
                            totalBytes
                        )
                        lastProgressUpdate = now
                    }
                }
                onProgress(
                    "Downloading $fileName",
                    (cumulativeBytesDownloaded.toFloat() / totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f),
                    cumulativeBytesDownloaded,
                    totalBytes
                )
            }
            onProgress("Ready", 1.0f, cumulativeBytesDownloaded, totalBytes)
        }
    }
}
