package com.brahmadeo.supertonic.tts

import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

object SupertonicTTS {
    private var nativePtr: Long = 0
    private var currentModelPath: String? = null

    private val HINDI_SPLIT_PATTERN = Pattern.compile("([.!?\\u0964\\u0965]['\\u2019\\u201D\\u0022)}\\]]?)\\s+")
    private val JAPANESE_SPLIT_PATTERN = Pattern.compile("([。！？][」』）』）｝\\]]?)\\s*")
    private val DEFAULT_SPLIT_PATTERN = Pattern.compile("([.!?]['\\u2019\\u201D\\u0022)}\\]]?)\\s+")

    private val PARAGRAPH_REGEX = Regex("\\n\\s*\\n")
    private val COMMA_REGEX = Regex("[,\\u3001]")
    private val WHITESPACE_REGEX = Regex("\\s+")

    init {
        try {
            System.loadLibrary("onnxruntime")
            System.loadLibrary("supertonic_tts")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("SupertonicTTS", "Failed to load native library: ${e.message}")
        }
    }

    private external fun init(modelPath: String, libPath: String, ortThreads: Int, xnnThreads: Int): Long
    private external fun synthesize(ptr: Long, text: String, lang: String, stylePath: String, speed: Float, bufferSeconds: Float, steps: Int, gain: Float, sibilanceMode: Int): ByteArray
    private external fun getSocClass(ptr: Long): Int
    private external fun getSampleRate(ptr: Long): Int
    private external fun close(ptr: Long)
    private external fun reset(ptr: Long)
    private external fun nativeChunkText(text: String, lang: String): String

    @Synchronized
    fun isInitialized(modelPath: String): Boolean {
        if (nativePtr == 0L || currentModelPath != modelPath) return false
        return getSocClass(nativePtr) != -1
    }

    @Synchronized
    fun initialize(modelPath: String, libPath: String, ortThreads: Int = 4, xnnThreads: Int = 1): Boolean {
        if (nativePtr != 0L) {
            // Health check: Can we still talk to the engine?
            if (getSocClass(nativePtr) != -1) {
                if (currentModelPath == modelPath) {
                    Log.i("SupertonicTTS", "Engine already initialized and healthy for this path: $modelPath")
                    return true
                } else {
                    Log.i("SupertonicTTS", "Model path changed ($currentModelPath -> $modelPath). Re-initializing...")
                    release()
                }
            } else {
                Log.w("SupertonicTTS", "Engine pointer exists but is unhealthy. Re-initializing...")
                release()
            }
        }
        
        nativePtr = init(modelPath, libPath, ortThreads, xnnThreads)
        val success = nativePtr != 0L
        if (success) {
            currentModelPath = modelPath
            Log.i("SupertonicTTS", "Engine initialized successfully (ORT: $ortThreads, XNN: $xnnThreads) with model: $modelPath")
        } else {
            currentModelPath = null
            Log.e("SupertonicTTS", "Engine initialization FAILED")
        }
        return success
    }

    private var listeners = java.util.concurrent.CopyOnWriteArrayList<ProgressListener>()
    
    // VULN-003 fix: Use an atomic SessionContext to ensure sid and listener are updated together
    private class SessionContext(val sid: Long, val listener: ProgressListener?)
    private val currentSession = AtomicReference<SessionContext?>(null)

    interface ProgressListener {
        fun onProgress(sessionId: Long, current: Int, total: Int)
        fun onAudioChunk(sessionId: Long, data: ByteArray)
    }

    fun addProgressListener(listener: ProgressListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeProgressListener(listener: ProgressListener) {
        listeners.remove(listener)
    }

    // Called from JNI
    fun notifyProgress(current: Int, total: Int) {
        val ctx = currentSession.get()
        val sid = ctx?.sid ?: 0L
        val listener = ctx?.listener
        
        // Priority to task-specific listener
        if (listener != null) {
            listener.onProgress(sid, current, total)
        } else {
            // Only notify global listeners if no specific task listener is set
            for (l in listeners) l.onProgress(sid, current, total)
        }
    }

    // Called from JNI
    fun notifyAudioChunk(data: ByteArray) {
        val ctx = currentSession.get()
        val sid = ctx?.sid ?: 0L
        val listener = ctx?.listener
        
        // STRICT ISOLATION: Audio chunks ONLY go to the requester
        if (listener != null) {
            listener.onAudioChunk(sid, data)
        } else {
            // Only if no specific task listener is active (e.g. legacy app call)
            // we send to global listeners
            for (l in listeners) l.onAudioChunk(sid, data)
        }
    }

    @Volatile
    private var isCancelled = false

    fun setCancelled(cancelled: Boolean) {
        isCancelled = cancelled
    }

    // Called from JNI
    fun isCancelled(): Boolean {
        return isCancelled
    }

    @Volatile
    var sibilanceMode: Int = 1 // 0: Off, 1: De-esser, 2: High-shelf, 3: Low-pass

    @Volatile
    private var sessionIdCounter: Long = 0

    @Synchronized
    fun generateAudio(
        text: String,
        lang: String,
        stylePath: String,
        speed: Float = 1.0f,
        bufferDuration: Float = 0.0f,
        steps: Int = 5,
        gain: Float = 1.0f,
        listener: ProgressListener? = null,
        sibilanceMode: Int = this.sibilanceMode
    ): ByteArray? {
        if (nativePtr == 0L) {
            Log.e("SupertonicTTS", "Engine not initialized")
            return null
        }
        
        val sid = ++sessionIdCounter
        currentSession.set(SessionContext(sid, listener))
        
        try {
            val data = synthesize(nativePtr, text, lang, stylePath, speed, bufferDuration, steps, gain, sibilanceMode)
            return if (data.isNotEmpty()) data else null
        } catch (e: Exception) {
            Log.e("SupertonicTTS", "Native synthesis exception: ${e.message}")
            return null
        } finally {
            currentSession.set(null)
        }
    }

    @Synchronized
    fun getSoC(): Int {
        if (nativePtr == 0L) return -1
        return getSocClass(nativePtr)
    }

    @Synchronized
    fun getAudioSampleRate(): Int {
        if (nativePtr == 0L) return 44100
        return getSampleRate(nativePtr)
    }

    @Synchronized
    fun release() {
        val ptrToRelease = nativePtr
        if (ptrToRelease != 0L) {
            Log.i("SupertonicTTS", "Releasing engine: $ptrToRelease")
            nativePtr = 0L // Immediately nullify to prevent use-after-free
            close(ptrToRelease)
        }
    }

    @Synchronized
    fun reset() {
        if (nativePtr != 0L) {
            reset(nativePtr)
        }
    }

    fun chunkText(text: String, lang: String): List<String> {
        val joined = try {
            nativeChunkText(text, lang)
        } catch (e: UnsatisfiedLinkError) {
            fallbackChunkText(text, lang)
        } catch (e: Exception) {
            fallbackChunkText(text, lang)
        }
        return joined.split("\u001E").filter { chunk ->
            chunk.any { it.isLetterOrDigit() }
        }
    }

    private fun fallbackChunkText(text: String, lang: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        
        val normalizedLang = lang.lowercase()
        val splitPattern = when {
            normalizedLang.startsWith("hi") -> HINDI_SPLIT_PATTERN
            normalizedLang.startsWith("ja") -> JAPANESE_SPLIT_PATTERN
            else -> DEFAULT_SPLIT_PATTERN
        }
        
        val abbreviations = if (normalizedLang.startsWith("en")) {
            listOf(
                "Mr.", "Mrs.", "Dr.", "Ms.", "Prof.", "Sr.", "Jr.", 
                "etc.", "vs.", "e.g.", "i.e.",
                "Jan.", "Feb.", "Mar.", "Apr.", "May.", "Jun.", 
                "Jul.", "Aug.", "Sep.", "Oct.", "Nov.", "Dec.",
                "U.S.", "U.K.", "E.U."
            )
        } else {
            emptyList()
        }
        
        val sentences = mutableListOf<String>()
        val matcher = splitPattern.matcher(trimmed)
        var lastEnd = 0
        while (matcher.find()) {
            val matchStart = matcher.start()
            val matchEnd = matcher.end()
            val puncChar = matcher.group(1) ?: ""
            val beforePunc = trimmed.substring(lastEnd, matchStart).trim()
            
            var isAbbrev = false
            for (abbr in abbreviations) {
                val combined = beforePunc + puncChar
                if (combined.endsWith(abbr, ignoreCase = true)) {
                    isAbbrev = true
                    break
                }
            }
            
            if (!isAbbrev) {
                sentences.add(trimmed.substring(lastEnd, matchEnd).trim())
                lastEnd = matchEnd
            }
        }
        if (lastEnd < trimmed.length) {
            val remaining = trimmed.substring(lastEnd).trim()
            if (remaining.isNotEmpty()) {
                sentences.add(remaining)
            }
        }
        
        // Chunking sentence grouping up to maxChunkLen (300 or 120)
        val maxChunkLen = when {
            normalizedLang.startsWith("ja") || normalizedLang.startsWith("ko") -> 120
            else -> 300
        }

        val rawParagraphs = trimmed.split(PARAGRAPH_REGEX)
        val paragraphs = mutableListOf<String>()
        val currentPara = StringBuilder()
        
        for (para in rawParagraphs) {
            val p = para.trim()
            if (p.isEmpty()) continue
            
            if (currentPara.isNotEmpty()) {
                if (currentPara.length + p.length + 2 > maxChunkLen || p.length >= 80) {
                    paragraphs.add(currentPara.toString())
                    currentPara.clear()
                } else {
                    val lastChar = currentPara.last()
                    if (lastChar != '.' && lastChar != '!' && lastChar != '?' && lastChar != '।') {
                        currentPara.append(" .")
                    }
                    currentPara.append(" ").append(p)
                    continue
                }
            }
            
            if (p.length < 80) {
                currentPara.append(p)
            } else {
                paragraphs.add(p)
            }
        }
        if (currentPara.isNotEmpty()) {
            paragraphs.add(currentPara.toString())
        }
        
        if (paragraphs.size > 1) {
            return paragraphs.flatMap { para -> 
                val subJoined = fallbackChunkText(para, lang)
                if (subJoined.isEmpty()) emptyList() else subJoined.split("\u001E")
            }.filter { it.isNotEmpty() }.joinToString("\u001E")
        }
        
        val chunked = mutableListOf<String>()
        val currentChunk = StringBuilder()
        
        for (sentence in sentences) {
            val s = sentence.trim()
            if (s.isEmpty()) continue
            
            if (s.length > maxChunkLen) {
                // If a single sentence is huge, flush current first
                if (currentChunk.isNotEmpty()) {
                    chunked.add(currentChunk.toString())
                    currentChunk.clear()
                }
                
                // Split long sentences by comma or space
                val parts = s.split(COMMA_REGEX)
                for (part in parts) {
                    val p = part.trim()
                    if (p.isEmpty()) continue
                    
                    if (p.length > maxChunkLen) {
                        // Word-level split as last resort
                        val words = if (normalizedLang.startsWith("ja") && !p.contains(WHITESPACE_REGEX)) {
                            p.map { it.toString() }
                        } else {
                            p.split(WHITESPACE_REGEX)
                        }
                        val wordChunk = StringBuilder()
                        for (word in words) {
                            if (wordChunk.length + word.length + 1 > maxChunkLen && wordChunk.isNotEmpty()) {
                                chunked.add(wordChunk.toString())
                                wordChunk.clear()
                            }
                            if (wordChunk.isNotEmpty() && !normalizedLang.startsWith("ja")) {
                                wordChunk.append(" ")
                            }
                            wordChunk.append(word)
                        }
                        if (wordChunk.isNotEmpty()) {
                            chunked.add(wordChunk.toString())
                        }
                    } else {
                        if (currentChunk.length + p.length + 1 > maxChunkLen && currentChunk.isNotEmpty()) {
                            chunked.add(currentChunk.toString())
                            currentChunk.clear()
                        }
                        if (currentChunk.isNotEmpty()) {
                            if (normalizedLang.startsWith("ja")) currentChunk.append("、") else currentChunk.append(", ")
                        }
                        currentChunk.append(p)
                    }
                }
            } else {
                if (currentChunk.length + s.length + 1 > maxChunkLen && currentChunk.isNotEmpty()) {
                    chunked.add(currentChunk.toString())
                    currentChunk.clear()
                }
                if (currentChunk.isNotEmpty() && !normalizedLang.startsWith("ja")) {
                    currentChunk.append(" ")
                }
                currentChunk.append(s)
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunked.add(currentChunk.toString())
        }
        return chunked.joinToString("\u001E")
    }
}

