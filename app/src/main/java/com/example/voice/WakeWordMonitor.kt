package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/**
 * Silent AudioRecord-based wake-word pre-gate monitor.
 * Never triggers system recognition beeps while waiting in standby.
 * Captures a rolling 2.5s PCM buffer so when clear human speech passes the
 * adaptive noise floor + echo guard, we also have raw audio for speaker classification.
 */
class WakeWordMonitor(
    private val scope: CoroutineScope,
    private val onSpeechCandidate: (ShortArray) -> Unit,
    private val onAudioError: (String) -> Unit,
) {
    private var monitorJob: Job? = null

    @Volatile
    private var filterMode: NoiseFilterMode = NoiseFilterMode.STRICT

    @Volatile
    private var suppressUntilMs: Long = 0L

    @Volatile
    private var isPausedForRecognizer: Boolean = false

    val isRunning: Boolean
        get() = monitorJob?.isActive == true

    fun setNoiseFilterMode(mode: NoiseFilterMode) {
        filterMode = mode
    }

    fun suppressForEcho(durationMs: Long) {
        val target = System.currentTimeMillis() + durationMs
        if (target > suppressUntilMs) {
            suppressUntilMs = target
        }
    }

    fun pauseTemporarily() {
        isPausedForRecognizer = true
        monitorJob?.cancel()
    }

    suspend fun awaitStopped(timeoutMs: Long = 450L) {
        val job = monitorJob ?: return
        withTimeoutOrNull(timeoutMs) {
            job.join()
        }
        if (monitorJob === job && !job.isActive) {
            monitorJob = null
        }
    }

    fun start() {
        isPausedForRecognizer = false
        if (monitorJob?.isActive == true) return
        launchMonitorLoop()
    }

    fun stop() {
        isPausedForRecognizer = false
        stopInternal()
    }

    private fun stopInternal() {
        monitorJob?.cancel()
        monitorJob = null
    }

    @SuppressLint("MissingPermission")
    private fun launchMonitorLoop() {
        val sampleRate = AudioFeatureExtractor.SAMPLE_RATE
        val minBufBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufBytes <= 0) {
            onAudioError("No se pudo inicializar el monitor de audio del dispositivo.")
            return
        }

        val frameSamples = 512 // 32 ms
        val ringCapacity = sampleRate * 5 / 2 // 2.5 seconds rolling PCM
        val ringBuffer = ShortArray(ringCapacity)
        var ringWriteIdx = 0
        var ringFilled = 0

        monitorJob = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            var noiseSuppressor: NoiseSuppressor? = null
            var echoCanceler: AcousticEchoCanceler? = null

            try {
                var initAttempts = 0
                while (initAttempts < 2) {
                    val candidate = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        max(minBufBytes, frameSamples * 8),
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        recorder = candidate
                        break
                    }
                    runCatching { candidate.release() }
                    initAttempts++
                    if (initAttempts < 2) delay(250L)
                }
                if (recorder == null) {
                    onAudioError("El micrófono está ocupado en este momento. Intenta activarlo en unos segundos.")
                    return@launch
                }

                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = runCatching { NoiseSuppressor.create(recorder.audioSessionId) }
                        .getOrNull()?.apply { enabled = true }
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = runCatching { AcousticEchoCanceler.create(recorder.audioSessionId) }
                        .getOrNull()?.apply { enabled = true }
                }

                recorder.startRecording()

                val frame = ShortArray(frameSamples)
                var noiseFloor = 260f
                var activeSpeechFrames = 0
                var quietGapFrames = 0
                var lastTriggerAtMs = 0L

                while (isActive && !isPausedForRecognizer) {
                    val read = recorder.read(frame, 0, frame.size)
                    if (read <= 0) {
                        delay(20L)
                        continue
                    }

                    val now = System.currentTimeMillis()
                    if (now < suppressUntilMs) {
                        activeSpeechFrames = 0
                        quietGapFrames = 0
                        delay(24L)
                        continue
                    }

                    // Write frame into ring buffer
                    for (i in 0 until read) {
                        ringBuffer[ringWriteIdx] = frame[i]
                        ringWriteIdx = (ringWriteIdx + 1) % ringCapacity
                        if (ringFilled < ringCapacity) ringFilled++
                    }

                    val rms = AudioFeatureExtractor.computeRms(frame, 0, read)
                    val zcr = AudioFeatureExtractor.computeZcr(frame, 0, read)
                    val mode = filterMode
                    val gateThreshold = max(mode.minRms, noiseFloor * mode.snrMultiplier)

                    val isSpeechLike = rms >= gateThreshold && zcr in 0.014f..0.30f

                    if (isSpeechLike) {
                        activeSpeechFrames++
                        quietGapFrames = 0
                    } else {
                        quietGapFrames++
                        // Slowly adapt background noise floor only during genuine quiet frames
                        if (rms < gateThreshold * 0.85f) {
                            noiseFloor = (noiseFloor * 0.96f + rms * 0.04f).coerceIn(140f, 1800f)
                        }
                        if (quietGapFrames > 5) {
                            activeSpeechFrames = 0
                        }
                    }

                    val cooldownElapsed = now - lastTriggerAtMs >= 2600L
                    if (activeSpeechFrames >= mode.minSpeechFrames && cooldownElapsed) {
                        if (isPausedForRecognizer || !isActive || now < suppressUntilMs) {
                            activeSpeechFrames = 0
                            continue
                        }
                        lastTriggerAtMs = now
                        activeSpeechFrames = 0
                        val snapshot = extractRingSnapshot(ringBuffer, ringWriteIdx, ringFilled, ringCapacity)
                        // Release AudioRecord before invoking SpeechRecognizer so mic hardware doesn't collide
                        isPausedForRecognizer = true
                        onSpeechCandidate(snapshot)
                        break
                    }
                }
            } catch (e: SecurityException) {
                onAudioError("Permiso de micrófono requerido para escuchar \"ADA\".")
            } catch (e: Exception) {
                Log.w("WakeWordMonitor", "Audio monitor loop stopped: ${e.message}")
            } finally {
                runCatching { noiseSuppressor?.release() }
                runCatching { echoCanceler?.release() }
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
            }
        }
    }

    private fun extractRingSnapshot(
        ring: ShortArray,
        writeIdx: Int,
        filled: Int,
        capacity: Int,
    ): ShortArray {
        val out = ShortArray(filled)
        val start = if (filled < capacity) 0 else writeIdx
        for (i in 0 until filled) {
            out[i] = ring[(start + i) % capacity]
        }
        return out
    }
}
