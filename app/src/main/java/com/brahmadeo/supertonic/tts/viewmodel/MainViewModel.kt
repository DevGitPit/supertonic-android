package com.brahmadeo.supertonic.tts.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brahmadeo.supertonic.tts.utils.AssetManager
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    // UI State
    var inputText = mutableStateOf("")
    var isInitializing = mutableStateOf(true)
    var isSynthesizing = mutableStateOf(false)
    var canResume = mutableStateOf(false)

    // Settings State
    var currentLang = mutableStateOf(DEFAULT_LANG)
    var selectedVoiceFile = mutableStateOf(DEFAULT_VOICE)
    var selectedVoiceFile2 = mutableStateOf(DEFAULT_VOICE_2)
    var isMixingEnabled = mutableStateOf(false)
    var mixAlpha = mutableFloatStateOf(0.5f)
    var currentSpeed = mutableFloatStateOf(DEFAULT_SPEED)
    var currentSteps = mutableIntStateOf(DEFAULT_STEPS)
    var isAdvancedNormalizationEnabled = mutableStateOf(false)
    var sibilanceMode = mutableIntStateOf(1) // 0: Off, 1: De-esser, 2: High-shelf, 3: Low-pass

    // Mini Player State
    var showMiniPlayer = mutableStateOf(false)
    var miniPlayerTitle = mutableStateOf("Now Playing")
    var miniPlayerIsPlaying = mutableStateOf(false)

    // Asset Download State
    var isDownloading = mutableStateOf(false)
    var downloadingVersion = mutableStateOf("v1")
    var downloadProgress = mutableFloatStateOf(0f)
    var downloadStatus = mutableStateOf("Checking assets...")
    var downloadedBytes = mutableLongStateOf(0L)
    var totalBytes = mutableLongStateOf(0L)
    var downloadError = mutableStateOf<String?>(null)

    // Dialog State
    var showQueueDialog = mutableStateOf(false)
    var queueDialogText = ""
    var showV2ConfirmDialog = mutableStateOf(false)
    var showV2DeleteDialog = mutableStateOf(false)
    var showV3ConfirmDialog = mutableStateOf(false)
    var showV3DeleteDialog = mutableStateOf(false)
    var pendingLangCode = ""

    // Data
    val voiceFiles = mutableStateMapOf<String, String>()

    fun startDownload(context: Context, version: String, onComplete: (String) -> Unit) {
        if (isDownloading.value) return

        isDownloading.value = true
        downloadingVersion.value = version
        downloadError.value = null
        downloadProgress.floatValue = 0f
        downloadStatus.value = "Initializing..."
        downloadedBytes.longValue = 0L
        totalBytes.longValue = 0L

        viewModelScope.launch {
            try {
                val onProgress: (String, Float, Long, Long) -> Unit = { status, progress, downloaded, total ->
                    downloadStatus.value = status
                    downloadProgress.floatValue = progress
                    downloadedBytes.longValue = downloaded
                    totalBytes.longValue = total
                }
                
                when (version) {
                    "v1" -> AssetManager.downloadV1(context, onProgress)
                    "v2" -> AssetManager.downloadV2(context, onProgress)
                    else -> AssetManager.downloadV3(context, onProgress)
                }
                
                isDownloading.value = false
                onComplete(version)
            } catch (e: Exception) {
                isDownloading.value = false // Allow UI to show error and retry
                downloadError.value = e.message ?: "Unknown error"
            }
        }
    }

    companion object {
        const val DEFAULT_VOICE = "F3.json"
        const val DEFAULT_VOICE_2 = "M2.json"
        const val DEFAULT_LANG = "en"
        const val DEFAULT_SPEED = 1.1f
        const val DEFAULT_STEPS = 5
    }
}
