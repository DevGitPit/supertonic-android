package com.brahmadeo.supertonic.tts.viewmodel

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

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
    var currentSpeed = mutableFloatStateOf(1.1f)
    var currentSteps = mutableIntStateOf(5)
    var isAdvancedNormalizationEnabled = mutableStateOf(false)

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

    companion object {
        const val DEFAULT_VOICE = "F3.json"
        const val DEFAULT_VOICE_2 = "M2.json"
        const val DEFAULT_LANG = "en"
        const val DEFAULT_SPEED = 1.1f
        const val DEFAULT_STEPS = 5
    }
}