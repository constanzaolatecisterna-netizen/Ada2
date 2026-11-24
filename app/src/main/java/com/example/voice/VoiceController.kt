package com.example.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

class VoiceController(
    private val context: Context,
    private val onSpeechStarted: () -> Unit,
    private val onSpeechWordPulse: () -> Unit = {},
    private val onSpeechFinished: (autoListenRequested: Boolean) -> Unit,
    private val onListeningStarted: () -> Unit,
    private val onPartialTranscript: (String) -> Unit,
    private val onFinalTranscript: (String) -> Unit,
    private val onListeningStopped: (errorMessage: String?) -> Unit,
) {
    private data class PendingSpeakRequest(
        val text: String,
        val autoListenAfter: Boolean,
        val interrupt: Boolean,
    )

    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false
    private val ttsLock = Any()
    private var speechSessionGen = 0L
    private var cancelledSessionMax = 0L
    private val pendingChunkIds = mutableSetOf<String>()
    private var pendingAutoListen = false
    private var hadUtteranceError = false
    private var pendingSpeakBeforeReady: PendingSpeakRequest? = null

    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile
    private var manualStopRequested = false
    private var busyRetryCount = 0

    private val ttsHandler = Handler(Looper.getMainLooper())
    private val recognizerHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            ttsHandler.post { stopSpeech() }
        }
    }

    private val speechAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    @Volatile
    var isSpeakingNow: Boolean = false
        private set

    @Volatile
    var lastSpeechEndedAtMs: Long = 0L
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                runCatching { engine.setAudioAttributes(speechAudioAttributes) }

                val localeCandidates = listOf(
                    Locale.forLanguageTag("es-CL"),
                    Locale.forLanguageTag("es-419"),
                    Locale.forLanguageTag("es-MX"),
                    Locale.forLanguageTag("es-ES"),
                    Locale.forLanguageTag("es"),
                    Locale.getDefault(),
                )
                for (loc in localeCandidates) {
                    val res = engine.setLanguage(loc)
                    if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                        break
                    }
                }

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val gen = parseSessionGen(utteranceId)
                        synchronized(ttsLock) {
                            if (gen <= cancelledSessionMax) return
                            isSpeakingNow = true
                        }
                        ttsHandler.post { onSpeechStarted() }
                    }

                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        val gen = parseSessionGen(utteranceId)
                        synchronized(ttsLock) {
                            if (gen <= cancelledSessionMax) return
                        }
                        ttsHandler.post { onSpeechWordPulse() }
                    }

                    override fun onDone(utteranceId: String?) {
                        handleUtteranceComplete(utteranceId, isError = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handleUtteranceComplete(utteranceId, isError = true)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        handleUtteranceComplete(utteranceId, isError = true)
                    }
                })

                val queuedBeforeInit: PendingSpeakRequest?
                synchronized(ttsLock) {
                    ttsReady = true
                    queuedBeforeInit = pendingSpeakBeforeReady
                    pendingSpeakBeforeReady = null
                }
                if (queuedBeforeInit != null) {
                    ttsHandler.post {
                        speak(
                            text = queuedBeforeInit.text,
                            autoListenAfter = queuedBeforeInit.autoListenAfter,
                            interrupt = queuedBeforeInit.interrupt,
                        )
                    }
                }
            } else {
                Log.w("VoiceController", "TTS init status=$status")
                val hadPending: Boolean
                synchronized(ttsLock) {
                    hadPending = pendingSpeakBeforeReady != null
                    pendingSpeakBeforeReady = null
                    isSpeakingNow = false
                }
                if (hadPending) {
                    ttsHandler.post { onSpeechFinished(false) }
                }
            }
        }
    }

    private fun parseSessionGen(utteranceId: String?): Long {
        if (utteranceId == null || !utteranceId.startsWith("ada_")) return -1L
        val parts = utteranceId.split('_')
        return parts.getOrNull(1)?.toLongOrNull() ?: -1L
    }

    private fun handleUtteranceComplete(utteranceId: String?, isError: Boolean) {
        var finishedAll = false
        var autoListen = false
        synchronized(ttsLock) {
            val gen = parseSessionGen(utteranceId)
            if (gen <= cancelledSessionMax) return
            if (utteranceId != null) {
                pendingChunkIds.remove(utteranceId)
            }
            if (isError) {
                hadUtteranceError = true
            }
            if (pendingChunkIds.isEmpty()) {
                finishedAll = true
                isSpeakingNow = false
                lastSpeechEndedAtMs = System.currentTimeMillis()
                autoListen = pendingAutoListen && !hadUtteranceError
                pendingAutoListen = false
                hadUtteranceError = false
            }
        }
        if (finishedAll) {
            abandonSpeechAudioFocus()
            ttsHandler.post { onSpeechFinished(autoListen) }
        }
    }

    private fun requestSpeechAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(speechAudioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                )
            }
        }
    }

    private fun abandonSpeechAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusListener)
            }
        }
    }

    private fun splitSpeechChunks(text: String): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()
        val maxLen = minOf(220, TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(220))
        if (normalized.length <= maxLen) return listOf(normalized)

        val sentences = normalized.split(Regex("(?<=[.!?…])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        fun flushCurrent() {
            if (current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.setLength(0)
            }
        }

        for (sentence in sentences) {
            val s = sentence.trim()
            if (s.isEmpty()) continue
            if (s.length > maxLen) {
                flushCurrent()
                var start = 0
                while (start < s.length) {
                    val endLimit = minOf(start + maxLen, s.length)
                    var splitAt = endLimit
                    if (endLimit < s.length) {
                        val lastPunct = s.lastIndexOfAny(charArrayOf(',', ';', ':', ' '), endLimit)
                        if (lastPunct > start + 40) {
                            splitAt = lastPunct + 1
                        }
                    }
                    chunks.add(s.substring(start, splitAt).trim())
                    start = splitAt
                }
            } else if (current.isEmpty()) {
                current.append(s)
            } else if (current.length + 1 + s.length <= maxLen) {
                current.append(' ').append(s)
            } else {
                flushCurrent()
                current.append(s)
            }
        }
        flushCurrent()
        return chunks.filter { it.isNotEmpty() }
    }

    fun speak(text: String, autoListenAfter: Boolean, interrupt: Boolean = true) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = tts
        if (engine == null || !ttsReady) {
            synchronized(ttsLock) {
                val existing = pendingSpeakBeforeReady
                pendingSpeakBeforeReady = if (!interrupt && existing != null) {
                    PendingSpeakRequest(
                        text = "${existing.text}. $clean",
                        autoListenAfter = existing.autoListenAfter || autoListenAfter,
                        interrupt = existing.interrupt,
                    )
                } else {
                    PendingSpeakRequest(clean, autoListenAfter, interrupt)
                }
            }
            return
        }

        val chunks = splitSpeechChunks(clean)
        if (chunks.isEmpty()) return

        val useQueueAdd: Boolean
        val gen: Long
        val chunkEntries: List<Pair<String, String>>

        synchronized(ttsLock) {
            useQueueAdd = !interrupt && isSpeakingNow && pendingChunkIds.isNotEmpty()
            if (!useQueueAdd) {
                cancelledSessionMax = speechSessionGen
                pendingChunkIds.clear()
                hadUtteranceError = false
                pendingAutoListen = autoListenAfter
            } else {
                pendingAutoListen = pendingAutoListen || autoListenAfter
            }
            speechSessionGen++
            gen = speechSessionGen
            chunkEntries = chunks.mapIndexed { idx, chunk ->
                val id = "ada_${gen}_$idx"
                pendingChunkIds.add(id)
                id to chunk
            }
            isSpeakingNow = true
        }

        if (!useQueueAdd) {
            runCatching { engine.stop() }
        }
        requestSpeechAudioFocus()
        runCatching {
            engine.setPitch(1.04f)
            engine.setSpeechRate(0.97f)
        }

        var enqueueFailed = false
        for ((idx, entry) in chunkEntries.withIndex()) {
            val (utteranceId, chunkText) = entry
            val queueMode = if (!useQueueAdd && idx == 0) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = runCatching {
                engine.speak(chunkText, queueMode, params, utteranceId)
            }.getOrDefault(TextToSpeech.ERROR)
            if (result == TextToSpeech.ERROR) {
                synchronized(ttsLock) {
                    pendingChunkIds.remove(utteranceId)
                }
                enqueueFailed = true
            }
        }

        val shouldNotifyImmediateFailure: Boolean
        synchronized(ttsLock) {
            shouldNotifyImmediateFailure = enqueueFailed && pendingChunkIds.isEmpty()
            if (shouldNotifyImmediateFailure) {
                isSpeakingNow = false
                pendingAutoListen = false
                hadUtteranceError = false
            }
        }
        if (shouldNotifyImmediateFailure) {
            abandonSpeechAudioFocus()
            ttsHandler.post { onSpeechFinished(false) }
        }
    }

    fun stopSpeech() {
        synchronized(ttsLock) {
            val wasSpeaking = isSpeakingNow
            cancelledSessionMax = speechSessionGen
            pendingChunkIds.clear()
            pendingSpeakBeforeReady = null
            isSpeakingNow = false
            pendingAutoListen = false
            hadUtteranceError = false
            if (wasSpeaking) {
                lastSpeechEndedAtMs = System.currentTimeMillis()
            }
        }
        abandonSpeechAudioFocus()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun startListening(isWakeCheck: Boolean = false) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onListeningStopped("El reconocimiento de voz no está disponible en este dispositivo.")
            return
        }
        stopSpeech()
        recognizerHandler.removeCallbacksAndMessages(null)
        destroyRecognizerOnly()
        manualStopRequested = false
        busyRetryCount = 0
        startRecognizerInternal(isWakeCheck)
    }

    private fun startRecognizerInternal(isWakeCheck: Boolean) {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                busyRetryCount = 0
                onListeningStarted()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (manualStopRequested) {
                    manualStopRequested = false
                    onListeningStopped(null)
                    return
                }
                val retryable = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_AUDIO
                if (retryable && busyRetryCount < 1) {
                    busyRetryCount++
                    destroyRecognizerOnly()
                    recognizerHandler.postDelayed({
                        if (!manualStopRequested) {
                            startRecognizerInternal(isWakeCheck)
                        }
                    }, 260L)
                    return
                }
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_CLIENT -> null
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        if (isWakeCheck) null else "El micrófono estaba ocupado. Intenta tocar el orbe nuevamente."
                    SpeechRecognizer.ERROR_AUDIO ->
                        if (isWakeCheck) null else "No pude usar el micrófono en este momento. Revisa que otra app no lo esté ocupando."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER ->
                        if (isWakeCheck) null else "Sin conexión estable para dictado por voz. Puedes escribir tu mensaje abajo."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Se necesita permiso de micrófono para escucharte."
                    else -> if (isWakeCheck) null else "No se pudo captar la voz en este intento. Inténtalo de nuevo."
                }
                onListeningStopped(message)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim().orEmpty()
                onListeningStopped(null)
                if (transcript.isNotEmpty()) {
                    onFinalTranscript(transcript)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    onPartialTranscript(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val localeTag = Locale.getDefault().let { loc ->
            if (loc.language.equals("es", ignoreCase = true)) loc.toLanguageTag() else "es-419"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (isWakeCheck) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            } else {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            }
        }
        recognizer.startListening(intent)
    }

    private fun destroyRecognizerOnly() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    fun stopListening() {
        manualStopRequested = true
        recognizerHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun recordVoiceSample(
        durationMs: Long = 2600L,
        onLevelNormalized: (Float) -> Unit = {},
    ): ShortArray? = withContext(Dispatchers.IO) {
        val sampleRate = AudioFeatureExtractor.SAMPLE_RATE
        val totalSamples = ((sampleRate * durationMs) / 1000L).toInt()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return@withContext null

        var recorder: AudioRecord? = null
        var noiseSuppressor: NoiseSuppressor? = null
        var echoCanceler: AcousticEchoCanceler? = null

        return@withContext try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, 4096),
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) return@withContext null

            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = runCatching { NoiseSuppressor.create(recorder.audioSessionId) }
                    .getOrNull()?.apply { enabled = true }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = runCatching { AcousticEchoCanceler.create(recorder.audioSessionId) }
                    .getOrNull()?.apply { enabled = true }
            }

            recorder.startRecording()
            val out = ShortArray(totalSamples)
            val chunk = ShortArray(512)
            var collected = 0
            while (collected < totalSamples) {
                val toRead = minOf(chunk.size, totalSamples - collected)
                val read = recorder.read(chunk, 0, toRead)
                if (read > 0) {
                    System.arraycopy(chunk, 0, out, collected, read)
                    collected += read
                    val rms = AudioFeatureExtractor.computeRms(chunk, 0, read)
                    onLevelNormalized((rms / 4500f).coerceIn(0f, 1f))
                } else {
                    break
                }
            }
            out.copyOf(collected)
        } catch (e: Exception) {
            Log.w("VoiceController", "Failed recording voice sample: ${e.message}")
            null
        } finally {
            runCatching { noiseSuppressor?.release() }
            runCatching { echoCanceler?.release() }
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
        }
    }

    fun release() {
        stopListening()
        stopSpeech()
        ttsHandler.removeCallbacksAndMessages(null)
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
        }
    }
}
