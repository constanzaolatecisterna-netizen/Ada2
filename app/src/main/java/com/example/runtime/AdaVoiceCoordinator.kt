package com.example.runtime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.ai.AdaAiRepository
import com.example.ai.AssistantContextSnapshot
import com.example.data.AvailabilityStatus
import com.example.data.FamilyProfile
import com.example.data.nowHHMM
import com.example.notifications.AdaNotificationListenerService
import com.example.voice.AudioFeatureExtractor
import com.example.voice.NoiseFilterMode
import com.example.voice.SpeakerClassification
import com.example.voice.SpeakerClassifier
import com.example.voice.VoiceController
import com.example.voice.VoicePrintStore
import com.example.voice.WakeWordMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface AdaCommandDelegate {
    fun getProfiles(): List<FamilyProfile>
    fun getActiveProfile(): FamilyProfile?
    fun getUid(): String
    fun isSilenceMode(): Boolean
    fun onVoiceTranscriptReady(
        commandText: String,
        speaker: SpeakerClassification?,
    )
    fun onVoiceError(message: String)
    fun onVoicePrintUpdated(enrolledIds: Set<String>, profileName: String, f0Hz: Int)
}

data class AdaVoiceUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isThinking: Boolean = false,
    val speechPulseTick: Long = 0L,
    val fluidMode: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val wakeWordArmed: Boolean = false,
    val noiseFilterMode: NoiseFilterMode = NoiseFilterMode.STRICT,
    val lastSpeaker: SpeakerClassification? = null,
    val transcriptPreview: String = "",
    val responseText: String = "¡Hola! Soy ADA, tu asistente en la Red Alianza.",
    val enrollingProfileId: String? = null,
    val enrollmentAudioLevel: Float = 0f,
    val enrolledVoiceProfileIds: Set<String> = emptySet(),
    val errorText: String? = null,
)

object AdaVoiceCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _voiceState = MutableStateFlow(AdaVoiceUiState())
    val voiceState: StateFlow<AdaVoiceUiState> = _voiceState.asStateFlow()

    private val aiRepository by lazy { AdaAiRepository() }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var delegate: AdaCommandDelegate? = null

    @Volatile
    private var voiceController: VoiceController? = null

    @Volatile
    private var wakeWordMonitor: WakeWordMonitor? = null

    @Volatile
    private var currentUid: String = ""

    @Volatile
    private var pendingAudioSample: ShortArray? = null

    @Volatile
    private var pendingWakeVerification: Boolean = false

    private var autoListenJob: Job? = null
    private var wakeResumeJob: Job? = null

    private fun ensureInitialized(context: Context) {
        val appCtx = context.applicationContext
        if (appContext == null) {
            appContext = appCtx
        }
        if (wakeWordMonitor == null) {
            wakeWordMonitor = WakeWordMonitor(
                scope = scope,
                onSpeechCandidate = { pcmWindow ->
                    scope.launch { handleWakeSpeechCandidate(appCtx, pcmWindow) }
                },
                onAudioError = { msg ->
                    scope.launch {
                        updateState(appCtx) {
                            it.copy(
                                wakeWordEnabled = false,
                                wakeWordArmed = false,
                                errorText = msg,
                            )
                        }
                        delegate?.onVoiceError(msg)
                    }
                },
            )
        }
        if (voiceController == null) {
            voiceController = VoiceController(
                context = appCtx,
                onSpeechStarted = {
                    wakeWordMonitor?.pauseTemporarily()
                    wakeWordMonitor?.suppressForEcho(60_000L)
                    scope.launch {
                        updateState(appCtx) {
                            it.copy(
                                isSpeaking = true,
                                wakeWordArmed = false,
                                speechPulseTick = it.speechPulseTick + 1L,
                            )
                        }
                    }
                },
                onSpeechWordPulse = {
                    scope.launch {
                        updateState(appCtx) {
                            it.copy(speechPulseTick = it.speechPulseTick + 1L)
                        }
                    }
                },
                onSpeechFinished = { autoListen ->
                    wakeWordMonitor?.suppressForEcho(1_800L)
                    scope.launch {
                        updateState(appCtx) { it.copy(isSpeaking = false) }
                        if (autoListen) {
                            scheduleFollowUpListen(appCtx)
                        } else {
                            scheduleWakeWordResume(appCtx, delayMs = 1_800L)
                        }
                    }
                },
                onListeningStarted = {
                    scope.launch {
                        updateState(appCtx) {
                            it.copy(
                                isListening = true,
                                wakeWordArmed = false,
                                transcriptPreview = "",
                            )
                        }
                    }
                },
                onPartialTranscript = { partial ->
                    scope.launch {
                        updateState(appCtx) { it.copy(transcriptPreview = partial) }
                    }
                },
                onFinalTranscript = { transcript ->
                    scope.launch {
                        handleRecognizedTranscript(appCtx, transcript)
                    }
                },
                onListeningStopped = { errorMessage ->
                    scope.launch {
                        pendingWakeVerification = false
                        pendingAudioSample = null
                        updateState(appCtx) {
                            it.copy(
                                isListening = false,
                                errorText = errorMessage ?: it.errorText,
                            )
                        }
                        if (!errorMessage.isNullOrBlank()) {
                            delegate?.onVoiceError(errorMessage)
                        }
                        if (voiceController?.isSpeakingNow != true && !_voiceState.value.isSpeaking) {
                            scheduleWakeWordResume(appCtx, delayMs = 1_500L)
                        }
                    }
                },
            )
        }
    }

    fun attachDelegate(context: Context, commandDelegate: AdaCommandDelegate) {
        ensureInitialized(context)
        delegate = commandDelegate
        currentUid = commandDelegate.getUid()
        refreshEnrolledIds(context)
    }

    fun detachDelegate(commandDelegate: AdaCommandDelegate) {
        if (delegate === commandDelegate) {
            delegate = null
            // If floating ADA isn't running when ViewModel clears, stop background mic
            if (!AdaRuntimeHub.state.value.floatingActive) {
                appContext?.let { setWakeWordEnabled(it, false) }
            }
        }
    }

    fun refreshEnrolledIds(context: Context) {
        val uid = resolveUid(context)
        if (uid.isBlank()) return
        val ids = VoicePrintStore(context, uid).loadAll().map { it.profileId }.toSet()
        updateState(context) { it.copy(enrolledVoiceProfileIds = ids) }
    }

    fun onFloatingServiceStarted(context: Context) {
        ensureInitialized(context)
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            setWakeWordEnabled(context, true)
        }
    }

    fun onFloatingServiceStopped(context: Context) {
        if (delegate == null) {
            setWakeWordEnabled(context, false)
        }
    }

    fun setWakeWordEnabled(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        wakeResumeJob?.cancel()
        val monitor = wakeWordMonitor ?: return
        if (enabled) {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                val msg = "Se necesita permiso de micrófono para escuchar \"ADA\"."
                updateState(context) { it.copy(wakeWordEnabled = false, wakeWordArmed = false, errorText = msg) }
                delegate?.onVoiceError(msg)
                return
            }
            updateState(context) { it.copy(wakeWordEnabled = true, errorText = null) }
            monitor.setNoiseFilterMode(_voiceState.value.noiseFilterMode)
            if (!_voiceState.value.isListening && !_voiceState.value.isSpeaking) {
                monitor.start()
                updateState(context) { it.copy(wakeWordArmed = true) }
            }
        } else {
            monitor.stop()
            updateState(context) { it.copy(wakeWordEnabled = false, wakeWordArmed = false) }
        }
    }

    fun setNoiseFilterMode(context: Context, mode: NoiseFilterMode) {
        ensureInitialized(context)
        wakeWordMonitor?.setNoiseFilterMode(mode)
        updateState(context) { it.copy(noiseFilterMode = mode) }
    }

    fun toggleFluidMode(context: Context) {
        ensureInitialized(context)
        updateState(context) { it.copy(fluidMode = !it.fluidMode) }
    }

    fun clearError(context: Context) {
        updateState(context) { it.copy(errorText = null) }
    }

    fun startDirectListening(context: Context) {
        ensureInitialized(context)
        autoListenJob?.cancel()
        wakeResumeJob?.cancel()
        val vc = voiceController ?: return
        if (_voiceState.value.isListening) {
            vc.stopListening()
            updateState(context) { it.copy(isListening = false) }
            scheduleWakeWordResume(context, delayMs = 1_200L)
            return
        }
        if (_voiceState.value.isSpeaking) {
            stopSpeech(context)
        }
        val wasMonitorActive = wakeWordMonitor?.isRunning == true
        wakeWordMonitor?.pauseTemporarily()
        pendingWakeVerification = false
        pendingAudioSample = null
        updateState(context) { it.copy(wakeWordArmed = false, errorText = null) }
        if (wasMonitorActive) {
            scope.launch {
                wakeWordMonitor?.awaitStopped(350L)
                delay(80L)
                vc.startListening(isWakeCheck = false)
            }
        } else {
            vc.startListening(isWakeCheck = false)
        }
    }

    fun speak(
        context: Context,
        text: String,
        autoListenAfter: Boolean = false,
        interrupt: Boolean = true,
    ) {
        ensureInitialized(context)
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (delegate?.isSilenceMode() == true) {
            updateState(context) { it.copy(responseText = clean) }
            return
        }
        autoListenJob?.cancel()
        wakeResumeJob?.cancel()
        wakeWordMonitor?.pauseTemporarily()
        wakeWordMonitor?.suppressForEcho(60_000L)
        updateState(context) {
            it.copy(
                responseText = clean,
                isSpeaking = true,
                wakeWordArmed = false,
            )
        }
        voiceController?.speak(clean, autoListenAfter = autoListenAfter, interrupt = interrupt)
    }

    fun stopSpeech(context: Context) {
        ensureInitialized(context)
        autoListenJob?.cancel()
        wakeResumeJob?.cancel()
        voiceController?.stopSpeech()
        wakeWordMonitor?.suppressForEcho(1_800L)
        updateState(context) { it.copy(isSpeaking = false) }
        scheduleWakeWordResume(context, delayMs = 1_800L)
    }

    fun enrollVoicePrint(context: Context, profile: FamilyProfile) {
        ensureInitialized(context)
        if (_voiceState.value.enrollingProfileId != null) return
        val uid = resolveUid(context)
        if (uid.isBlank()) return

        wakeResumeJob?.cancel()
        wakeWordMonitor?.pauseTemporarily()
        voiceController?.stopListening()
        voiceController?.stopSpeech()

        scope.launch {
            updateState(context) {
                it.copy(
                    enrollingProfileId = profile.id,
                    enrollmentAudioLevel = 0f,
                    wakeWordArmed = false,
                    errorText = null,
                )
            }
            val pcm = voiceController?.recordVoiceSample(durationMs = 2_600L) { level ->
                scope.launch {
                    updateState(context) { it.copy(enrollmentAudioLevel = level) }
                }
            }
            val features = pcm?.let { AudioFeatureExtractor.extractFeatures(it) }
            if (features == null) {
                val msg = "No capté suficiente voz clara para ${profile.name}. Habla 2 segundos cerca del micrófono."
                updateState(context) {
                    it.copy(
                        enrollingProfileId = null,
                        enrollmentAudioLevel = 0f,
                        errorText = msg,
                    )
                }
                delegate?.onVoiceError(msg)
                scheduleWakeWordResume(context, delayMs = 1_200L)
                return@launch
            }

            val updatedPrints = VoicePrintStore(context, uid).saveEnrollment(
                profileId = profile.id,
                profileName = profile.name,
                features = features,
            )
            val enrolledIds = updatedPrints.map { it.profileId }.toSet()
            updateState(context) {
                it.copy(
                    enrollingProfileId = null,
                    enrollmentAudioLevel = 0f,
                    enrolledVoiceProfileIds = enrolledIds,
                )
            }
            delegate?.onVoicePrintUpdated(enrolledIds, profile.name, features.f0Hz.toInt())
        }
    }

    fun clearVoicePrint(context: Context, profileId: String) {
        val uid = resolveUid(context)
        if (uid.isBlank()) return
        val updated = VoicePrintStore(context, uid).removeEnrollment(profileId)
        updateState(context) {
            it.copy(enrolledVoiceProfileIds = updated.map { p -> p.profileId }.toSet())
        }
    }

    private fun handleWakeSpeechCandidate(context: Context, pcmWindow: ShortArray) {
        val cur = _voiceState.value
        val vc = voiceController ?: return
        if (!cur.wakeWordEnabled || cur.isSpeaking || cur.isThinking || cur.isListening || cur.enrollingProfileId != null) {
            return
        }
        val echoElapsed = System.currentTimeMillis() - vc.lastSpeechEndedAtMs
        if (vc.isSpeakingNow || echoElapsed < 1_800L) {
            scheduleWakeWordResume(context, delayMs = 1_200L)
            return
        }
        pendingAudioSample = pcmWindow
        pendingWakeVerification = true
        updateState(context) { it.copy(wakeWordArmed = false) }
        scope.launch {
            wakeWordMonitor?.awaitStopped(400L)
            delay(120L)
            val now = _voiceState.value
            if (now.wakeWordEnabled && !now.isSpeaking && !vc.isSpeakingNow && !now.isThinking && !now.isListening) {
                vc.startListening(isWakeCheck = true)
            }
        }
    }

    private fun handleRecognizedTranscript(context: Context, transcript: String) {
        val wasWakeCheck = pendingWakeVerification
        val audioSample = pendingAudioSample
        pendingWakeVerification = false
        pendingAudioSample = null

        val profiles = delegate?.getProfiles() ?: AdaRuntimeHub.state.value.profiles
        val uid = resolveUid(context)
        val voicePrints = if (uid.isNotBlank()) VoicePrintStore(context, uid).loadAll() else emptyList()

        val speaker = SpeakerClassifier.classify(
            samples = audioSample,
            transcript = transcript,
            profiles = profiles,
            voicePrints = voicePrints,
        )

        if (wasWakeCheck) {
            val wake = SpeakerClassifier.parseWakeWord(transcript)
            if (!wake.triggered) {
                // Silently discard ambient chatter / TV without responding or making noise
                updateState(context) { it.copy(transcriptPreview = "") }
                scheduleWakeWordResume(context, delayMs = 1_900L)
                return
            }

            if (speaker != null) {
                updateState(context) { it.copy(lastSpeaker = speaker) }
            }

            if (wake.commandText.isBlank()) {
                // User called "ADA" directly -> answer aloud immediately & listen for their command!
                val speakerName = speaker?.profileId?.let { id ->
                    profiles.firstOrNull { it.id == id }?.name
                }
                val replyGreeting = if (!speakerName.isNullOrBlank()) {
                    "Te escucho, $speakerName. ¿En qué te ayudo?"
                } else {
                    "Aquí estoy, ¿en qué te ayudo?"
                }
                updateState(context) {
                    it.copy(
                        transcriptPreview = "ADA activada",
                        responseText = replyGreeting,
                    )
                }
                speak(context, replyGreeting, autoListenAfter = true)
                return
            }

            updateState(context) { it.copy(transcriptPreview = wake.commandText) }
            executeRecognizedCommand(context, wake.commandText, speaker)
        } else {
            if (speaker != null) {
                updateState(context) { it.copy(lastSpeaker = speaker) }
            }
            updateState(context) { it.copy(transcriptPreview = transcript) }
            executeRecognizedCommand(context, transcript, speaker)
        }
    }

    private fun executeRecognizedCommand(
        context: Context,
        commandText: String,
        speaker: SpeakerClassification?,
    ) {
        val activeDelegate = delegate
        if (activeDelegate != null) {
            activeDelegate.onVoiceTranscriptReady(commandText, speaker)
            return
        }
        // Standalone mode (when MainActivity is closed & Floating ADA is active)
        handleStandaloneVoiceCommand(context, commandText, speaker)
    }

    private fun handleStandaloneVoiceCommand(
        context: Context,
        commandText: String,
        speaker: SpeakerClassification?,
    ) {
        val runtime = AdaRuntimeHub.state.value
        val cached = AdaRuntimeHub.loadCachedSummary(context)
        val intent = com.example.ai.LocalCommandMatcher.match(commandText)

        when (intent) {
            is com.example.ai.LocalAssistantIntent.ReadNotifications -> {
                val nativeCaptured = AdaNotificationListenerService.capturedAlerts.value
                val lines = when {
                    runtime.alerts.isNotEmpty() -> runtime.alerts.takeLast(4).map { "De ${it.app}: ${it.text}" }
                    nativeCaptured.isNotEmpty() -> nativeCaptured.takeLast(4).map { "De ${it.app}: ${it.text}" }
                    else -> cached.recentAlertLines
                }
                val msg = if (lines.isEmpty()) {
                    "No tienes notificaciones nuevas en este momento."
                } else {
                    "Tus notificaciones recientes son: ${lines.joinToString(". ")}."
                }
                speak(context, msg, autoListenAfter = false)
                return
            }
            is com.example.ai.LocalAssistantIntent.ReadAllianceMessages -> {
                val lines = if (runtime.messages.isNotEmpty()) {
                    runtime.messages.takeLast(4).map { "${it.fromName} para ${it.toName}: ${it.text}" }
                } else {
                    cached.recentMessageLines
                }
                val msg = if (lines.isEmpty()) {
                    "No hay mensajes recientes en la Red Alianza."
                } else {
                    "Mensajes de la Red Alianza: ${lines.joinToString(". ")}."
                }
                speak(context, msg, autoListenAfter = false)
                return
            }
            is com.example.ai.LocalAssistantIntent.CheckWeather -> {
                val summary = runtime.weatherSummary.ifBlank { "Clima local disponible dentro de la app" }
                speak(context, "El clima actual es: $summary.", autoListenAfter = false)
                return
            }
            is com.example.ai.LocalAssistantIntent.CheckTime -> {
                speak(context, "Son las ${nowHHMM()}.", autoListenAfter = false)
                return
            }
            is com.example.ai.LocalAssistantIntent.CheckDate -> {
                speak(
                    context,
                    "Hoy es ${com.example.ai.LocalCommandMatcher.formatCurrentSpanishDate()} y son las ${nowHHMM()}.",
                    autoListenAfter = false,
                )
                return
            }
            is com.example.ai.LocalAssistantIntent.FamilyStatus -> {
                val family = runtime.profiles.joinToString(". ") { "${it.name} está ${it.availability}" }
                val msg = if (family.isBlank()) {
                    "Abre la app para ver el estado actualizado de tu familia."
                } else {
                    "Estado de la familia: $family."
                }
                speak(context, msg, autoListenAfter = false)
                return
            }
            is com.example.ai.LocalAssistantIntent.UtilityAnswer -> {
                speak(context, intent.answer, autoListenAfter = false)
                return
            }
            is com.example.ai.LocalAssistantIntent.WhoIsSpeaking -> {
                val msg = if (speaker != null) {
                    "Te identifico como ${speaker.displayName} con ${speaker.confidencePercent}% de coincidencia."
                } else {
                    "Perfil activo: ${cached.activeProfileName}."
                }
                speak(context, msg, autoListenAfter = false)
                return
            }
            is com.example.ai.LocalAssistantIntent.HowAreYou -> {
                speak(context, "¡Todo en orden! Aquí sigo escuchando desde el widget flotante.", autoListenAfter = true)
                return
            }
            is com.example.ai.LocalAssistantIntent.Greeting -> {
                val name = speaker?.profileId?.let { id ->
                    runtime.profiles.firstOrNull { it.id == id }?.name
                } ?: cached.activeProfileName
                speak(context, "¡Hola $name! Aquí estoy contigo en la pantalla. ¿Qué necesitas?", autoListenAfter = true)
                return
            }
            is com.example.ai.LocalAssistantIntent.Thanks -> {
                speak(context, "Con gusto, aquí sigo pendiente.", autoListenAfter = false)
                return
            }
            is com.example.ai.LocalAssistantIntent.Help -> {
                speak(
                    context,
                    "Puedo leer tus notificaciones y WhatsApp, revisar mensajes de la familia, resolver cálculos, decirte hora, fecha o clima, y buscar información abierta.",
                    autoListenAfter = false,
                )
                return
            }
            else -> Unit
        }

        // Open-knowledge & calculation fallback in standalone floating mode (without Gemini)
        updateState(context) { it.copy(isThinking = true, errorText = null) }
        scope.launch {
            val speakerLabel = speaker?.let { "${it.displayName} (${it.confidencePercent}%)" }
            val snapshot = AssistantContextSnapshot(
                activeName = cached.activeProfileName,
                activeRole = "Familia",
                availability = AvailabilityStatus.DISPONIBLE.label,
                weather = runtime.weatherSummary,
                currentTime = nowHHMM(),
                pendingRoutines = emptyList(),
                reminders = emptyList(),
                unreadAlliance = cached.recentMessageLines,
                notifications = cached.recentAlertLines,
                familyMembers = runtime.profiles.map { "${it.name} (${it.role})" },
                contacts = emptyList(),
                detectedSpeaker = speakerLabel,
            )
            aiRepository.generateAssistantReply(
                userMessage = commandText,
                history = emptyList(),
                contextSnapshot = snapshot,
            ).onSuccess { structured ->
                updateState(context) { it.copy(isThinking = false) }
                when (structured.actionType) {
                    "read_notifications" -> handleStandaloneVoiceCommand(context, "leer notificaciones", speaker)
                    else -> speak(context, structured.reply, autoListenAfter = false)
                }
            }.onFailure { err ->
                updateState(context) {
                    it.copy(
                        isThinking = false,
                        errorText = err.message ?: "No pude consultar esa información ahora mismo.",
                    )
                }
                speak(context, err.message ?: "No pude consultar esa información ahora mismo.", autoListenAfter = false)
            }
        }
    }

    private fun scheduleFollowUpListen(context: Context) {
        autoListenJob?.cancel()
        wakeResumeJob?.cancel()
        val vc = voiceController ?: return
        wakeWordMonitor?.pauseTemporarily()
        autoListenJob = scope.launch {
            wakeWordMonitor?.awaitStopped(350L)
            // Brief echo guard after short prompt so mic doesn't hear ADA's own TTS
            delay(950L)
            val now = _voiceState.value
            if (!now.isSpeaking && !vc.isSpeakingNow && !now.isListening && !now.isThinking && now.enrollingProfileId == null) {
                pendingWakeVerification = false
                pendingAudioSample = null
                vc.startListening(isWakeCheck = false)
            }
        }
    }

    private fun scheduleWakeWordResume(context: Context, delayMs: Long = 1_800L) {
        wakeResumeJob?.cancel()
        val state = _voiceState.value
        if (!state.wakeWordEnabled || state.isListening || state.isSpeaking || voiceController?.isSpeakingNow == true || state.enrollingProfileId != null) {
            return
        }
        wakeResumeJob = scope.launch {
            delay(delayMs)
            val now = _voiceState.value
            if (now.wakeWordEnabled && !now.isListening && !now.isSpeaking && voiceController?.isSpeakingNow != true && !now.isThinking && now.enrollingProfileId == null) {
                wakeWordMonitor?.setNoiseFilterMode(now.noiseFilterMode)
                wakeWordMonitor?.start()
                updateState(context) { it.copy(wakeWordArmed = true) }
            }
        }
    }

    private fun resolveUid(context: Context): String {
        return delegate?.getUid()?.takeIf { it.isNotBlank() }
            ?: currentUid.takeIf { it.isNotBlank() }
            ?: AdaRuntimeHub.state.value.uid.takeIf { it.isNotBlank() }
            ?: AdaRuntimeHub.loadCachedSummary(context).uid
    }

    private fun updateState(
        context: Context,
        transform: (AdaVoiceUiState) -> AdaVoiceUiState,
    ) {
        val next = transform(_voiceState.value)
        _voiceState.value = next
        AdaRuntimeHub.updateState(context) { cur ->
            cur.copy(
                isListening = next.isListening,
                isSpeaking = next.isSpeaking,
                isThinking = next.isThinking,
                speechPulseTick = next.speechPulseTick,
                wakeWordEnabled = next.wakeWordEnabled,
                wakeWordArmed = next.wakeWordArmed,
                noiseFilterMode = next.noiseFilterMode,
                lastSpeaker = next.lastSpeaker ?: cur.lastSpeaker,
                transcriptPreview = next.transcriptPreview,
                responseText = next.responseText,
            )
        }
    }
}
