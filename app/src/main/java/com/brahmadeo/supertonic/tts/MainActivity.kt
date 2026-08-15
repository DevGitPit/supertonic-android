package com.brahmadeo.supertonic.tts

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.brahmadeo.supertonic.tts.service.IPlaybackListener
import com.brahmadeo.supertonic.tts.service.IPlaybackService
import com.brahmadeo.supertonic.tts.service.PlaybackService
import com.brahmadeo.supertonic.tts.ui.DownloadScreen
import com.brahmadeo.supertonic.tts.ui.MainScreen
import com.brahmadeo.supertonic.tts.ui.theme.SupertonicTheme
import com.brahmadeo.supertonic.tts.utils.AssetManager
import com.brahmadeo.supertonic.tts.utils.EbookManager
import com.brahmadeo.supertonic.tts.utils.EbookParser
import com.brahmadeo.supertonic.tts.utils.HistoryManager
import com.brahmadeo.supertonic.tts.utils.LexiconManager
import com.brahmadeo.supertonic.tts.utils.QueueManager
import com.brahmadeo.supertonic.tts.viewmodel.MainViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.content.edit
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.ClickableText

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var ebookParser: EbookParser

    // Data
    private val languages = mapOf(
        R.string.lang_english to "en",
        R.string.lang_french to "fr",
        R.string.lang_portuguese to "pt",
        R.string.lang_spanish to "es",
        R.string.lang_korean to "ko",
        R.string.lang_japanese to "ja",
        R.string.lang_arabic to "ar",
        R.string.lang_bulgarian to "bg",
        R.string.lang_czech to "cs",
        R.string.lang_danish to "da",
        R.string.lang_german to "de",
        R.string.lang_greek to "el",
        R.string.lang_estonian to "et",
        R.string.lang_finnish to "fi",
        R.string.lang_hindi to "hi",
        R.string.lang_croatian to "hr",
        R.string.lang_hungarian to "hu",
        R.string.lang_indonesian to "id",
        R.string.lang_italian to "it",
        R.string.lang_lithuanian to "lt",
        R.string.lang_latvian to "lv",
        R.string.lang_dutch to "nl",
        R.string.lang_polish to "pl",
        R.string.lang_romanian to "ro",
        R.string.lang_russian to "ru",
        R.string.lang_slovak to "sk",
        R.string.lang_slovenian to "sl",
        R.string.lang_swedish to "sv",
        R.string.lang_turkish to "tr",
        R.string.lang_ukrainian to "uk",
        R.string.lang_vietnamese to "vi"
    )

    private var currentModelVersion = "v1" // "v1", "v2", or "v3"

    // Service
    private var playbackService: IPlaybackService? = null
    private var isBound = false

    private val playbackListener = object : IPlaybackListener.Stub() {
        override fun onStateChanged(isPlaying: Boolean, hasContent: Boolean, isSynthesizing: Boolean) {
            runOnUiThread {
                viewModel.miniPlayerIsPlaying.value = isPlaying
                viewModel.isSynthesizing.value = isSynthesizing
                if (hasContent || isSynthesizing) {
                    viewModel.showMiniPlayer.value = true
                    val lastText = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).getString("last_text", "")
                    if (!lastText.isNullOrEmpty()) {
                        viewModel.miniPlayerTitle.value = lastText
                    }
                } else {
                    viewModel.showMiniPlayer.value = false
                }
            }
        }
        override fun onProgress(current: Int, total: Int) { }
        override fun onPlaybackStopped() {
            runOnUiThread {
                viewModel.showMiniPlayer.value = false
                viewModel.miniPlayerIsPlaying.value = false
            }
        }
        override fun onExportComplete(success: Boolean, path: String) { }
        override fun onChapterChanged(newText: String, chapterHref: String?, pageIndex: Int) {
            runOnUiThread {
                if (newText.isNotEmpty()) {
                    viewModel.miniPlayerTitle.value = newText
                }
            }
        }
        override fun onTransitioningChanged(isTransitioning: Boolean) { }
        override fun onSleepTimerUpdated(secondsRemaining: Int) { }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            playbackService = IPlaybackService.Stub.asInterface(service)
            isBound = true
            try {
                playbackService?.setListener(playbackListener)
                checkResumeState()
            } catch (e: Exception) { e.printStackTrace() }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            playbackService = null
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val ebookLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val localPath = EbookManager.importBook(this, it)
            if (localPath != null) {
                val intent = Intent(this, EbookOutlineActivity::class.java).apply {
                    putExtra(EbookOutlineActivity.EXTRA_URI, localPath)
                }
                ebookOutlineLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Failed to import book", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val ebookOutlineLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "ebookOutlineLauncher result: ${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringExtra(EbookOutlineActivity.EXTRA_TEXT)
            Log.d("MainActivity", "Received text length: ${text?.length ?: 0}")
            if (!text.isNullOrEmpty()) {
                // Reset state before loading new ebook text
                viewModel.inputText.value = ""
                val stopIntent = Intent(this, PlaybackService::class.java).apply { action = "STOP_PLAYBACK" }
                startService(stopIntent)
                
                viewModel.inputText.value = prepareTextForTts(text, viewModel.currentLang.value)
                Toast.makeText(this, "Chapter loaded", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("MainActivity", "Received empty or null text from ebook activity")
            }
        }
    }

    private fun prepareTextForTts(text: String?, lang: String): String {
        if (text.isNullOrEmpty()) return ""
        val trimmed = text.trim()
        
        // Append " ." to prevent diffusion model from cutting off abruptly at the end
        // RESTRICTED for Korean
        if (lang.lowercase().startsWith("ko")) {
            return trimmed
        }
        
        return if (trimmed.endsWith(" .")) trimmed else "$trimmed ."
    }

    private val historyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedText = result.data?.getStringExtra("selected_text")
            if (!selectedText.isNullOrEmpty()) {
                viewModel.inputText.value = selectedText
            }
        }
    }

    private val voiceStylePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importVoiceStyle(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(this)

        loadPreferences()
        checkNotificationPermission()
        migrateSavedAudioFiles()

        val bindIntent = Intent(this, PlaybackService::class.java)
        bindService(bindIntent, connection, BIND_AUTO_CREATE)

        ebookParser = EbookParser(this)
        LexiconManager.load(this)
        QueueManager.initialize(this)

        // Initial setup based on saved language
        val savedLang = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).getString("selected_lang", MainViewModel.DEFAULT_LANG) ?: MainViewModel.DEFAULT_LANG
        currentModelVersion = resolveModelVersion(savedLang)

        // On FIRST LAUNCH, we check/download the required version.
        // If English (default), we ensure V1 is ready.
        // If they managed to switch language before assets were ready (unlikely), we check that version.
        if (currentModelVersion == "v1") {
            if (!AssetManager.isV1Ready(this)) {
                startDownload("v1")
            } else {
                initializeEngine("v1")
            }
        } else if (currentModelVersion == "v2") {
            if (!AssetManager.isV2Ready(this)) {
                startDownload("v2")
            } else {
                initializeEngine("v2")
            }
        } else {
            if (!AssetManager.isV3Ready(this)) {
                startDownload("v3")
            } else {
                initializeEngine("v3")
            }
        }

        handleIntent(intent)

        setContent {
            SupertonicTheme(voiceFile = viewModel.selectedVoiceFile.value) {
                if (viewModel.isDownloading.value) {
                    DownloadScreen(
                        status = viewModel.downloadStatus.value,
                        progress = viewModel.downloadProgress.floatValue,
                        version = viewModel.downloadingVersion.value,
                        downloadedBytes = viewModel.downloadedBytes.longValue,
                        totalBytes = viewModel.totalBytes.longValue,
                        error = viewModel.downloadError.value,
                        onRetry = { startDownload(viewModel.downloadingVersion.value) }
                    )
                } else {
                    if (viewModel.showQueueDialog.value) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { viewModel.showQueueDialog.value = false },
                            title = { Text(getString(R.string.playback_active_title)) },
                            text = { Text(getString(R.string.playback_active_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    addToQueue(viewModel.queueDialogText)
                                    viewModel.showQueueDialog.value = false
                                }) { Text(getString(R.string.add_to_queue)) }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    playNow(viewModel.queueDialogText)
                                    viewModel.showQueueDialog.value = false
                                }) { Text(getString(R.string.play_now)) }
                            }
                        )
                    }

                    if (viewModel.showV2ConfirmDialog.value) {
                        androidx.compose.material3.AlertDialog(
                             onDismissRequest = { 
                                 viewModel.showV2ConfirmDialog.value = false
                             },
                            title = { Text(getString(R.string.v2_download_title)) },
                            text = { Text(getString(R.string.v2_download_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    val lang = viewModel.pendingLangCode
                                    viewModel.currentLang.value = lang
                                    saveStringPref("selected_lang", lang)
                                    viewModel.showV2ConfirmDialog.value = false
                                    switchModel("v2")
                                    val resetIntent = Intent(this@MainActivity, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                                    startService(resetIntent)
                                }) { Text(getString(R.string.v2_download_button)) }
                            },
                            dismissButton = {
                                 TextButton(onClick = {
                                     viewModel.showV2ConfirmDialog.value = false
                                 }) { Text(getString(R.string.cancel)) }
                            }
                        )
                    }

                    if (viewModel.showV2DeleteDialog.value) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { viewModel.showV2DeleteDialog.value = false },
                            title = { Text(getString(R.string.v2_delete_title)) },
                            text = { Text(getString(R.string.v2_delete_message)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        AssetManager.deleteVersion(this@MainActivity, "v2")
                                        viewModel.showV2DeleteDialog.value = false
                                        // Ensure we are on English/V1
                                        viewModel.currentLang.value = "en"
                                        saveStringPref("selected_lang", "en")
                                        switchModel("v1")
                                        val resetIntent = Intent(this@MainActivity, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                                        startService(resetIntent)
                                        Toast.makeText(this@MainActivity, getString(R.string.v2_deleted_msg), Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text(getString(R.string.delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showV2DeleteDialog.value = false }) { Text(getString(R.string.cancel)) }
                            }
                        )
                    }

                    if (viewModel.showV3ConfirmDialog.value) {
                        androidx.compose.material3.AlertDialog(
                             onDismissRequest = { 
                                 viewModel.showV3ConfirmDialog.value = false
                             },
                            title = { Text(getString(R.string.v3_download_title)) },
                            text = { Text(getString(R.string.v3_download_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    val lang = viewModel.pendingLangCode
                                    viewModel.currentLang.value = lang
                                    saveStringPref("selected_lang", lang)
                                    viewModel.showV3ConfirmDialog.value = false
                                    switchModel("v3")
                                    val resetIntent = Intent(this@MainActivity, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                                    startService(resetIntent)
                                }) { Text(getString(R.string.v3_download_button)) }
                            },
                            dismissButton = {
                                 TextButton(onClick = {
                                     viewModel.showV3ConfirmDialog.value = false
                                 }) { Text(getString(R.string.cancel)) }
                            }
                        )
                    }

                    if (viewModel.showV3DeleteDialog.value) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { viewModel.showV3DeleteDialog.value = false },
                            title = { Text(getString(R.string.v3_delete_title)) },
                            text = { Text(getString(R.string.v3_delete_message)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        AssetManager.deleteVersion(this@MainActivity, "v3")
                                        viewModel.showV3DeleteDialog.value = false
                                        // Ensure we are on English/V1
                                        viewModel.currentLang.value = "en"
                                        saveStringPref("selected_lang", "en")
                                        switchModel("v1")
                                        val resetIntent = Intent(this@MainActivity, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                                        startService(resetIntent)
                                        Toast.makeText(this@MainActivity, getString(R.string.v3_deleted_msg), Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text(getString(R.string.delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showV3DeleteDialog.value = false }) { Text(getString(R.string.cancel)) }
                            }
                        )
                    }

                    if (viewModel.showDeleteVoiceConfirmDialog.value) {
                        val voiceName = viewModel.selectedVoiceFile.value.removeSuffix(".json")
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { viewModel.showDeleteVoiceConfirmDialog.value = false },
                            title = { Text(getString(R.string.delete_voice_title)) },
                            text = { Text(getString(R.string.delete_voice_message, voiceName)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.showDeleteVoiceConfirmDialog.value = false
                                        deleteVoiceStyle()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text(getString(R.string.delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showDeleteVoiceConfirmDialog.value = false }) { Text(getString(R.string.cancel)) }
                            }
                        )
                    }

                    if (viewModel.showCustomVoicesDialog.value) {
                        val defaultVoiceFiles = remember {
                            listOf(
                                "M1.json", "M2.json", "M3.json", "M4.json", "M5.json",
                                "F1.json", "F2.json", "F3.json", "F4.json", "F5.json"
                            )
                        }
                        val customVoices = remember(viewModel.voiceFiles.size, viewModel.selectedVoiceFile.value) {
                            viewModel.voiceFiles.entries.filter { it.value !in defaultVoiceFiles }
                        }
                        
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { viewModel.showCustomVoicesDialog.value = false },
                            title = { Text(getString(R.string.custom_voices_title)) },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = getString(R.string.custom_voices_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    val uriHandler = LocalUriHandler.current
                                    val annotatedString = buildAnnotatedString {
                                        append("Tip: Voice styles are .json files containing speaker characteristics. You can extract them from your own voice recordings using the external python project: ")
                                        pushStringAnnotation(tag = "URL", annotation = "https://github.com/saurabhv749/supertonic3-voice-clone")
                                        withStyle(style = androidx.compose.ui.text.SpanStyle(
                                            color = MaterialTheme.colorScheme.primary,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                        )) {
                                            append("supertonic3-voice-clone")
                                        }
                                        pop()
                                        append(" on a computer.")
                                    }

                                    ClickableText(
                                        text = annotatedString,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        onClick = { offset ->
                                            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                                .firstOrNull()?.let { annotation ->
                                                    try {
                                                        uriHandler.openUri(annotation.item)
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                        }
                                    )
                                    
                                    androidx.compose.material3.Button(
                                        onClick = {
                                            voiceStylePickerLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(getString(R.string.import_voice_style_button))
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (customVoices.isEmpty()) {
                                        androidx.compose.material3.Text(
                                            text = "No custom voices imported yet.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(vertical = 16.dp)
                                        )
                                    } else {
                                        val scrollState = androidx.compose.foundation.rememberScrollState()
                                        Column(
                                            modifier = Modifier
                                                .heightIn(max = 240.dp)
                                                .verticalScroll(scrollState),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            for (entry in customVoices) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = entry.key,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            // We set the selected voice to this one so the deletion helper knows which file to delete
                                                            viewModel.selectedVoiceFile.value = entry.value
                                                            viewModel.showDeleteVoiceConfirmDialog.value = true
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Voice",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { viewModel.showCustomVoicesDialog.value = false }) {
                                    Text(getString(R.string.close_button))
                                }
                            }
                        )
                    }

                    // Get localized placeholder and languages
                    val placeholder = remember(viewModel.currentLang.value) {
                        getLocalizedResource(this@MainActivity, viewModel.currentLang.value, R.string.default_input_text)
                    }
                    val localizedLanguages = remember(viewModel.currentLang.value) {
                        languages.mapKeys { getLocalizedResource(this@MainActivity, viewModel.currentLang.value, it.key) }
                    }

                    MainScreen(
                        inputText = viewModel.inputText.value,
                        onInputTextChange = { 
                            viewModel.inputText.value = it
                            saveStringPref("last_text", it)
                        },
                        placeholderText = placeholder,
                        isInitializing = viewModel.isInitializing.value,
                        isSynthesizing = viewModel.isSynthesizing.value,
                        onSynthesizeClick = {
                            val textToPlay = viewModel.inputText.value.ifEmpty { placeholder }
                            generateAndPlay(textToPlay)
                        },

                        languages = localizedLanguages,
                        currentLangCode = viewModel.currentLang.value,
                        onLangChange = { lang ->
                            val targetVersion = resolveModelVersion(lang)
                            if (targetVersion == "v1") {
                                viewModel.currentLang.value = lang
                                saveStringPref("selected_lang", lang)
                                switchModel("v1")
                                val resetIntent = Intent(this, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                                startService(resetIntent)
                            } else if (targetVersion == "v2") {
                                if (AssetManager.isV2Ready(this@MainActivity)) {
                                    viewModel.currentLang.value = lang
                                    saveStringPref("selected_lang", lang)
                                    switchModel("v2")
                                    val resetIntent = Intent(this, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                                    startService(resetIntent)
                                } else {
                                    viewModel.pendingLangCode = lang
                                    viewModel.showV2ConfirmDialog.value = true
                                }
                            } else { // v3
                                if (AssetManager.isV3Ready(this@MainActivity)) {
                                    viewModel.currentLang.value = lang
                                    saveStringPref("selected_lang", lang)
                                    switchModel("v3")
                                    val resetIntent = Intent(this, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                                    startService(resetIntent)
                                } else {
                                    viewModel.pendingLangCode = lang
                                    viewModel.showV3ConfirmDialog.value = true
                                }
                            }
                        },

                        voices = viewModel.voiceFiles,
                        selectedVoiceFile = viewModel.selectedVoiceFile.value,
                        onVoiceChange = {
                            if (viewModel.selectedVoiceFile.value != it) {
                                viewModel.selectedVoiceFile.value = it
                                saveStringPref("selected_voice", it)
                                val resetIntent = Intent(this, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                                startService(resetIntent)
                            }
                        },
                        preferredModelEngine = viewModel.preferredModelEngine.value,
                        onPreferredModelEngineChange = { newEngine ->
                            onPreferredModelEngineChanged(newEngine)
                        },
                        onCustomVoicesClick = {
                            viewModel.showCustomVoicesDialog.value = true
                        },

                        isMixingEnabled = viewModel.isMixingEnabled.value,
                        onMixingEnabledChange = { 
                            viewModel.isMixingEnabled.value = it
                            getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit {
                                putBoolean(
                                    "is_mixing_enabled",
                                    it
                                )
                            }
                        },
                        selectedVoiceFile2 = viewModel.selectedVoiceFile2.value,
                        onVoice2Change = {
                            viewModel.selectedVoiceFile2.value = it
                            saveStringPref("selected_voice_2", it)
                        },
                        mixAlpha = viewModel.mixAlpha.floatValue,
                        onMixAlphaChange = { 
                            viewModel.mixAlpha.floatValue = it
                            getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit {
                                putFloat(
                                    "mix_alpha",
                                    it
                                )
                            }
                        },

                        speed = viewModel.currentSpeed.floatValue,
                        onSpeedChange = {
                            viewModel.currentSpeed.floatValue = it
                            getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit {
                                putFloat("speed", it)
                            }
                        },
                        steps = viewModel.currentSteps.intValue,
                        onStepsChange = {
                            viewModel.currentSteps.intValue = it
                            getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit {
                                putInt(
                                    "diffusion_steps",
                                    it
                                )
                            }
                        },

                        isAdvancedNormalizationEnabled = viewModel.isAdvancedNormalizationEnabled.value,
                        onAdvancedNormalizationEnabledChange = {
                            viewModel.isAdvancedNormalizationEnabled.value = it
                            getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit {
                                putBoolean(
                                    "is_advanced_normalization",
                                    it
                                )
                            }
                        },

                        sibilanceMode = viewModel.sibilanceMode.intValue,
                        onSibilanceModeChange = { mode ->
                            viewModel.sibilanceMode.intValue = mode
                            SupertonicTTS.sibilanceMode = mode
                            getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit {
                                putInt("sibilance_reduction_mode", mode)
                            }
                        },

                        onResetClick = {
                            viewModel.inputText.value = ""
                            val stopIntent = Intent(this, PlaybackService::class.java).apply { action = "STOP_PLAYBACK" }
                            startService(stopIntent)
                        },
                        onSavedAudioClick = { startActivity(Intent(this, SavedAudioActivity::class.java)) },
                        onHistoryClick = { historyLauncher.launch(Intent(this, HistoryActivity::class.java)) },
                        onQueueClick = { startActivity(Intent(this, QueueActivity::class.java)) },
                        onLexiconClick = { startActivity(Intent(this, LexiconActivity::class.java)) },
                        onDeleteV2Click = { viewModel.showV2DeleteDialog.value = true },
                        onDeleteV3Click = { viewModel.showV3DeleteDialog.value = true },
                        onOpenEbookClick = { 
                            try {
                                if (EbookManager.getRecentBooks(this).isEmpty()) {
                                    ebookLauncher.launch(arrayOf("application/epub+zip", "application/pdf"))
                                } else {
                                    val intent = Intent(this, EbookLibraryActivity::class.java)
                                    ebookOutlineLauncher.launch(intent)
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to open ebook library", e)
                                ebookLauncher.launch(arrayOf("application/epub+zip", "application/pdf"))
                            }
                        },
                        isV2Ready = AssetManager.isV2Ready(this),
                        isV3Ready = AssetManager.isV3Ready(this),

                        canResume = viewModel.canResume.value,
                        onResumeClick = {
                            val intent = Intent(this, PlaybackActivity::class.java)
                            intent.putExtra("is_resume", true)
                            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            startActivity(intent)
                        },

                        showMiniPlayer = viewModel.showMiniPlayer.value,
                        miniPlayerTitle = viewModel.miniPlayerTitle.value,
                        miniPlayerIsPlaying = viewModel.miniPlayerIsPlaying.value,
                        onMiniPlayerClick = {
                            val intent = Intent(this, PlaybackActivity::class.java)
                            intent.putExtra("is_resume", true)
                            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            startActivity(intent)
                        },
                        onMiniPlayerPlayPauseClick = {
                             if (playbackService?.isServiceActive == true) {
                                try {
                                    if (viewModel.miniPlayerIsPlaying.value) playbackService?.pause() else playbackService?.play()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkResumeState()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        viewModel.inputText.value = "" // Do not load user text from cleartext storage
        viewModel.currentLang.value = prefs.getString("selected_lang", MainViewModel.DEFAULT_LANG) ?: MainViewModel.DEFAULT_LANG
        viewModel.selectedVoiceFile.value = prefs.getString("selected_voice", MainViewModel.DEFAULT_VOICE) ?: MainViewModel.DEFAULT_VOICE
        viewModel.selectedVoiceFile2.value = prefs.getString("selected_voice_2", MainViewModel.DEFAULT_VOICE_2) ?: MainViewModel.DEFAULT_VOICE_2
        viewModel.isMixingEnabled.value = prefs.getBoolean("is_mixing_enabled", false)
        viewModel.mixAlpha.floatValue = prefs.getFloat("mix_alpha", 0.5f)
        viewModel.currentSpeed.floatValue = prefs.getFloat("speed", MainViewModel.DEFAULT_SPEED)
        viewModel.currentSteps.intValue = prefs.getInt("diffusion_steps", MainViewModel.DEFAULT_STEPS)
        viewModel.isAdvancedNormalizationEnabled.value = prefs.getBoolean("is_advanced_normalization", false)
        viewModel.preferredModelEngine.value = prefs.getString("preferred_model_engine", "default") ?: "default"
        val sMode = prefs.getInt("sibilance_reduction_mode", 1)
        viewModel.sibilanceMode.intValue = sMode
        SupertonicTTS.sibilanceMode = sMode
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun getLocalizedResource(context: Context, lang: String, resId: Int): String {
        val locale = java.util.Locale.forLanguageTag(lang)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        val localizedContext = context.createConfigurationContext(config)
        return localizedContext.resources.getString(resId)
    }

    private fun saveStringPref(key: String, value: String) {
        getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit(commit = true) {
            putString(key, value)
        }
    }

    private fun startDownload(version: String) {
        viewModel.startDownload(this, version) { completedVersion ->
            initializeEngine(completedVersion)
        }
    }

    private fun initializeEngine(version: String) {
        val isReady = AssetManager.isVersionReady(this, version)
        if (!isReady) {
            Log.w("MainActivity", "Assets not ready for $version, triggering re-download")
            startDownload(version)
            return
        }

        val modelPath = File(filesDir, "$version/onnx").absolutePath
        val libPath = applicationInfo.nativeLibraryDir + "/libonnxruntime.so"

        if (SupertonicTTS.isInitialized(modelPath)) {
            Log.i("MainActivity", "Engine already initialized for $version, skipping reload")
            viewModel.isInitializing.value = false
            currentModelVersion = version
            setupVoicesMap(version, viewModel.currentLang.value)
            return
        }

        currentModelVersion = version
        viewModel.isInitializing.value = true
        
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                setupVoicesMap(version, viewModel.currentLang.value)
            }

            val success = withContext(Dispatchers.IO) {
                SupertonicTTS.initialize(modelPath, libPath)
            }

            if (success) {
                withContext(Dispatchers.Main) {
                    viewModel.isInitializing.value = false
                }
            }
        }
    }

    private fun switchModel(version: String) {
        // Even if the version is the same, we might need to re-initialize 
        // to update the voices map for a new language.
        
        // Lazy Check
        val isReady = when (version) {
            "v1" -> AssetManager.isV1Ready(this)
            "v2" -> AssetManager.isV2Ready(this)
            else -> AssetManager.isV3Ready(this)
        }
        
        if (!isReady) {
            // Trigger Download
            startDownload(version)
        } else {
            // Instant Switch
            initializeEngine(version)
            Toast.makeText(this, "Switched to model $version", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupVoicesMap(version: String, lang: String) {
        viewModel.voiceFiles.clear()
        val voiceResources = mapOf(
            "M1.json" to R.string.voice_m1,
            "M2.json" to R.string.voice_m2,
            "M3.json" to R.string.voice_m3,
            "M4.json" to R.string.voice_m4,
            "M5.json" to R.string.voice_m5,
            "F1.json" to R.string.voice_f1,
            "F2.json" to R.string.voice_f2,
            "F3.json" to R.string.voice_f3,
            "F4.json" to R.string.voice_f4,
            "F5.json" to R.string.voice_f5
        )

        voiceResources.forEach { (filename, resId) ->
            viewModel.voiceFiles[getLocalizedResource(this, lang, resId)] = filename
        }

        // Check dynamic dir for default listing
        val voiceDir = File(filesDir, "$version/voice_styles")
        if (voiceDir.exists()) {
            val files = voiceDir.listFiles { _, name -> name.endsWith(".json") }
            files?.forEach { file ->
                if (!voiceResources.containsKey(file.name)) {
                    val friendlyName = file.name.removeSuffix(".json")
                    viewModel.voiceFiles[friendlyName] = file.name
                }
            }
        }
    }

    private fun generateAndPlay(text: String) {
        val isReady = AssetManager.isVersionReady(this, currentModelVersion)
        if (!isReady) {
            startDownload(currentModelVersion)
            return
        }

        if (viewModel.isInitializing.value) return

        var stylePath = File(filesDir, "$currentModelVersion/voice_styles/${viewModel.selectedVoiceFile.value}").absolutePath
        if (!File(stylePath).exists()) {
             // This case should be covered by isReady, but as a fallback:
             startDownload(currentModelVersion)
             return
        }

        if (viewModel.isMixingEnabled.value) {
            val stylePath2 = File(filesDir, "$currentModelVersion/voice_styles/${viewModel.selectedVoiceFile2.value}").absolutePath
            if (File(stylePath2).exists()) {
                stylePath = "$stylePath;$stylePath2;${viewModel.mixAlpha.floatValue}"
            }
        }
        
        val v1Name = viewModel.voiceFiles.entries.find { it.value == viewModel.selectedVoiceFile.value }?.key ?: "Voice 1"
        val v2Name = viewModel.voiceFiles.entries.find { it.value == viewModel.selectedVoiceFile2.value }?.key ?: "Voice 2"
        val voiceName = if (viewModel.isMixingEnabled.value) "Mixed: $v1Name + $v2Name" else v1Name

        HistoryManager.saveItem(this, text, voiceName)

        try {
            if (playbackService?.isServiceActive == true) {
                viewModel.queueDialogText = text
                viewModel.showQueueDialog.value = true
            } else {
                launchPlaybackActivity(text, stylePath)
            }
        } catch (_: Exception) {
            launchPlaybackActivity(text, stylePath)
        }
    }

    private fun addToQueue(text: String) {
        val isReady = AssetManager.isVersionReady(this, currentModelVersion)
        if (!isReady) {
            startDownload(currentModelVersion)
            return
        }

        if (viewModel.isInitializing.value) return

        var stylePath = File(filesDir, "$currentModelVersion/voice_styles/${viewModel.selectedVoiceFile.value}").absolutePath
        if (viewModel.isMixingEnabled.value) {
            val stylePath2 = File(filesDir, "$currentModelVersion/voice_styles/${viewModel.selectedVoiceFile2.value}").absolutePath
            stylePath = "$stylePath;$stylePath2;${viewModel.mixAlpha.floatValue}"
        }

        try {
            playbackService?.addToQueue(
                text,
                viewModel.currentLang.value,
                stylePath,
                viewModel.currentSpeed.floatValue,
                viewModel.currentSteps.intValue,
                0
            )
            Toast.makeText(this, getString(R.string.added_to_queue), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playNow(text: String) {
        val isReady = AssetManager.isVersionReady(this, currentModelVersion)
        if (!isReady) {
            startDownload(currentModelVersion)
            return
        }

        if (viewModel.isInitializing.value) return

        var stylePath = File(filesDir, "$currentModelVersion/voice_styles/${viewModel.selectedVoiceFile.value}").absolutePath
        if (viewModel.isMixingEnabled.value) {
            val stylePath2 = File(filesDir, "$currentModelVersion/voice_styles/${viewModel.selectedVoiceFile2.value}").absolutePath
            stylePath = "$stylePath;$stylePath2;${viewModel.mixAlpha.floatValue}"
        }
        launchPlaybackActivity(text, stylePath)
    }

    private fun launchPlaybackActivity(text: String, stylePath: String) {
        val intent = Intent(this, PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_TEXT, text)
            putExtra(PlaybackActivity.EXTRA_VOICE_PATH, stylePath)
            putExtra(PlaybackActivity.EXTRA_SPEED, viewModel.currentSpeed.floatValue)
            putExtra(PlaybackActivity.EXTRA_STEPS, viewModel.currentSteps.intValue)
            putExtra(PlaybackActivity.EXTRA_LANG, viewModel.currentLang.value)
        }
        startActivity(intent)
    }

    private fun importVoiceStyle(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                
                // Simple validation
                val jsonObject = org.json.JSONObject(jsonString)
                val hasTtl = jsonObject.has("style_ttl")
                val hasDp = jsonObject.has("style_dp")
                
                if (!hasTtl || !hasDp) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.import_voice_style_invalid), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                // Resolve name
                var displayName: String? = null
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                val fileName = if (!displayName.isNullOrBlank()) {
                    val name = displayName!!
                    if (!name.endsWith(".json", ignoreCase = true)) {
                        "$name.json"
                    } else {
                        name
                    }
                } else {
                    "custom_voice_${System.currentTimeMillis()}.json"
                }
                
                // Sanitize file name (replace illegal chars with underscore)
                val sanitizedFileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                
                // Save to active model version
                val voiceDir = File(filesDir, "$currentModelVersion/voice_styles")
                if (!voiceDir.exists()) {
                    voiceDir.mkdirs()
                }
                
                val destFile = File(voiceDir, sanitizedFileName)
                destFile.writeText(jsonString)
                
                withContext(Dispatchers.Main) {
                    // Refresh voice map
                    setupVoicesMap(currentModelVersion, viewModel.currentLang.value)
                    
                    // Select new voice
                    viewModel.selectedVoiceFile.value = sanitizedFileName
                    saveStringPref("selected_voice", sanitizedFileName)
                    
                    // Reset playback service engine to reload style
                    val resetIntent = Intent(this@MainActivity, PlaybackService::class.java).apply {
                        action = "RESET_ENGINE"
                    }
                    startService(resetIntent)
                    
                    val friendlyName = sanitizedFileName.removeSuffix(".json")
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.import_voice_style_success, friendlyName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to import voice style", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deleteVoiceStyle() {
        val voiceFile = viewModel.selectedVoiceFile.value
        val isDefault = voiceFile in listOf(
            "M1.json", "M2.json", "M3.json", "M4.json", "M5.json",
            "F1.json", "F2.json", "F3.json", "F4.json", "F5.json"
        )
        if (isDefault) return

        try {
            val file = File(filesDir, "$currentModelVersion/voice_styles/$voiceFile")
            if (file.exists()) {
                file.delete()
            }
            
            val friendlyName = voiceFile.removeSuffix(".json")
            
            // Select default voice style
            val defaultVoice = MainViewModel.DEFAULT_VOICE
            viewModel.selectedVoiceFile.value = defaultVoice
            saveStringPref("selected_voice", defaultVoice)
            
            // Refresh voices list
            setupVoicesMap(currentModelVersion, viewModel.currentLang.value)
            
            // Reset playback service engine to reload style
            val resetIntent = Intent(this, PlaybackService::class.java).apply {
                action = "RESET_ENGINE"
            }
            startService(resetIntent)
            
            Toast.makeText(
                this,
                getString(R.string.delete_voice_success, friendlyName),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to delete voice style", e)
            Toast.makeText(this, "Error deleting voice style: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun resolveModelVersion(lang: String): String {
        val engine = viewModel.preferredModelEngine.value
        return if (engine == "default" || engine.isEmpty()) {
            AssetManager.getModelVersionForLanguage(lang)
        } else {
            engine
        }
    }

    private fun onPreferredModelEngineChanged(newEngine: String) {
        viewModel.preferredModelEngine.value = newEngine
        saveStringPref("preferred_model_engine", newEngine)
        
        // Resolve new active model version
        val targetVersion = resolveModelVersion(viewModel.currentLang.value)
        
        // Trigger model switch and/or download check
        if (targetVersion == "v1") {
            if (AssetManager.isV1Ready(this)) {
                switchModel("v1")
                val resetIntent = Intent(this, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                startService(resetIntent)
            } else {
                startDownload("v1")
            }
        } else if (targetVersion == "v2") {
            if (AssetManager.isV2Ready(this)) {
                switchModel("v2")
                val resetIntent = Intent(this, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                startService(resetIntent)
            } else {
                viewModel.pendingLangCode = viewModel.currentLang.value
                viewModel.showV2ConfirmDialog.value = true
            }
        } else if (targetVersion == "v3") {
            if (AssetManager.isV3Ready(this)) {
                switchModel("v3")
                val resetIntent = Intent(this, PlaybackService::class.java).apply { action = "RESET_ENGINE" }
                startService(resetIntent)
            } else {
                viewModel.pendingLangCode = viewModel.currentLang.value
                viewModel.showV3ConfirmDialog.value = true
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val pattern = Regex("https?://\\S+")
        val match = pattern.find(text)
        return match?.value
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("intent_handled", false)) return
        
        val action = intent.action
        val type = intent.type
        
        // 1. Check if the intent is a PDF or EPUB file import
        val isViewFile = action == Intent.ACTION_VIEW && intent.data != null
        val isSendFile = action == Intent.ACTION_SEND && IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) != null
        
        val fileUri = if (isViewFile) intent.data else if (isSendFile) IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else null
        
        if (fileUri != null) {
            val contentResolver = contentResolver
            val mimeType = contentResolver.getType(fileUri)
            val uriStr = fileUri.toString().lowercase()
            
            var fileName: String? = null
            try {
                contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIdx)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val isEbook = mimeType == "application/epub+zip" || 
                          mimeType == "application/pdf" ||
                          fileName?.lowercase()?.endsWith(".epub") == true ||
                          fileName?.lowercase()?.endsWith(".pdf") == true ||
                          uriStr.endsWith(".epub") || uriStr.contains(".epub?") ||
                          uriStr.endsWith(".pdf") || uriStr.contains(".pdf?")
                          
            if (isEbook) {
                intent.putExtra("intent_handled", true)
                val localPath = EbookManager.importBook(this, fileUri)
                if (localPath != null) {
                    val outlineIntent = Intent(this, EbookOutlineActivity::class.java).apply {
                        putExtra(EbookOutlineActivity.EXTRA_URI, localPath)
                    }
                    ebookOutlineLauncher.launch(outlineIntent)
                } else {
                    Toast.makeText(this, "Failed to import book", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
        
        // 2. Fall back to existing text sharing logic
        if (action == Intent.ACTION_SEND && type == "text/plain") {
            intent.putExtra("intent_handled", true)
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val url = extractUrl(sharedText)
            if (url != null) {
                Toast.makeText(this, "Fetching webpage text...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    try {
                        val webText = ebookParser.extractWebpageText(url)
                        if (webText.isNotBlank()) {
                            viewModel.inputText.value = prepareTextForTts(webText, viewModel.currentLang.value)
                            Toast.makeText(this@MainActivity, "Webpage text loaded", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.inputText.value = prepareTextForTts(sharedText, viewModel.currentLang.value)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        viewModel.inputText.value = prepareTextForTts(sharedText, viewModel.currentLang.value)
                        Toast.makeText(this@MainActivity, "Failed to fetch webpage text", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (sharedText.isNotBlank()) {
                viewModel.inputText.value = prepareTextForTts(sharedText, viewModel.currentLang.value)
            }
        } else {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.data?.getQueryParameter("text")
            if (!text.isNullOrEmpty()) {
                intent.putExtra("intent_handled", true)
                val url = extractUrl(text)
                if (url != null) {
                    Toast.makeText(this, "Fetching webpage text...", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        try {
                            val webText = ebookParser.extractWebpageText(url)
                            if (webText.isNotBlank()) {
                                viewModel.inputText.value = prepareTextForTts(webText, viewModel.currentLang.value)
                                Toast.makeText(this@MainActivity, "Webpage text loaded", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.inputText.value = prepareTextForTts(text, viewModel.currentLang.value)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            viewModel.inputText.value = prepareTextForTts(text, viewModel.currentLang.value)
                        }
                    }
                } else {
                    viewModel.inputText.value = prepareTextForTts(text, viewModel.currentLang.value)
                }
            }
        }
    }

    private fun checkResumeState() {
        if (viewModel.isDownloading.value) return

        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val lastText = prefs.getString("last_text", null)
        val isPlayingPref = prefs.getBoolean("is_playing", false)

        if (lastText.isNullOrEmpty()) {
            viewModel.canResume.value = false
            return
        }

        // If service is already active, we just sync the mini player state
        try {
            if (playbackService != null && playbackService?.isServiceActive == true) {
                runOnUiThread {
                    viewModel.showMiniPlayer.value = true
                    viewModel.miniPlayerTitle.value = lastText
                    viewModel.canResume.value = false // Mini player handles it
                }
                return
            }
        } catch (_: Exception) { }

        viewModel.canResume.value = isPlayingPref
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try {
                playbackService?.removeListener(playbackListener)
            } catch (_: Exception) { }
            unbindService(connection)
            isBound = false
        }
    }

    private fun migrateSavedAudioFiles() {
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("audio_migration_done", false)) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val oldMusicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val oldAppDir = File(oldMusicDir, "Supertonic Audio")
                val newAppDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(filesDir, "Music")
                
                if (!oldAppDir.exists()) {
                    prefs.edit { putBoolean("audio_migration_done", true) }
                    return@launch
                }

                if (oldAppDir.isDirectory) {
                    if (!newAppDir.exists()) {
                        newAppDir.mkdirs()
                    }
                    val files = oldAppDir.listFiles()
                    var migrationSuccessful = files != null
                    if (files != null) {
                        for (file in files) {
                            if (file.isFile && file.name.endsWith(".wav")) {
                                val destFile = File(newAppDir, file.name)
                                val success = file.renameTo(destFile)
                                if (!success) {
                                    try {
                                        file.inputStream().use { input ->
                                            destFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        if (!file.delete()) {
                                            migrationSuccessful = false
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        migrationSuccessful = false
                                    }
                                }
                            }
                        }
                    }
                    if (migrationSuccessful) {
                        val remainingFiles = oldAppDir.listFiles()
                        if (remainingFiles == null || remainingFiles.isEmpty()) {
                            oldAppDir.delete()
                        }
                        prefs.edit { putBoolean("audio_migration_done", true) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
