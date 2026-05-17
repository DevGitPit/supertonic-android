package com.brahmadeo.supertonic.tts.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AssetManager {
    private const val TAG = "AssetManager"
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

    private val V3_FILES = listOf(
        "onnx/duration_predictor.onnx",
        "onnx/text_encoder.onnx",
        "onnx/vector_estimator.onnx",
        "onnx/vocoder.onnx",
        "onnx/tts.json",
        "onnx/unicode_indexer.json",
        // V3 voices (same filenames, upgraded model weights)
        "voice_styles/M1.json", "voice_styles/M2.json", "voice_styles/M3.json", "voice_styles/M4.json", "voice_styles/M5.json",
        "voice_styles/F1.json", "voice_styles/F2.json", "voice_styles/F3.json", "voice_styles/F4.json", "voice_styles/F5.json"
    )

    private fun filesFor(version: ModelVersion): List<String> = when (version) {
        ModelVersion.V1 -> V1_FILES
        ModelVersion.V3 -> V3_FILES
    }

    fun isReady(context: Context, version: ModelVersion): Boolean {
        val baseDir = File(context.filesDir, version.dirName)
        if (!baseDir.exists()) return false
        return filesFor(version).all { File(baseDir, it).exists() }
    }

    fun isV1Ready(context: Context): Boolean = isReady(context, ModelVersion.V1)
    fun isV3Ready(context: Context): Boolean = isReady(context, ModelVersion.V3)

    suspend fun download(context: Context, version: ModelVersion, onProgress: (String, Float, Long, Long) -> Unit) {
        downloadVersion(context, version.dirName, version.baseUrl, filesFor(version), onProgress)
    }

    private fun probeFileSize(urlString: String): Long {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
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
                    Thread.sleep(retryAfter * 1_000)
                    lastException = Exception("HTTP 429 for $urlString")
                    return@repeat
                }
                val contentRange = conn.getHeaderField("Content-Range")
                if (contentRange != null) {
                    val total = contentRange.substringAfterLast('/').trim().toLongOrNull()
                    if (total != null && total > 0) return total
                }
                return conn.contentLengthLong.takeIf { it > 0 } ?: 0L
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Probe attempt ${attempt + 1}/$MAX_RETRIES failed for $urlString: ${e.message}")
                if (attempt < MAX_RETRIES - 1) Thread.sleep(1_000L * (attempt + 1))
            } finally {
                conn.disconnect()
            }
        }
        throw lastException ?: Exception("Probe failed after $MAX_RETRIES attempts")
    }

    private fun downloadFileWithResume(
        url: String,
        targetFile: File,
        onChunk: (bytesWritten: Long) -> Unit
    ) {
        val partFile = File(targetFile.parent, "${targetFile.name}.part")
        val resumeOffset = if (partFile.exists()) partFile.length() else 0L

        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
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
                        Thread.sleep(retryAfter * 1_000)
                        throw Exception("HTTP 429 for $url")
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
                partFile.renameTo(targetFile)
                return
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt + 1}/$MAX_RETRIES failed for $url: ${e.message}")
                if (attempt < MAX_RETRIES - 1) Thread.sleep(1_000L * (attempt + 1))
            }
        }
        throw lastException ?: Exception("Download failed after $MAX_RETRIES attempts")
    }

    fun deleteVersion(context: Context, version: String) {
        val baseDir = File(context.filesDir, version)
        if (baseDir.exists()) {
            baseDir.deleteRecursively()
        }
    }

    fun deleteVersion(context: Context, version: ModelVersion) = deleteVersion(context, version.dirName)

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

            // Pre-pass: compute stable total before any downloading begins
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

                downloadFileWithResume(url, targetFile) { chunkBytes ->
                    cumulativeBytesDownloaded += chunkBytes
                    onProgress(
                        "Downloading $fileName",
                        (cumulativeBytesDownloaded.toFloat() / totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f),
                        cumulativeBytesDownloaded,
                        totalBytes
                    )
                }
            }
            onProgress("Ready", 1.0f, cumulativeBytesDownloaded, totalBytes)
        }
    }
}
