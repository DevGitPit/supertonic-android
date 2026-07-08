package com.brahmadeo.supertonic.tts

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.RemoteException
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.brahmadeo.supertonic.tts.service.IPlaybackListener
import com.brahmadeo.supertonic.tts.service.IPlaybackService
import com.brahmadeo.supertonic.tts.service.PlaybackService
import com.brahmadeo.supertonic.tts.ui.PlaybackScreen
import com.brahmadeo.supertonic.tts.ui.theme.SupertonicTheme
import com.brahmadeo.supertonic.tts.utils.TextNormalizer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.brahmadeo.supertonic.tts.utils.EbookManager
import com.brahmadeo.supertonic.tts.utils.EbookParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.readium.r2.shared.publication.services.positions

class PlaybackActivity : ComponentActivity() {

    private var playbackService: IPlaybackService? = null
    private var isBound = false

    // Reactive State
    private var sentencesState = mutableStateOf<List<String>>(emptyList())
    private var currentIndexState = mutableIntStateOf(-1)
    private var isPlayingState = mutableStateOf(false)
    private var isServiceActiveState = mutableStateOf(false)
    private var isExportingState = mutableStateOf(false)
    private var exportCurrentState = mutableIntStateOf(0)
    private var exportTotalState = mutableIntStateOf(0)
    private val sleepTimerSecondsState = mutableIntStateOf(0)

    // State persistence
    private var currentText = ""
    private var currentVoicePath = ""
    private var currentSpeed = 1.0f
    private var currentSteps = 5
    private var currentLang = "en"
    private var currentBookPath: String? = null
    private var currentChapterHref: String? = null
    private var currentPageIndex: Int = -1
    private var isRecreating = false
    private val isLoadingNextState = mutableStateOf(false)
    private lateinit var ebookParser: EbookParser

    companion object {
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_VOICE_PATH = "extra_voice_path"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_STEPS = "extra_steps"
        const val EXTRA_LANG = "extra_lang"
        const val EXTRA_BOOK_PATH = "extra_book_path"
        const val EXTRA_CHAPTER_HREF = "extra_chapter_href"
        const val EXTRA_PAGE_INDEX = "extra_page_index"
    }

    private val playbackListenerStub = object : IPlaybackListener.Stub() {
        override fun onStateChanged(isPlaying: Boolean, hasContent: Boolean, isSynthesizing: Boolean) {
            runOnUiThread {
                isPlayingState.value = isPlaying
                isServiceActiveState.value = isPlaying || isSynthesizing
            }
        }

        override fun onProgress(current: Int, total: Int) {
            runOnUiThread {
                if (isExportingState.value) {
                    exportCurrentState.intValue = current
                    exportTotalState.intValue = total
                } else {
                    if (current == -1) {
                        return@runOnUiThread
                    }
                    currentIndexState.intValue = current
                    updateIndexState(current)
                    if (total > 0 && current !in 0 until total) {
                        clearState()
                    }
                }
            }
        }

        override fun onPlaybackStopped() {
            runOnUiThread {
                isPlayingState.value = false
                isServiceActiveState.value = false
            }
        }

        override fun onExportComplete(success: Boolean, path: String) {
            runOnUiThread {
                if (!isExportingState.value) return@runOnUiThread
                isExportingState.value = false
                if (success) {
                    Toast.makeText(this@PlaybackActivity, getString(R.string.saved_to_fmt, path), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@PlaybackActivity, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onChapterChanged(newText: String, chapterHref: String?, pageIndex: Int) {
            runOnUiThread {
                currentText = newText
                currentChapterHref = chapterHref
                currentPageIndex = pageIndex
                currentIndexState.intValue = 0
                setupList(newText)
            }
        }

        override fun onTransitioningChanged(isTransitioning: Boolean) {
            runOnUiThread {
                isLoadingNextState.value = isTransitioning
            }
        }

        override fun onSleepTimerUpdated(secondsRemaining: Int) {
            runOnUiThread {
                sleepTimerSecondsState.intValue = secondsRemaining
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            playbackService = IPlaybackService.Stub.asInterface(service)
            try {
                playbackService?.setListener(playbackListenerStub)
                isBound = true

                val isResumeExtra = intent.getBooleanExtra("is_resume", false)
                val hasTextExtra = intent.hasExtra(EXTRA_TEXT)
                val shouldResume = isResumeExtra || isRecreating || !hasTextExtra
                
                if (shouldResume) {
                    val serviceIndex = try { playbackService?.getCurrentIndex() ?: -1 } catch (_: Exception) { -1 }
                    if (serviceIndex != -1) {
                        currentIndexState.intValue = serviceIndex
                    }
                    restoreState()
                } else {
                    // New playback request (hasTextExtra is true and not a resume request)
                    startPlaybackFromIntent()
                }
            } catch (e: RemoteException) {
                e.printStackTrace()
                Toast.makeText(this@PlaybackActivity, "Failed to connect to playback service", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            playbackService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isRecreating = savedInstanceState != null
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ebookParser = EbookParser(this)
        handleIntent(intent)
        setupUI()

        val intent = Intent(this, PlaybackService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun setupUI() {
        setContent {
            SupertonicTheme(voiceFile = currentVoicePath) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PlaybackScreen(
                        sentences = sentencesState.value,
                        currentIndex = currentIndexState.intValue,
                        isPlaying = isPlayingState.value,
                        isServiceActive = isServiceActiveState.value,
                        isExporting = isExportingState.value,
                        exportCurrent = exportCurrentState.intValue,
                        exportTotal = exportTotalState.intValue,
                        sleepTimerSecondsRemaining = sleepTimerSecondsState.intValue,
                        onBackClick = { finish() },
                        onHomeClick = {
                            val intent = Intent(this@PlaybackActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(intent)
                            finish()
                        },
                        onItemClick = { index -> playFromIndex(index) },
                        onPlayPauseClick = { handlePlayPause() },
                        onStopClick = { handleStop() },
                        onExportClick = { startExport() },
                        onCancelExportClick = {
                            try { playbackService?.stop() } catch (e: Exception) {}
                            if (isExportingState.value) {
                                isExportingState.value = false
                                Toast.makeText(this@PlaybackActivity, "Audio saving cancelled", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSleepTimerClick = { handleSleepTimerClick() }
                    )

                    if (isLoadingNextState.value) {
                        Surface(
                            modifier = Modifier.fillMaxSize().clickable(enabled = false) {},
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Loading next chapter...", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val isResumeExtra = intent.getBooleanExtra("is_resume", false)
        val hasTextExtra = intent.hasExtra(EXTRA_TEXT)
        val shouldResume = isResumeExtra || isRecreating || !hasTextExtra
        
        if (shouldResume) {
            val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
            currentText = prefs.getString("last_text", "") ?: ""
            currentVoicePath = prefs.getString("last_voice_path", "") ?: ""
            currentSpeed = prefs.getFloat("last_speed", 1.0f)
            currentSteps = prefs.getInt("last_steps", 5)
            currentLang = prefs.getString("last_lang", "en") ?: "en"
            currentIndexState.intValue = prefs.getInt("last_index", 0)
            currentBookPath = prefs.getString("last_book_path", null)
            currentChapterHref = prefs.getString("last_chapter_href", null)
            currentPageIndex = prefs.getInt("last_page_index", -1)
        } else {
            currentText = intent.getStringExtra(EXTRA_TEXT) ?: ""
            currentVoicePath = intent.getStringExtra(EXTRA_VOICE_PATH) ?: ""
            currentSpeed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
            currentSteps = intent.getIntExtra(EXTRA_STEPS, 5)
            currentLang = intent.getStringExtra(EXTRA_LANG) ?: "en"
            currentBookPath = intent.getStringExtra(EXTRA_BOOK_PATH)
            currentChapterHref = intent.getStringExtra(EXTRA_CHAPTER_HREF)
            currentPageIndex = intent.getIntExtra(EXTRA_PAGE_INDEX, -1)
            
            // Reset state for new playback
            currentIndexState.intValue = -1
            isExportingState.value = false
            

            saveState()
        }

        setupList(currentText)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
        val newText = prefs.getString("last_text", "") ?: ""
        if (newText.isNotEmpty() && newText != currentText) {
            currentText = newText
            currentVoicePath = prefs.getString("last_voice_path", "") ?: ""
            currentSpeed = prefs.getFloat("last_speed", 1.0f)
            currentSteps = prefs.getInt("last_steps", 5)
            currentLang = prefs.getString("last_lang", "en") ?: "en"
            currentBookPath = prefs.getString("last_book_path", null)
            currentChapterHref = prefs.getString("last_chapter_href", null)
            currentPageIndex = prefs.getInt("last_page_index", -1)
            
            val serviceIndex = try { playbackService?.getCurrentIndex() ?: -1 } catch (_: Exception) { -1 }
            currentIndexState.intValue = if (serviceIndex != -1) serviceIndex else prefs.getInt("last_index", 0)
            
            setupList(currentText)
        } else {
            if (isBound && playbackService != null) {
                try {
                    val serviceIndex = playbackService?.getCurrentIndex() ?: -1
                    if (serviceIndex != -1) {
                        currentIndexState.intValue = serviceIndex
                    }
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
        }

        if (isBound && playbackService != null) {
            try {
                playbackService?.setListener(playbackListenerStub)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private val playbackReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                "com.brahmadeo.supertonic.tts.SLEEP_TIMER_EXPIRED" -> {
                    finish()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter().apply {
            addAction("com.brahmadeo.supertonic.tts.SLEEP_TIMER_EXPIRED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playbackReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(playbackReceiver)
        } catch (_: Exception) {}
    }

    private fun handleSleepTimerClick() {
        val service = playbackService
        if (!isBound || service == null) {
            Toast.makeText(this, "Playback service not ready", Toast.LENGTH_SHORT).show()
            return
        }
        val currentSeconds = try { service.getSleepTimerSeconds() } catch (e: RemoteException) { 0 }
        val nextMinutes = when {
            currentSeconds == 0 -> 10
            currentSeconds <= 10 * 60 -> 20
            currentSeconds <= 20 * 60 -> 30
            else -> 0
        }
        
        try {
            service.setSleepTimer(nextMinutes)
            if (nextMinutes == 0) {
                Toast.makeText(this, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sleep timer set to $nextMinutes minutes", Toast.LENGTH_SHORT).show()
            }
            sleepTimerSecondsState.intValue = nextMinutes * 60
        } catch (e: RemoteException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to set sleep timer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupList(text: String) {
        val normalizer = TextNormalizer()
        val sentences = normalizer.splitIntoSentences(text, currentLang)
        sentencesState.value = sentences
    }

    private fun handlePlayPause() {
        try {
            if (isPlayingState.value) {
                playbackService?.stop() // Or pause if implemented
            } else if (isServiceActiveState.value) {
                playFromIndex(currentIndexState.intValue)
            } else {
                if (currentIndexState.intValue >= 0) {
                    playFromIndex(currentIndexState.intValue)
                } else {
                    startPlaybackFromIntent()
                }
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun handleStop() {
        try {
            playbackService?.stop()
        } catch (e: RemoteException) { }
        clearState()
        finish()
    }

    private fun startPlaybackFromIntent() {
        if (currentText.isEmpty()) return
        saveState()
        try {
            playbackService?.synthesizeAndPlay(currentText, currentLang, currentVoicePath, currentSpeed, currentSteps, 0)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun playFromIndex(index: Int) {
        if (currentText.isEmpty()) return
        saveState()
        try {
            playbackService?.synthesizeAndPlay(currentText, currentLang, currentVoicePath, currentSpeed, currentSteps, index)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun saveState() {
        getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit {
            putString("last_text", currentText)
                .putString("last_voice_path", currentVoicePath)
                .putFloat("last_speed", currentSpeed)
                .putInt("last_steps", currentSteps)
                .putString("last_lang", currentLang)
                .putBoolean("is_playing", true)
                .putString("last_book_path", currentBookPath)
                .putString("last_chapter_href", currentChapterHref)
                .putInt("last_page_index", currentPageIndex)
        }
    }

    private fun updateIndexState(index: Int) {
        getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit {
            putInt("last_index", index)
        }
    }

    private fun clearState() {
        getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).edit {
            putBoolean("is_playing", false)
        }
    }

    private fun restoreState() {
        try {
            if (playbackService?.isServiceActive == false) {
                 playbackListenerStub.onStateChanged(false, true, false)
            }
        } catch (e: RemoteException) { }
    }

    private fun getExportFileName(text: String): String {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            return "Supertonic_TTS_$timestamp.wav"
        }
        
        // Split by whitespace and grab up to 5 words
        val words = cleanText.split(Regex("\\s+")).take(5)
        
        // Sanitize each word to allow only alphanumeric characters
        val sanitizedWords = words.map { word ->
            word.filter { it.isLetterOrDigit() }
        }.filter { it.isNotEmpty() }
        
        val baseName = if (sanitizedWords.isEmpty()) {
            "Supertonic_TTS"
        } else {
            sanitizedWords.joinToString("_")
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${baseName}_$timestamp.wav"
    }

    private fun startExport() {
        if (currentText.isEmpty()) {
            Toast.makeText(this, "No text to save", Toast.LENGTH_SHORT).show()
            return
        }

        exportCurrentState.intValue = 0
        exportTotalState.intValue = sentencesState.value.size
        isExportingState.value = true

        val filename = getExportFileName(currentText)
        val appDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(filesDir, "Music")
        if (!appDir.exists()) appDir.mkdirs()
        val file = File(appDir, filename)

        try {
            playbackService?.exportAudio(currentText, currentLang, currentVoicePath, currentSpeed, currentSteps, file.absolutePath)
        } catch (e: RemoteException) {
            e.printStackTrace()
            isExportingState.value = false
            Toast.makeText(this, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try {
                playbackService?.removeListener(playbackListenerStub)
            } catch (e: Exception) { }
            unbindService(connection)
            isBound = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        
        // If a new playback intent is delivered, trigger playback start
        val isResumeExtra = intent.getBooleanExtra("is_resume", false)
        val hasTextExtra = intent.hasExtra(EXTRA_TEXT)
        val shouldResume = isResumeExtra || !hasTextExtra
        if (!shouldResume && isBound) {
            startPlaybackFromIntent()
        }
    }


}
