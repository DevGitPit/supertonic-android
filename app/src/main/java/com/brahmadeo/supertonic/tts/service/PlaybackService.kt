@file:Suppress("DEPRECATION")
package com.brahmadeo.supertonic.tts.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.brahmadeo.supertonic.tts.R
import com.brahmadeo.supertonic.tts.SupertonicTTS
import com.brahmadeo.supertonic.tts.utils.AssetManager
import com.brahmadeo.supertonic.tts.utils.ModelVersion
import com.brahmadeo.supertonic.tts.utils.QueueItem
import com.brahmadeo.supertonic.tts.utils.QueueManager
import com.brahmadeo.supertonic.tts.utils.TextNormalizer
import com.brahmadeo.supertonic.tts.utils.WavUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class PlaybackService : Service(), SupertonicTTS.ProgressListener, AudioManager.OnAudioFocusChangeListener {

    private val binder = object : IPlaybackService.Stub() {
        override fun synthesizeAndPlay(text: String, lang: String, stylePath: String, speed: Float, steps: Int, startIndex: Int) {
            this@PlaybackService.synthesizeAndPlay(text, lang, stylePath, speed, steps, startIndex)
        }

        override fun addToQueue(text: String, lang: String, stylePath: String, speed: Float, steps: Int, startIndex: Int) {
            this@PlaybackService.addToQueue(text, lang, stylePath, speed, steps, startIndex)
        }

        override fun play() {
            this@PlaybackService.play()
        }

        override fun pause() {
            this@PlaybackService.pause()
        }

        override fun stop() {
            this@PlaybackService.stopServicePlayback()
        }

        override fun isServiceActive(): Boolean {
            return this@PlaybackService.isServiceActive()
        }

        override fun setListener(listener: IPlaybackListener?) {
            this@PlaybackService.setListener(listener)
        }

        override fun removeListener(listener: IPlaybackListener?) {
            this@PlaybackService.removeListener(listener)
        }

        override fun exportAudio(text: String, lang: String, stylePath: String, speed: Float, steps: Int, outputPath: String) {
            this@PlaybackService.exportAudio(text, lang, stylePath, speed, steps, File(outputPath))
        }

        override fun getCurrentIndex(): Int {
            return currentSentenceIndex
        }
    }

    private val listeners = RemoteCallbackList<IPlaybackListener>()

    fun setListener(listener: IPlaybackListener?) {
        if (listener != null) {
            listeners.register(listener)
            try {
                listener.onStateChanged(isPlaying, audioTrack != null || isSynthesizing, isSynthesizing)
                listener.onProgress(currentSentenceIndex, -1)
            } catch (_: RemoteException) {}
        }
    }

    fun removeListener(listener: IPlaybackListener?) {
        if (listener != null) {
            listeners.unregister(listener)
        }
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var audioTrack: AudioTrack? = null
    private var lastTrackRate: Int = -1
    @Volatile private var isPlaying = false
    @Volatile private var isSynthesizing = false
    private val textNormalizer = TextNormalizer()
    private var resumeOnFocusGain = false
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val attributionContext: Context by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createAttributionContext("supertonic_playback")
        } else {
            this
        }
    }

    private data class PlaybackItem(val index: Int, val data: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PlaybackItem

            if (index != other.index) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = index
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    private var currentSentenceIndex: Int = 0

    companion object {
        const val CHANNEL_ID = "supertonic_playback"
        const val NOTIFICATION_ID = 1
        const val TAG = "PlaybackService"
        const val VOLUME_BOOST_FACTOR = 2.5f
        const val AUDIO_WRITE_CHUNK_SIZE = 8192
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        com.brahmadeo.supertonic.tts.utils.LexiconManager.load(this)
        QueueManager.initialize(this)

        audioManager = attributionContext.getSystemService(AUDIO_SERVICE) as AudioManager
        val powerManager = attributionContext.getSystemService(POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Supertonic:PlaybackWakeLock")
        
        mediaSession = MediaSessionCompat(attributionContext, "SupertonicMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { this@PlaybackService.play() }
                override fun onPause() { this@PlaybackService.pause() }
                override fun onStop() { this@PlaybackService.stopPlayback() }
            })
            isActive = true
        }

        val savedLang = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).getString("selected_lang", "en") ?: "en"
        val modelVersion = resolveModelVersion(savedLang)
        val modelPath = File(filesDir, "${modelVersion.dirName}/onnx").absolutePath
        val libPath = applicationInfo.nativeLibraryDir + "/libonnxruntime.so"
        SupertonicTTS.initialize(modelPath, libPath)
    }

    private fun resolveModelVersion(savedLang: String): ModelVersion = ModelVersion.resolve(
        savedLang = savedLang,
        isV3Ready = AssetManager.isV3Ready(this),
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_PLAYBACK") {
            stopPlayback()
        } else if (intent?.action == "RESET_ENGINE") {
            SupertonicTTS.release()
            val savedLang = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE).getString("selected_lang", "en") ?: "en"
            val modelVersion = intent.getStringExtra("model_version")?.let { ModelVersion.fromDirName(it) }
                ?: resolveModelVersion(savedLang)
            val modelPath = File(filesDir, "${modelVersion.dirName}/onnx").absolutePath
            val libPath = applicationInfo.nativeLibraryDir + "/libonnxruntime.so"
            SupertonicTTS.initialize(modelPath, libPath)
        }
        return START_NOT_STICKY
    }

    fun isServiceActive(): Boolean {
        return isPlaying || isSynthesizing
    }

    fun addToQueue(text: String, lang: String, stylePath: String, speed: Float, steps: Int, startIndex: Int) {
        QueueManager.add(QueueItem(
            text = text,
            lang = lang,
            stylePath = stylePath,
            speed = speed,
            steps = steps,
            startIndex = startIndex
        ))
    }

    private var synthesisJob: Job? = null

    fun synthesizeAndPlay(text: String, lang: String, stylePath: String, speed: Float, steps: Int, startIndex: Int = 0) {
        serviceScope.launch {
            if (synthesisJob?.isActive == true) {
                SupertonicTTS.setCancelled(true)
                synthesisJob?.cancelAndJoin()
            }
            
            stopPlayback(removeNotification = false)
            
            isSynthesizing = true
            isPlaying = true
            SupertonicTTS.setCancelled(false) 
            
            updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            startForegroundService(getString(R.string.notif_synthesizing), false)
            notifyListenerState(false)
            
            wakeLock?.acquire(10 * 60 * 1000L)
            
            if (!requestAudioFocus()) {
                Log.w(TAG, "Audio Focus denied")
            }

            synthesisJob = launch(Dispatchers.IO) {
                val sentences = textNormalizer.splitIntoSentences(text, lang)
                val totalSentences = sentences.size
                
                // Fix: If resume index is out of bounds, restart from beginning
                val validStartIndex = if (startIndex in 0 until totalSentences) startIndex else 0

                // Channel size 10 to allow producer to stay ahead
                val channel = kotlinx.coroutines.channels.Channel<PlaybackItem>(10)
                val preBufferComplete = CompletableDeferred<Unit>()

                // Producer
                launch {
                    var producedCount = 0
                    for (index in validStartIndex until totalSentences) {
                        if (SupertonicTTS.isCancelled() || !isActive) break
                        
                        while (!isPlaying && isSynthesizing && isActive) {
                            delay(100)
                        }
                        if (SupertonicTTS.isCancelled() || !isActive || !isSynthesizing) break

                        val sentence = sentences[index]
                        val sentenceLang = lang // Strict enforcement as per requirement
                        
                        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
                        val isAdvancedEnabled = prefs.getBoolean("is_advanced_normalization", false)
                        val normalizedText = textNormalizer.normalize(sentence, sentenceLang, isAdvancedEnabled)

                        val audioData = SupertonicTTS.generateAudio(
                            normalizedText, sentenceLang, stylePath, speed, 0.0f, steps, VOLUME_BOOST_FACTOR, null
                        )
                        
                        if (audioData != null && audioData.isNotEmpty()) {
                            channel.send(PlaybackItem(index, audioData))
                            producedCount++
                            
                            // Signal pre-buffer complete when 3 chunks are ready (2 in buffer)
                            if (producedCount >= 3 && !preBufferComplete.isCompleted) {
                                preBufferComplete.complete(Unit)
                                Log.d(TAG, "Pre-buffer complete: 3 chunks ready")
                            }
                        }
                    }
                    
                    // If we finish generating before reaching 3, complete anyway
                    if (!preBufferComplete.isCompleted) {
                        preBufferComplete.complete(Unit)
                    }
                    channel.close()
                }

                // Wait for pre-buffer (3 chunks ready)
                preBufferComplete.await()
                
                withContext(Dispatchers.Main) {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    notifyListenerState(true)
                }

                // Consumer
                for (item in channel) {
                    if (SupertonicTTS.isCancelled() || !isActive || !isSynthesizing) break
                    
                withContext(Dispatchers.Main) {
                    currentSentenceIndex = item.index
                    notifyListenerProgress(item.index, totalSentences)
                }
                    
                    playAudioDataBlocking(item.data)
                }
                
                withContext(Dispatchers.Main) {
                    if (isSynthesizing && isActive) {
                        val wasCancelled = SupertonicTTS.isCancelled()
                        isSynthesizing = false
                        
                        if (!wasCancelled) {
                            notifyListenerProgress(totalSentences, totalSentences)
                        }
                        
                        notifyListenerState(true)

                        if (!wasCancelled) {
                            // Check queue for next item
                            val nextItem = QueueManager.next()
                            if (nextItem != null) {
                                SupertonicTTS.reset() // Explicit JNI Handshake
                                synthesizeAndPlay(nextItem.text, nextItem.lang, nextItem.stylePath, nextItem.speed, nextItem.steps, nextItem.startIndex)
                            } else {
                                stopPlayback()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun playAudioDataBlocking(data: ByteArray) {
        if (!currentCoroutineContext().isActive) return
        val rate = SupertonicTTS.getAudioSampleRate()
        
        // Reuse or create AudioTrack
        var track: AudioTrack? = null
        synchronized(this) {
            track = audioTrack
            try {
                if (track == null || lastTrackRate != rate || track.state == AudioTrack.STATE_UNINITIALIZED) {
                    try { track?.release() } catch (_: Exception) {}
                    
                    val minBufferSize = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4
                    val builder = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                        .setAudioFormat(AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                        .setBufferSizeInBytes(minBufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        builder.setContext(attributionContext)
                    }

                    track = builder.build()
                    
                    audioTrack = track
                    lastTrackRate = rate
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create AudioTrack", e)
                return
            }
        }
        
        val safeTrack = track ?: return
        if (safeTrack.state != AudioTrack.STATE_INITIALIZED) return

        withContext(Dispatchers.Main) {
            if (isPlaying && safeTrack.state == AudioTrack.STATE_INITIALIZED && safeTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    safeTrack.play()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Failed to start AudioTrack", e)
                }
                notifyListenerState(true)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }
        }
        
        // Use a small wait logic to keep progress UI somewhat in sync
        // but start writing the next chunk before this one is fully finished.
        val startHead = try { safeTrack.playbackHeadPosition } catch (_: Exception) { 0 }
        val chunkSamples = data.size / 2
        
        // Write data in loop to handle pause/resume gracefully
        var offset = 0
        while (offset < data.size && currentCoroutineContext().isActive && isSynthesizing) {
            if (!isPlaying) {
                if (safeTrack.state == AudioTrack.STATE_INITIALIZED && safeTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    try { safeTrack.pause() } catch (_: Exception) {}
                }
                delay(100)
                continue
            } else {
                if (safeTrack.state == AudioTrack.STATE_INITIALIZED && safeTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        safeTrack.play()
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "Failed to start AudioTrack in loop", e)
                        break
                    }
                }
            }
            
            val toWrite = (data.size - offset).coerceAtMost(AUDIO_WRITE_CHUNK_SIZE)
            val written = try {
                safeTrack.write(data, offset, toWrite, AudioTrack.WRITE_BLOCKING)
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack write exception", e)
                -1
            }
            
            if (written > 0) {
                offset += written
            } else {
                if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: $written")
                } else {
                    Log.w(TAG, "AudioTrack write returned 0, stopping chunk playback")
                }
                break
            }
        }

        // To keep the progress indicator moving roughly with the audio, 
        // we wait until the head has moved significantly.
        // We leave about 100ms of "overlap" to ensure zero gap.
        val samplesToWait = chunkSamples - (rate / 10) // Wait until 100ms before end
        while (currentCoroutineContext().isActive && isSynthesizing && isPlaying) {
            val currentHead = try { safeTrack.playbackHeadPosition } catch (_: Exception) { startHead + samplesToWait }
            val headMove = currentHead - startHead
            if (headMove >= samplesToWait) break
            delay(50)
        }
    }

    override fun onProgress(sessionId: Long, current: Int, total: Int) {}
    override fun onAudioChunk(sessionId: Long, data: ByteArray) {}

    fun play() {
        resumeOnFocusGain = false
        if (!isPlaying) {
            if (requestAudioFocus()) {
                isPlaying = true
                try {
                    if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                        audioTrack?.play()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing audio track", e)
                }
                notifyListenerState(true)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                startForegroundService(getString(R.string.notif_playing), true)
            }
        }
    }

    fun pause() {
        resumeOnFocusGain = false
        if (isPlaying) {
            isPlaying = false
            try {
                if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                    audioTrack?.pause()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing audio track", e)
            }
            notifyListenerState(false)
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            updateNotification(getString(R.string.notif_paused))
        }
    }

    fun stopPlayback(removeNotification: Boolean = true) {
        synchronized(this) {
            isPlaying = false
            try {
                if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                    audioTrack?.stop()
                }
                audioTrack?.release()
            } catch (_: Exception) { }
            audioTrack = null
        }
        resumeOnFocusGain = false
        notifyListenerState(false)
        abandonAudioFocus()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (removeNotification) {
            notifyListenerPlaybackStopped()
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    fun stopServicePlayback() {
        isPlaying = false
        try {
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.pause()
            }
        } catch (_: Exception) {}

        serviceScope.launch {
            SupertonicTTS.setCancelled(true)
            isSynthesizing = false
            synthesisJob?.cancelAndJoin()
            stopPlayback()
        }
    }

    private fun notifyListenerState(playing: Boolean) {
        val n = listeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                listeners.getBroadcastItem(i).onStateChanged(playing, audioTrack != null || isSynthesizing, isSynthesizing)
            } catch (_: RemoteException) {}
        }
        listeners.finishBroadcast()
    }

    private fun notifyListenerProgress(current: Int, total: Int) {
        val n = listeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                listeners.getBroadcastItem(i).onProgress(current, total)
            } catch (_: RemoteException) {}
        }
        listeners.finishBroadcast()
    }

    private fun notifyListenerPlaybackStopped() {
        val n = listeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                listeners.getBroadcastItem(i).onPlaybackStopped()
            } catch (_: RemoteException) {}
        }
        listeners.finishBroadcast()
    }

    private fun notifyListenerExportComplete(success: Boolean, path: String) {
        val n = listeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                listeners.getBroadcastItem(i).onExportComplete(success, path)
            } catch (_: RemoteException) {}
        }
        listeners.finishBroadcast()
    }

    private fun requestAudioFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            return audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> stopServicePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) {
                    resumeOnFocusGain = true
                    isPlaying = false
                    try {
                        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                            audioTrack?.pause()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pausing on focus loss", e)
                    }
                    notifyListenerState(false)
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try {
                    audioTrack?.setVolume(0.2f)
                } catch (_: Exception) {}
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                try {
                    audioTrack?.setVolume(1.0f)
                } catch (_: Exception) {}
                if (resumeOnFocusGain) play()
            }
        }
    }

    fun exportAudio(text: String, lang: String, stylePath: String, speed: Float, steps: Int, outputFile: File) {
        serviceScope.launch {
            if (synthesisJob?.isActive == true) {
                SupertonicTTS.setCancelled(true)
                synthesisJob?.cancelAndJoin()
            }
            
            stopPlayback(removeNotification = false)
            SupertonicTTS.setCancelled(false)
            isSynthesizing = true
            notifyListenerState(false)
            startForegroundService(getString(R.string.notif_exporting), false)
            
            synthesisJob = launch(Dispatchers.IO) {
                var exportSuccess = false
                try {
                    val sentences = textNormalizer.splitIntoSentences(text, lang)
                    if (sentences.isEmpty()) {
                        Log.w(TAG, "Export: No sentences found")
                        return@launch
                    }

                    val outputStream = ByteArrayOutputStream()
                    for ((index, sentence) in sentences.withIndex()) {
                        if (!isActive || SupertonicTTS.isCancelled()) break
                        
                        withContext(Dispatchers.Main) {
                            notifyListenerProgress(index + 1, sentences.size)
                        }

                        val prefs = getSharedPreferences("SupertonicPrefs", MODE_PRIVATE)
                        val isAdvancedEnabled = prefs.getBoolean("is_advanced_normalization", false)
                        val normalizedText = textNormalizer.normalize(sentence, lang, isAdvancedEnabled)

                        val audioData = SupertonicTTS.generateAudio(normalizedText, lang, stylePath, speed, 0.0f, steps, VOLUME_BOOST_FACTOR, null)
                        if (audioData != null && audioData.isNotEmpty()) {
                            outputStream.write(audioData)
                        } else if (SupertonicTTS.isCancelled()) {
                            break
                        }
                    }
                    
                    if (isActive && !SupertonicTTS.isCancelled() && outputStream.size() > 0) {
                        WavUtils.saveWav(outputFile, outputStream.toByteArray(), SupertonicTTS.getAudioSampleRate())
                        exportSuccess = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Export failed", e)
                } finally {
                    withContext(Dispatchers.Main) {
                        isSynthesizing = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        notifyListenerExportComplete(exportSuccess, outputFile.absolutePath)
                        notifyListenerState(false)
                    }
                }
            }
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun startForegroundService(status: String, showControls: Boolean) {
        val notification = buildNotification(status, showControls)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
    }

    private fun updateNotification(status: String) {
        val notificationManager = attributionContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status, true))
    }

    private fun buildNotification(status: String, showControls: Boolean): android.app.Notification {
        val activityIntent = Intent(this, com.brahmadeo.supertonic.tts.PlaybackActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("is_resume", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0))

        if (showControls) {
            if (isPlaying) {
                builder.addAction(android.R.drawable.ic_media_pause, getString(R.string.notif_paused),
                    androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE))
            } else {
                builder.addAction(android.R.drawable.ic_media_play, getString(R.string.yes), // No play string in resources, reusing yes for now or just generic
                    androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY))
            }
        } else {
             builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel),
                androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = attributionContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        serviceScope.cancel()
        abandonAudioFocus()
    }
}