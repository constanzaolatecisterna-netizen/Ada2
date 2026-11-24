package com.example.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
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
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingAutoListen = false
    private var activeUtteranceId: String? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var manualStopRequested = false
    private var busyRetryCount = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
                val localeResult = engine.setLanguage(Locale.forLanguageTag("es-ES"))
                ttsReady = localeResult != TextToSpeech.LANG_MISSING_DATA &&
                    localeResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) {
                    engine.setLanguage(Locale.getDefault())
                    ttsReady = true
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            isSpeakingNow = true
                            onSpeechStarted()
                        }
                    }

                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        if (utteranceId == activeUtteranceId) {
                            onSpeechWordPulse()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            isSpeakingNow = false
                            lastSpeechEndedAtMs = System.currentTimeMillis()
                            val autoListen = pendingAutoListen
                            pendingAutoListen = false
                            activeUtteranceId = null
                            onSpeechFinished(autoListen)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == activeUtteranceId) {
                            isSpeakingNow = false
                            lastSpeechEndedAtMs = System.currentTimeMillis()
                            pendingAutoListen = false
                            activeUtteranceId = null
                            onSpeechFinished(false)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == activeUtteranceId) {
                            isSpeakingNow = false
                            lastSpeechEndedAtMs = System.currentTimeMillis()
                            pendingAutoListen = false
                            activeUtteranceId = null
                            onSpeechFinished(false)
                        }
                    }
                })
            } else {
                Log.w("VoiceController", "TTS init status=$status")
            }
        }
    }

    fun speak(text: String, autoListenAfter: Boolean) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = tts
        if (engine == null || !ttsReady) {
            onSpeechFinished(false)
            return
        }
        stopSpeech()
        pendingAutoListen = autoListenAfter
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId

        engine.setPitch(1.06f)
        engine.setSpeechRate(0.98f)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val queued = engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (queued == TextToSpeech.ERROR) {
            isSpeakingNow = false
            activeUtteranceId = null
            pendingAutoListen = false
            onSpeechFinished(false)
        } else {
            isSpeakingNow = true
        }
    }

    fun stopSpeech() {
        val wasSpeaking = isSpeakingNow
        isSpeakingNow = false
        if (wasSpeaking) {
            lastSpeechEndedAtMs = System.currentTimeMillis()
        }
        pendingAutoListen = false
        activeUtteranceId = null
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
                if ((error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) &&
                    busyRetryCount < 1
                ) {
                    busyRetryCount++
                    destroyRecognizerOnly()
                    mainHandler.postDelayed({
                        if (!manualStopRequested) {
                            startRecognizerInternal(isWakeCheck)
                        }
                    }, 220L)
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

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (isWakeCheck) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 950L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 750L)
            } else {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 950L)
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
        mainHandler.removeCallbacksAndMessages(null)
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
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
        }
    }
}
