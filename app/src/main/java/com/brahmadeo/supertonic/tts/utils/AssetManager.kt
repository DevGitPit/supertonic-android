package com.brahmadeo.supertonic.tts.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object AssetManager {
    private const val TAG = "AssetManager"
    private const val BASE_URL_V1 = "https://huggingface.co/Supertone/supertonic/resolve/main"
    private const val BASE_URL_V2 = "https://huggingface.co/Supertone/supertonic-2/resolve/main"
    private const val BASE_URL_V3 = "https://huggingface.co/Supertone/supertonic-3/resolve/main"
    
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

    fun isV1Ready(context: Context): Boolean = checkReady(context, "v1", V1_FILES)
    fun isV2Ready(context: Context): Boolean = checkReady(context, "v2", V2_FILES)
    fun isV3Ready(context: Context): Boolean = checkReady(context, "v3", V3_FILES)

    private fun checkReady(context: Context, version: String, files: List<String>): Boolean {
        val baseDir = File(context.filesDir, version)
        if (!baseDir.exists()) return false
        return files.all { File(baseDir, it).exists() }
    }

    suspend fun downloadV1(context: Context, onProgress: (String, Float) -> Unit) {
        downloadVersion(context, "v1", BASE_URL_V1, V1_FILES, onProgress)
    }

    suspend fun downloadV2(context: Context, onProgress: (String, Float) -> Unit) {
        downloadVersion(context, "v2", BASE_URL_V2, V2_FILES, onProgress)
    }

    suspend fun downloadV3(context: Context, onProgress: (String, Float) -> Unit) {
        downloadVersion(context, "v3", BASE_URL_V3, V3_FILES, onProgress)
    }

    fun deleteVersion(context: Context, version: String) {
        val baseDir = File(context.filesDir, version)
        if (baseDir.exists()) {
            baseDir.deleteRecursively()
        }
    }

    private suspend fun downloadVersion(
        context: Context, 
        version: String, 
        baseUrl: String, 
        files: List<String>, 
        onProgress: (String, Float) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val baseDir = File(context.filesDir, version)
            if (!baseDir.exists()) baseDir.mkdirs()

            files.forEachIndexed { index, relativePath ->
                val targetFile = File(baseDir, relativePath)
                if (targetFile.exists()) {
                    onProgress("Checking $version/$relativePath...", (index.toFloat() / files.size))
                    return@forEachIndexed
                }

                targetFile.parentFile?.let {
                    if (!it.exists()) it.mkdirs()
                }

                // Hugging Face structure for V1 is "supertonic/onnx/..."
                // But the file list assumes relative path matches URL path structure.
                // V1 URL: .../supertonic/resolve/main/onnx/duration_predictor.onnx
                // V2 URL: .../supertonic-2/resolve/main/onnx/duration_predictor.onnx
                // This matches our structure.

                val url = "$baseUrl/$relativePath"
                try {
                    onProgress("Downloading $version/$relativePath...", (index.toFloat() / files.size))
                    Log.d(TAG, "Downloading $url to ${targetFile.absolutePath}")
                    
                    URL(url).openStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download $relativePath", e)
                    targetFile.delete()
                    throw e
                }
            }
            onProgress("Ready", 1.0f)
        }
    }
}
