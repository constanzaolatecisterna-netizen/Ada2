package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AdaAiRepository
import com.example.ai.AssistantContextSnapshot
import com.example.ai.LocalAssistantIntent
import com.example.ai.LocalCommandMatcher
import com.example.data.AlianzaRepository
import com.example.data.AllianceMessage
import com.example.data.AllianceRule
import com.example.data.AvailabilityStatus
import com.example.data.ChatTurn
import com.example.data.CommChannel
import com.example.data.ContactItem
import com.example.data.DefaultSeed
import com.example.data.FamilyProfile
import com.example.data.HubTab
import com.example.data.OrbCheckPulse
import com.example.data.OrbVisualMode
import com.example.data.PushAlert
import com.example.data.PushWebhookEndpoint
import com.example.data.QuickComm
import com.example.data.ReminderItem
import com.example.data.RoutineItem
import com.example.data.RuleTrigger
import com.example.data.UiState
import com.example.data.UserNetworkConfig
import com.example.data.WeatherRepository
import com.example.data.nowHHMM
import com.example.notifications.AdaNotificationListenerService
import com.example.notifications.ReminderNotifier
import com.example.runtime.AdaCommandDelegate
import com.example.runtime.AdaExternalAction
import com.example.runtime.AdaRuntimeHub
import com.example.runtime.AdaVoiceCoordinator
import com.example.voice.NoiseFilterMode
import com.example.voice.SpeakerClassification
import com.example.voice.SpeakerClassifier
import com.example.voice.VoicePrintStore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AlianzaCloudSnapshot(
    val networkCode: String = DefaultSeed.DEFAULT_NETWORK_CODE,
    val activeProfileId: String = DefaultSeed.DEFAULT_ACTIVE_PROFILE_ID,
    val profiles: List<FamilyProfile> = emptyList(),
    val routines: List<RoutineItem> = emptyList(),
    val reminders: List<ReminderItem> = emptyList(),
    val contacts: List<ContactItem> = emptyList(),
    val messages: List<AllianceMessage> = emptyList(),
    val rules: List<AllianceRule> = emptyList(),
    val alerts: List<PushAlert> = emptyList(),
    val webhooks: List<PushWebhookEndpoint> = emptyList(),
)

data class AlianzaLocalUiState(
    val orbMode: OrbVisualMode = OrbVisualMode.IDLE,
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val speechPulseTick: Long = 0L,
    val fluidMode: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val wakeWordArmed: Boolean = false,
    val noiseFilterMode: NoiseFilterMode = NoiseFilterMode.STRICT,
    val lastSpeaker: SpeakerClassification? = null,
    val enrollingProfileId: String? = null,
    val enrollmentAudioLevel: Float = 0f,
    val enrolledVoiceProfileIds: Set<String> = emptySet(),
    val floatingActive: Boolean = false,
    val checkPulse: OrbCheckPulse? = null,
    val transcriptPreview: String = "",
    val responseText: String = "¡Hola! Soy ADA, tu asistente en la Red Alianza.",
    val errorText: String? = null,
    val chatHistory: List<ChatTurn> = emptyList(),
    val quickComm: QuickComm? = null,
    val weatherSummary: String = "Clima local · 21°C",
    val weatherDetail: String = "Consultando temperatura local...",
    val calmSeconds: Int = 120,
    val calmRunning: Boolean = false,
    val selectedTab: HubTab = HubTab.ALIANZA,
    val bannerDismissed: Boolean = false,
    val nativeNotificationAccess: Boolean = false,
    val autoReadWhatsApp: Boolean = true,
)

data class AlianzaUiModel(
    val cloudState: UiState<AlianzaCloudSnapshot> = UiState.Loading,
    val local: AlianzaLocalUiState = AlianzaLocalUiState(),
)

class AlianzaViewModel(
    application: Application,
    private val uid: String,
    private val repository: AlianzaRepository,
    private val aiRepository: AdaAiRepository,
    private val weatherRepository: WeatherRepository,
) : AndroidViewModel(application) {

    class Factory(
        private val application: Application,
        private val uid: String,
        private val repository: AlianzaRepository,
        private val aiRepository: AdaAiRepository = AdaAiRepository(),
        private val weatherRepository: WeatherRepository = WeatherRepository(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AlianzaViewModel(application, uid, repository, aiRepository, weatherRepository) as T
        }
    }

    private val voicePrintStore = VoicePrintStore(application, uid)

    private val _localState = MutableStateFlow(
        AlianzaLocalUiState(
            nativeNotificationAccess = AdaNotificationListenerService.hasNotificationAccess(application),
            autoReadWhatsApp = AdaNotificationListenerService.isAutoReadWhatsAppEnabled(application),
            enrolledVoiceProfileIds = voicePrintStore.loadAll().map { it.profileId }.toSet(),
            floatingActive = AdaRuntimeHub.state.value.floatingActive,
            wakeWordEnabled = AdaVoiceCoordinator.voiceState.value.wakeWordEnabled,
            wakeWordArmed = AdaVoiceCoordinator.voiceState.value.wakeWordArmed,
            noiseFilterMode = AdaVoiceCoordinator.voiceState.value.noiseFilterMode,
            fluidMode = AdaVoiceCoordinator.voiceState.value.fluidMode,
            lastSpeaker = AdaVoiceCoordinator.voiceState.value.lastSpeaker ?: AdaRuntimeHub.state.value.lastSpeaker,
        )
    )

    private var calmJob: Job? = null
    private var pulseJob: Job? = null
    private var lastSeenCoordinatorResponse: String = AdaVoiceCoordinator.voiceState.value.responseText
    private var lastSeenCoordinatorError: String? = AdaVoiceCoordinator.voiceState.value.errorText
    private val firedReminders = mutableSetOf<String>()
    private val syncedNativeAlertIds = mutableSetOf<String>()
    private val spokenWhatsAppAlertIds = mutableSetOf<String>()

    private val commandDelegate = object : AdaCommandDelegate {
        override fun getProfiles(): List<FamilyProfile> = currentSnapshot()?.profiles.orEmpty()

        override fun getActiveProfile(): FamilyProfile? = currentProfile()

        override fun getUid(): String = uid

        override fun isSilenceMode(): Boolean =
            currentProfile()?.availabilityStatus == AvailabilityStatus.EN_SILENCIO

        override fun onVoiceTranscriptReady(
            commandText: String,
            speaker: SpeakerClassification?,
        ) {
            submitCommand(commandText, fromVoice = true, detectedSpeaker = speaker)
        }

        override fun onVoiceError(message: String) {
            updateLocal { it.copy(errorText = message) }
        }

        override fun onVoicePrintUpdated(
            enrolledIds: Set<String>,
            profileName: String,
            f0Hz: Int,
        ) {
            updateLocal { it.copy(enrolledVoiceProfileIds = enrolledIds) }
            triggerOrbCheck("Huella: $profileName")
            reply("Huella vocal de $profileName guardada ($f0Hz Hz).", speak = true)
        }
    }

    private val cloudPrimaryFlow: Flow<Pair<UserNetworkConfig?, QuadLists>> = combine(
        repository.observeNetworkConfig(uid).safeCatch("networkConfig"),
        repository.observeProfiles(uid).safeCatch("profiles"),
        repository.observeRoutines(uid).safeCatch("routines"),
        repository.observeReminders(uid).safeCatch("reminders"),
        repository.observeContacts(uid).safeCatch("contacts"),
    ) { config, profiles, routines, reminders, contacts ->
        config to QuadLists(profiles, routines, reminders, contacts)
    }

    private val cloudSecondaryFlow: Flow<SecondaryLists> = combine(
        repository.observeMessages(uid).safeCatch("messages"),
        repository.observeRules(uid).safeCatch("rules"),
        repository.observeAlerts(uid).safeCatch("alerts"),
        repository.observeWebhooks(uid).safeCatch("webhooks"),
    ) { messages, rules, alerts, webhooks ->
        SecondaryLists(messages, rules, alerts, webhooks)
    }

    private val _cloudError = MutableStateFlow<String?>(null)

    private val cloudUiStateFlow: StateFlow<UiState<AlianzaCloudSnapshot>> = combine(
        cloudPrimaryFlow,
        cloudSecondaryFlow,
        _cloudError,
    ) { (config, primary), secondary, err ->
        if (err != null && primary.profiles.isEmpty()) {
            UiState.Error(err)
        } else if (primary.profiles.isEmpty()) {
            UiState.Loading
        } else {
            val activeId = config?.activeProfileId?.takeIf { id ->
                primary.profiles.any { it.id == id }
            } ?: primary.profiles.first().id
            UiState.Success(
                AlianzaCloudSnapshot(
                    networkCode = config?.networkCode ?: DefaultSeed.DEFAULT_NETWORK_CODE,
                    activeProfileId = activeId,
                    profiles = primary.profiles,
                    routines = primary.routines,
                    reminders = primary.reminders,
                    contacts = primary.contacts,
                    messages = secondary.messages,
                    rules = secondary.rules,
                    alerts = secondary.alerts,
                    webhooks = secondary.webhooks,
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = UiState.Loading,
    )

    val uiState: StateFlow<AlianzaUiModel> = combine(
        cloudUiStateFlow,
        _localState,
    ) { cloud, local ->
        AlianzaUiModel(cloudState = cloud, local = local)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AlianzaUiModel(),
    )

    init {
        AdaRuntimeHub.hasActiveViewModel = true
        AdaVoiceCoordinator.attachDelegate(getApplication(), commandDelegate)
        viewModelScope.launch {
            val seedRes = repository.ensureInitialSeed(uid)
            seedRes.onFailure { e ->
                _cloudError.value = e.message ?: "No se pudo inicializar la red familiar en Firestore."
            }
        }
        refreshWeather(speakResult = false)
        startReminderTicker()
        observeSystemNotifications()
        observeVoiceCoordinator()
        observeRuntimeHubBridge()
    }

    private fun observeVoiceCoordinator() {
        viewModelScope.launch {
            AdaVoiceCoordinator.voiceState.collect { voice ->
                updateLocal { cur ->
                    val nextMode = when {
                        voice.isListening -> OrbVisualMode.LISTENING
                        voice.isSpeaking -> OrbVisualMode.SPEAKING
                        cur.calmRunning -> OrbVisualMode.CALM
                        cur.orbMode == OrbVisualMode.ALERT -> OrbVisualMode.ALERT
                        else -> OrbVisualMode.IDLE
                    }
                    val respChanged = voice.responseText.isNotBlank() &&
                        voice.responseText != lastSeenCoordinatorResponse
                    if (respChanged) {
                        lastSeenCoordinatorResponse = voice.responseText
                    }
                    val errChanged = voice.errorText != lastSeenCoordinatorError
                    if (errChanged) {
                        lastSeenCoordinatorError = voice.errorText
                    }
                    cur.copy(
                        isListening = voice.isListening,
                        isSpeaking = voice.isSpeaking,
                        speechPulseTick = voice.speechPulseTick,
                        fluidMode = voice.fluidMode,
                        wakeWordEnabled = voice.wakeWordEnabled,
                        wakeWordArmed = voice.wakeWordArmed,
                        noiseFilterMode = voice.noiseFilterMode,
                        lastSpeaker = voice.lastSpeaker ?: cur.lastSpeaker,
                        transcriptPreview = voice.transcriptPreview,
                        responseText = if (respChanged) voice.responseText else cur.responseText,
                        enrollingProfileId = voice.enrollingProfileId,
                        enrollmentAudioLevel = voice.enrollmentAudioLevel,
                        enrolledVoiceProfileIds = voice.enrolledVoiceProfileIds.ifEmpty { cur.enrolledVoiceProfileIds },
                        orbMode = nextMode,
                        errorText = if (errChanged && !voice.errorText.isNullOrBlank()) voice.errorText else cur.errorText,
                    )
                }
            }
        }
    }

    private fun observeRuntimeHubBridge() {
        // Push latest cloud/app state to RuntimeHub (Floating ADA & Home Widget)
        viewModelScope.launch {
            combine(cloudUiStateFlow, _localState, AdaRuntimeHub.state) { cloud, local, hub ->
                Triple(cloud, local, hub.floatingActive)
            }.collect { (cloud, local, hubFloating) ->
                if (local.floatingActive != hubFloating) {
                    updateLocal { it.copy(floatingActive = hubFloating) }
                }
                val snap = (cloud as? UiState.Success)?.data
                val prof = snap?.let { currentProfile(it) }
                AdaRuntimeHub.updateState(getApplication()) { cur ->
                    cur.copy(
                        uid = uid,
                        activeProfileId = prof?.id ?: cur.activeProfileId,
                        activeProfileName = prof?.name ?: cur.activeProfileName,
                        profiles = snap?.profiles ?: cur.profiles,
                        messages = snap?.messages ?: cur.messages,
                        alerts = snap?.alerts ?: cur.alerts,
                        weatherSummary = local.weatherSummary,
                        lastSpeaker = local.lastSpeaker ?: cur.lastSpeaker,
                        responseText = local.responseText,
                        isThinking = local.isThinking,
                        autoReadWhatsApp = local.autoReadWhatsApp,
                    )
                }
            }
        }

        // Listen for commands from Floating ADA, Home Screen Widget, or Incoming WhatsApp notifications
        viewModelScope.launch {
            AdaRuntimeHub.actions.collect { action ->
                when (action) {
                    AdaExternalAction.StartVoice -> startVoiceListening()
                    AdaExternalAction.ReadMessages -> readAllianceMessagesAloud()
                    AdaExternalAction.ReadAlerts -> readNotificationsAloud()
                    AdaExternalAction.StopSpeech -> stopSpeech()
                    is AdaExternalAction.ToggleWakeWord -> setWakeWordEnabled(action.enabled)
                    is AdaExternalAction.SetNoiseFilter -> setNoiseFilterMode(action.mode)
                    is AdaExternalAction.ToggleAutoReadWhatsApp -> setAutoReadWhatsApp(action.enabled)
                    is AdaExternalAction.IncomingWhatsAppAlert -> handleIncomingWhatsAppAlert(action.alert)
                }
            }
        }
    }

    private fun handleIncomingWhatsAppAlert(alert: com.example.notifications.CapturedSystemAlert) {
        if (!spokenWhatsAppAlertIds.add(alert.id)) return
        if (!_localState.value.autoReadWhatsApp) return
        val prof = currentProfile()
        if (prof?.availabilityStatus == AvailabilityStatus.EN_SILENCIO) return

        triggerOrbCheck("WhatsApp entrante")
        val spoken = AdaRuntimeHub.formatWhatsAppSpokenText(alert.text)
        reply(spoken, speak = true, autoListen = false, recordTurn = false)
    }

    private fun <T> Flow<T>.safeCatch(tag: String): Flow<T> = catch { e ->
        if (e is CancellationException) throw e
        if (e is FirebaseFirestoreException) {
            Log.e("AlianzaViewModel", "Firestore stream failure on $tag: ${e.code}", e)
            _cloudError.value = "Error de sincronización Firestore (${e.code.name})."
        } else {
            Log.e("AlianzaViewModel", "Stream failure on $tag", e)
            _cloudError.value = e.message ?: "Error al sincronizar datos."
        }
    }

    private fun currentSnapshot(): AlianzaCloudSnapshot? =
        (cloudUiStateFlow.value as? UiState.Success)?.data

    private fun currentProfile(snapshot: AlianzaCloudSnapshot? = currentSnapshot()): FamilyProfile? {
        val list = snapshot?.profiles ?: return null
        return list.firstOrNull { it.id == snapshot.activeProfileId } ?: list.firstOrNull()
    }

    private fun updateLocal(transform: (AlianzaLocalUiState) -> AlianzaLocalUiState) {
        _localState.value = transform(_localState.value)
    }


    private fun observeSystemNotifications() {
        viewModelScope.launch {
            AdaNotificationListenerService.capturedAlerts.collect { captured ->
                val snapshot = currentSnapshot()
                for (entry in captured) {
                    val alreadySaved = snapshot?.alerts?.any { it.id == entry.id } == true
                    if (!alreadySaved && syncedNativeAlertIds.add(entry.id)) {
                        repository.addAlert(
                            docId = entry.id,
                            app = entry.app,
                            text = entry.text,
                            source = "native",
                        )
                        triggerOrbCheck("Alerta: ${entry.app}")
                    }
                }
            }
        }
    }

    private fun startReminderTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                val snap = currentSnapshot() ?: continue
                val prof = currentProfile(snap) ?: continue
                val hhmm = nowHHMM()
                snap.reminders
                    .filter { it.profileId == prof.id && it.time == hhmm }
                    .forEach { rem ->
                        val stamp = "${prof.id}_${rem.id}_$hhmm"
                        if (firedReminders.add(stamp)) {
                            val msg = "Recordatorio de las ${rem.time}: ${rem.text}"
                            ReminderNotifier.showReminderNotification(getApplication(), rem.time, rem.text)
                            reply(msg, speak = true, mode = OrbVisualMode.ALERT)
                        }
                    }
            }
        }
    }

    fun triggerOrbCheck(label: String) {
        pulseJob?.cancel()
        updateLocal { it.copy(checkPulse = OrbCheckPulse(System.currentTimeMillis(), label)) }
        pulseJob = viewModelScope.launch {
            delay(1250L)
            updateLocal { it.copy(checkPulse = null) }
        }
    }

    fun selectTab(tab: HubTab) {
        updateLocal { it.copy(selectedTab = tab) }
    }

    fun dismissBanner() {
        updateLocal { it.copy(bannerDismissed = true) }
    }

    fun toggleFluidMode() {
        AdaVoiceCoordinator.toggleFluidMode(getApplication())
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        AdaVoiceCoordinator.setWakeWordEnabled(getApplication(), enabled)
    }

    fun setNoiseFilterMode(mode: NoiseFilterMode) {
        AdaVoiceCoordinator.setNoiseFilterMode(getApplication(), mode)
        triggerOrbCheck("Filtro: ${mode.label}")
    }

    fun enrollVoicePrint(profileId: String) {
        val snap = currentSnapshot() ?: return
        val profile = snap.profiles.firstOrNull { it.id == profileId } ?: return
        AdaVoiceCoordinator.enrollVoicePrint(getApplication(), profile)
    }

    fun clearVoicePrint(profileId: String) {
        AdaVoiceCoordinator.clearVoicePrint(getApplication(), profileId)
    }

    fun closeQuickComm() {
        updateLocal { it.copy(quickComm = null) }
    }

    fun refreshNativeNotificationAccess() {
        val granted = AdaNotificationListenerService.hasNotificationAccess(getApplication())
        updateLocal { it.copy(nativeNotificationAccess = granted) }
    }

    fun setAutoReadWhatsApp(enabled: Boolean) {
        AdaRuntimeHub.setAutoReadWhatsApp(getApplication(), enabled)
        updateLocal { it.copy(autoReadWhatsApp = enabled) }
        triggerOrbCheck(if (enabled) "WhatsApp Auto: On" else "WhatsApp Auto: Off")
    }

    fun dismissError() {
        AdaVoiceCoordinator.clearError(getApplication())
        updateLocal { it.copy(errorText = null) }
    }

    fun startVoiceListening() {
        updateLocal { it.copy(errorText = null) }
        AdaVoiceCoordinator.startDirectListening(getApplication())
    }

    fun stopSpeech() {
        AdaVoiceCoordinator.stopSpeech(getApplication())
    }

    fun repeatSpeech() {
        if (currentProfile() == null) return
        val text = _localState.value.responseText
        reply(text, speak = true, mode = null, autoListen = false, recordTurn = false)
    }

    private fun reply(
        text: String,
        speak: Boolean = true,
        mode: OrbVisualMode? = null,
        autoListen: Boolean = false,
        recordTurn: Boolean = true,
    ) {
        val prof = currentProfile()
        updateLocal { cur ->
            val updatedHistory = if (recordTurn) {
                (cur.chatHistory + ChatTurn(
                    id = "turn_${System.currentTimeMillis()}",
                    isUser = false,
                    speaker = "ADA",
                    text = text,
                    time = nowHHMM(),
                )).takeLast(18)
            } else {
                cur.chatHistory
            }
            cur.copy(
                responseText = text,
                errorText = null,
                chatHistory = updatedHistory,
                orbMode = mode ?: cur.orbMode,
            )
        }

        if (speak && prof?.availabilityStatus != AvailabilityStatus.EN_SILENCIO) {
            val shouldAutoListen = autoListen && _localState.value.fluidMode && !_localState.value.wakeWordEnabled
            AdaVoiceCoordinator.speak(getApplication(), text, autoListenAfter = shouldAutoListen)
        } else if (mode == null) {
            updateLocal { cur ->
                cur.copy(orbMode = if (cur.calmRunning) OrbVisualMode.CALM else OrbVisualMode.IDLE)
            }
        }
    }

    fun refreshWeather(speakResult: Boolean) {
        viewModelScope.launch {
            weatherRepository.fetchWeather(getApplication())
                .onSuccess { weather ->
                    updateLocal {
                        it.copy(weatherSummary = weather.summary, weatherDetail = weather.detail)
                    }
                    if (speakResult) reply(weather.detail, speak = true)
                }
                .onFailure {
                    val fallback = _localState.value.weatherSummary
                    updateLocal { it.copy(weatherDetail = fallback) }
                    if (speakResult) reply(fallback, speak = true)
                }
        }
    }

    fun setAvailability(status: AvailabilityStatus) {
        val prof = currentProfile() ?: return
        viewModelScope.launch {
            repository.updateProfileAvailability(prof.id, status)
                .onSuccess {
                    if (status == AvailabilityStatus.OCUPADO) {
                        fireRules(RuleTrigger.STATUS_BUSY, prof.name)
                    }
                }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            repository.selectActiveProfile(profileId)
                .onSuccess {
                    val prof = currentSnapshot()?.profiles?.firstOrNull { it.id == profileId }
                    if (prof != null) {
                        reply("Perfil de ${prof.name} conectado.", speak = true)
                    }
                }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun createProfile(name: String, role: String, phone: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            repository.createProfile(name, role, phone, snap.profiles.size)
                .onSuccess {
                    reply("Perfil de $name creado y enlazado a ${snap.networkCode}.", speak = true)
                }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun deleteActiveProfile() {
        val snap = currentSnapshot() ?: return
        if (snap.profiles.size <= 1) return
        val active = currentProfile(snap) ?: return
        val fallback = snap.profiles.first { it.id != active.id }
        viewModelScope.launch {
            repository.deleteProfile(active.id, fallback.id)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun updateNetworkCode(code: String) {
        val clean = code.trim().uppercase()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.updateNetworkCode(clean)
                .onSuccess { triggerOrbCheck("Red: $clean") }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun sendAllianceMessage(to: String, text: String) {
        val prof = currentProfile() ?: return
        viewModelScope.launch {
            repository.sendMessage(prof.name, to, text, status = "Entregado")
                .onSuccess {
                    updateLocal { it.copy(bannerDismissed = false) }
                    triggerOrbCheck("Aviso enviado")
                }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun readAllianceMessagesAloud() {
        val snap = currentSnapshot() ?: return
        val prof = currentProfile(snap)
        val msgs = snap.messages
        if (msgs.isEmpty()) {
            reply("No hay mensajes recientes en la Red Alianza.", speak = true)
            return
        }
        val unreadForMe = if (prof != null) {
            msgs.filter { it.fromName != prof.name && it.status != "Leído" }
        } else {
            emptyList()
        }
        val toRead = if (unreadForMe.isNotEmpty()) unreadForMe.takeLast(4) else msgs.takeLast(4)
        val spoken = toRead.joinToString(". ") { m ->
            "${m.fromName} para ${m.toName} a las ${m.time}: ${m.text}"
        }
        reply("Mensajes de la Red Alianza: $spoken.", speak = true)
        if (unreadForMe.isNotEmpty()) {
            markAllianceMessagesRead()
        }
    }

    fun readSingleAllianceMessage(message: AllianceMessage) {
        val prof = currentProfile()
        reply(
            "Mensaje de ${message.fromName} para ${message.toName}: ${message.text}",
            speak = true,
        )
        if (prof != null && message.fromName != prof.name && message.status != "Leído") {
            markMessageRead(message.id)
        }
    }

    fun markMessageRead(messageId: String) {
        viewModelScope.launch {
            repository.markMessagesRead(listOf(messageId))
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun deleteAllianceMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun markAllianceMessagesRead() {
        val snap = currentSnapshot() ?: return
        val prof = currentProfile(snap) ?: return
        val unreadIds = snap.messages
            .filter { it.fromName != prof.name && it.status != "Leído" }
            .map { it.id }
        viewModelScope.launch {
            repository.markMessagesRead(unreadIds)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun addRule(trigger: RuleTrigger, targetProfileId: String, text: String) {
        viewModelScope.launch {
            repository.addRule(trigger, targetProfileId, text)
                .onSuccess { triggerOrbCheck("Regla guardada") }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            repository.deleteRule(ruleId)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    private fun fireRules(trigger: RuleTrigger, detail: String) {
        val snap = currentSnapshot() ?: return
        val prof = currentProfile(snap) ?: return
        val matching = snap.rules.filter { it.trigger == trigger.wireValue }
        if (matching.isEmpty()) return
        viewModelScope.launch {
            for (rule in matching) {
                val targetName = snap.profiles.firstOrNull { it.id == rule.targetProfileId }?.name
                    ?: "Toda la Familia"
                val suffix = if (detail.isNotBlank()) " ($detail)" else ""
                repository.sendMessage(
                    fromName = prof.name,
                    toName = targetName,
                    text = "${rule.text}$suffix",
                    status = "Automático",
                )
            }
        }
    }

    fun addRoutine(text: String) {
        val prof = currentProfile() ?: return
        viewModelScope.launch {
            repository.addRoutine(prof.id, text)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun toggleRoutine(item: RoutineItem) {
        val nextDone = !item.done
        viewModelScope.launch {
            repository.toggleRoutineDone(item.id, nextDone)
                .onSuccess {
                    if (nextDone) {
                        triggerOrbCheck("Rutina completada")
                        fireRules(RuleTrigger.ROUTINE_DONE, item.text)
                    }
                }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            repository.deleteRoutine(routineId)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun addReminder(time: String, text: String) {
        val prof = currentProfile() ?: return
        viewModelScope.launch {
            repository.addReminder(prof.id, time, text)
                .onSuccess { triggerOrbCheck("Recordatorio guardado") }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun addContact(alias: String, phone: String) {
        val prof = currentProfile() ?: return
        viewModelScope.launch {
            repository.addContact(prof.id, alias, phone)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            repository.deleteContact(contactId)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun addClipboardAlert(clipText: String) {
        val clean = clipText.trim()
        if (clean.isEmpty()) {
            reply("El portapapeles está vacío en este momento.", speak = true)
            return
        }
        viewModelScope.launch {
            repository.addAlert(app = "Portapapeles", text = clean, source = "clipboard")
                .onSuccess { reply("Notificación capturada: $clean", speak = true) }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun sendTestAlert() {
        viewModelScope.launch {
            repository.addAlert(
                app = "Prueba Alianza",
                text = "Alerta push de prueba sincronizada en tiempo real.",
                source = "webhook",
            )
                .onSuccess { triggerOrbCheck("Alerta de prueba") }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun deleteAlert(alertId: String) {
        viewModelScope.launch {
            repository.deleteAlert(alertId)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun clearAllAlerts() {
        val ids = currentSnapshot()?.alerts?.map { it.id } ?: return
        viewModelScope.launch {
            repository.clearAlerts(ids)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun addWebhook(name: String, url: String) {
        viewModelScope.launch {
            repository.addWebhook(name, url)
                .onSuccess { triggerOrbCheck("Webhook registrado") }
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun toggleWebhook(webhookId: String, nextActive: Boolean) {
        viewModelScope.launch {
            repository.toggleWebhook(webhookId, nextActive)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun deleteWebhook(webhookId: String) {
        viewModelScope.launch {
            repository.deleteWebhook(webhookId)
                .onFailure { e -> updateLocal { it.copy(errorText = e.message) } }
        }
    }

    fun readNotificationsAloud() {
        val snap = currentSnapshot() ?: return
        val alerts = snap.alerts
        if (alerts.isNotEmpty()) {
            val summary = alerts.takeLast(4).joinToString(". ") { "De ${it.app}: ${it.text}" }
            reply("Tus notificaciones recientes son: $summary.", speak = true)
            return
        }
        val msgs = snap.messages
        if (msgs.isNotEmpty()) {
            val spoken = msgs.takeLast(3).joinToString(". ") {
                "Mensaje de ${it.fromName} para ${it.toName}: ${it.text}"
            }
            reply(
                "No hay alertas externas recientes, pero en la Red Alianza tienes estos mensajes: $spoken.",
                speak = true
            )
            return
        }
        reply("No tienes notificaciones ni mensajes nuevos en este momento.", speak = true)
    }

    fun startCalmMode() {
        calmJob?.cancel()
        updateLocal { it.copy(orbMode = OrbVisualMode.CALM, calmRunning = true) }
        currentProfile()?.let { fireRules(RuleTrigger.CALM_STARTED, it.name) }
        calmJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val next = _localState.value.calmSeconds - 1
                if (next <= 0) {
                    updateLocal {
                        it.copy(
                            calmSeconds = 120,
                            calmRunning = false,
                            orbMode = OrbVisualMode.IDLE,
                        )
                    }
                    reply("Excelente, completaste tu pausa de calma de dos minutos.", speak = true)
                    break
                } else {
                    updateLocal { it.copy(calmSeconds = next) }
                }
            }
        }
    }

    fun resetCalmMode() {
        calmJob?.cancel()
        updateLocal {
            it.copy(
                calmRunning = false,
                calmSeconds = 120,
                orbMode = OrbVisualMode.IDLE,
            )
        }
    }

    private fun resolveContact(queryLower: String): ContactItem? {
        val snap = currentSnapshot() ?: return null
        val prof = currentProfile(snap) ?: return null
        val ownContacts = snap.contacts.filter { it.profileId == prof.id }
        for (c in ownContacts) {
            if (queryLower.contains(c.alias.lowercase())) return c
        }
        for (p in snap.profiles) {
            if (queryLower.contains(p.name.lowercase())) {
                return ownContacts.firstOrNull { it.alias.equals(p.name, ignoreCase = true) }
                    ?: ContactItem(id = p.id, profileId = prof.id, alias = p.name, phone = "")
            }
        }
        return null
    }

    private fun prepareCommunication(
        channel: CommChannel,
        targetQuery: String,
        messageBody: String = "",
    ): Boolean {
        val contact = resolveContact(targetQuery.lowercase())
        if (contact == null || contact.phone.isBlank()) {
            val label = contact?.alias ?: targetQuery.ifBlank { "ese contacto" }
            updateLocal { it.copy(selectedTab = HubTab.RUTINAS) }
            reply(
                "Aún no tengo guardado el teléfono de $label. Agrégalo en Rutinas → Contactos rápidos para conectar de inmediato.",
                speak = true
            )
            return false
        }
        val cleanDigits = contact.phone.filter { it.isDigit() }
        val uriString = if (channel == CommChannel.WHATSAPP) {
            val encoded = if (messageBody.isNotBlank()) {
                "?text=${java.net.URLEncoder.encode(messageBody, "UTF-8")}"
            } else {
                ""
            }
            "https://wa.me/$cleanDigits$encoded"
        } else {
            "tel:${contact.phone}"
        }
        updateLocal {
            it.copy(
                quickComm = QuickComm(
                    channel = channel,
                    alias = contact.alias,
                    phone = contact.phone,
                    uriString = uriString,
                )
            )
        }
        return true
    }

    fun submitCommand(
        rawText: String,
        fromVoice: Boolean = false,
        detectedSpeaker: SpeakerClassification? = null,
    ) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        val snap = currentSnapshot() ?: return
        val prof = currentProfile(snap) ?: return

        val resolvedSpeaker = detectedSpeaker ?: if (fromVoice) {
            SpeakerClassifier.classify(
                samples = null,
                transcript = text,
                profiles = snap.profiles,
                voicePrints = voicePrintStore.loadAll(),
            )
        } else {
            null
        }

        val chatSpeakerLabel = when {
            resolvedSpeaker != null && resolvedSpeaker.profileId != null ->
                "${resolvedSpeaker.displayName} (${resolvedSpeaker.confidencePercent}%)"
            resolvedSpeaker != null ->
                resolvedSpeaker.displayName
            else -> prof.name
        }

        updateLocal { cur ->
            cur.copy(
                errorText = null,
                transcriptPreview = "",
                lastSpeaker = resolvedSpeaker ?: cur.lastSpeaker,
                chatHistory = (cur.chatHistory + ChatTurn(
                    id = "user_${System.currentTimeMillis()}",
                    isUser = true,
                    speaker = chatSpeakerLabel,
                    text = text,
                    time = nowHHMM(),
                )).takeLast(18),
            )
        }

        when (val intent = LocalCommandMatcher.match(text)) {
            is LocalAssistantIntent.CallContact -> {
                val prepared = prepareCommunication(CommChannel.CALL, intent.targetQuery)
                val contact = resolveContact(text.lowercase())
                if (prepared && contact != null) {
                    reply("Preparando llamada para ${contact.alias}.", speak = true, autoListen = fromVoice)
                }
                return
            }

            is LocalAssistantIntent.WhatsAppContact -> {
                val prepared = prepareCommunication(
                    CommChannel.WHATSAPP,
                    intent.targetQuery,
                    intent.messageBody,
                )
                val contact = resolveContact(text.lowercase())
                if (prepared && contact != null) {
                    reply("Preparando WhatsApp para ${contact.alias}.", speak = true, autoListen = fromVoice)
                }
                return
            }

            is LocalAssistantIntent.AddRoutine -> {
                addRoutine(intent.text)
                reply("Rutina agregada: \"${intent.text}\".", speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.CompleteRoutine -> {
                val needle = LocalCommandMatcher.normalize(intent.query)
                val match = snap.routines.firstOrNull {
                    it.profileId == prof.id && !it.done &&
                        LocalCommandMatcher.normalize(it.text).contains(needle)
                }
                if (match != null) {
                    toggleRoutine(match)
                    reply("Excelente, marqué \"${match.text}\" como completada.", speak = true, autoListen = fromVoice)
                } else {
                    reply("No encontré una rutina pendiente llamada \"${intent.query}\".", speak = true, autoListen = fromVoice)
                }
                return
            }

            is LocalAssistantIntent.AddReminder -> {
                val remTime = intent.time ?: nowHHMM()
                addReminder(remTime, intent.text)
                reply("Listo, programé \"${intent.text}\" para las $remTime.", speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.BroadcastAlliance -> {
                val targetProfile = snap.profiles.firstOrNull {
                    LocalCommandMatcher.normalize(intent.targetQuery)
                        .contains(LocalCommandMatcher.normalize(it.name))
                }
                val finalTarget = targetProfile?.name ?: if (intent.targetQuery.equals("Toda la Familia", ignoreCase = true)) {
                    "Toda la Familia"
                } else {
                    intent.targetQuery
                }
                sendAllianceMessage(finalTarget, intent.text)
                val targetLabel = if (finalTarget == "Toda la Familia") "toda la familia" else finalTarget
                reply("Listo, envié el aviso a $targetLabel: \"${intent.text}\".", speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.SetAvailability -> {
                setAvailability(intent.status)
                reply(
                    "Listo ${prof.name}, cambié tu estado a ${intent.status.label}.",
                    speak = true,
                    autoListen = false,
                )
                return
            }

            is LocalAssistantIntent.UtilityAnswer -> {
                reply(intent.answer, speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.ReadNotifications -> {
                readNotificationsAloud()
                return
            }

            is LocalAssistantIntent.ReadAllianceMessages -> {
                readAllianceMessagesAloud()
                return
            }

            is LocalAssistantIntent.CheckWeather -> {
                refreshWeather(speakResult = true)
                return
            }

            is LocalAssistantIntent.StartCalm -> {
                updateLocal { it.copy(selectedTab = HubTab.CALMA) }
                startCalmMode()
                reply(
                    "Activando modo calma. Respira conmigo despacio durante dos minutos.",
                    speak = true,
                    mode = OrbVisualMode.CALM,
                    autoListen = false,
                )
                return
            }

            is LocalAssistantIntent.DailySummary -> {
                val pending = snap.routines.filter { it.profileId == prof.id && !it.done }
                val rems = snap.reminders.filter { it.profileId == prof.id }
                val unread = snap.messages.filter { it.fromName != prof.name && it.status != "Leído" }
                val detailWeather = _localState.value.weatherDetail
                val msg = "Hola ${prof.name}. $detailWeather Hoy tienes ${pending.size} rutina(s) pendiente(s), ${rems.size} recordatorio(s) y ${unread.size} mensaje(s) nuevo(s) de tu familia."
                reply(msg, speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.ListRoutines -> {
                val pending = snap.routines.filter { it.profileId == prof.id && !it.done }
                val msg = if (pending.isEmpty()) {
                    "No tienes rutinas pendientes por ahora, ${prof.name}."
                } else {
                    "Tus rutinas pendientes son: ${pending.joinToString(", ") { it.text }}."
                }
                reply(msg, speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.ListReminders -> {
                val rems = snap.reminders.filter { it.profileId == prof.id }
                val msg = if (rems.isEmpty()) {
                    "No tienes recordatorios programados."
                } else {
                    "Tus recordatorios son: ${rems.joinToString(". ") { "${it.text} a las ${it.time}" }}."
                }
                reply(msg, speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.FamilyStatus -> {
                val summary = snap.profiles.joinToString(". ") { "${it.name} está ${it.availability}" }
                reply("Estado de la Red Alianza: $summary.", speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.WhoIsSpeaking -> {
                val spk = resolvedSpeaker ?: _localState.value.lastSpeaker
                val msg = if (spk != null) {
                    "Te identifico como ${spk.displayName} con ${spk.confidencePercent}% de coincidencia."
                } else {
                    "Estás conectado con el perfil de ${prof.name}."
                }
                reply(msg, speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.CheckTime -> {
                reply("Son las ${nowHHMM()}.", speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.CheckDate -> {
                reply(
                    "Hoy es ${LocalCommandMatcher.formatCurrentSpanishDate()} y son las ${nowHHMM()}.",
                    speak = true,
                    autoListen = fromVoice,
                )
                return
            }

            is LocalAssistantIntent.HowAreYou -> {
                val unread = snap.messages.count { it.fromName != prof.name && it.status != "Leído" }
                val pending = snap.routines.count { it.profileId == prof.id && !it.done }
                reply(
                    "¡Todo en orden, ${prof.name}! Tienes $pending rutina(s) pendiente(s) y $unread aviso(s) familiar(es) sin leer. ¿Qué hacemos?",
                    speak = true,
                    autoListen = fromVoice,
                )
                return
            }

            is LocalAssistantIntent.Help -> {
                reply(
                    "Puedo leer tus notificaciones y WhatsApp, enviar avisos o llamadas a tu familia, gestionar rutinas y recordatorios, hacer cálculos o conversiones, decirte clima y hora, y buscar información abierta.",
                    speak = true,
                    autoListen = fromVoice,
                )
                return
            }

            is LocalAssistantIntent.Greeting -> {
                reply("¡Hola ${prof.name}! Aquí estoy contigo. ¿En qué te ayudo hoy?", speak = true, autoListen = fromVoice)
                return
            }

            is LocalAssistantIntent.Thanks -> {
                reply("Con gusto, ${prof.name}. Aquí sigo pendiente.", speak = true, autoListen = false)
                return
            }

            null -> askOpenAssistant(text, snap, prof, fromVoice)
        }
    }

    private fun askOpenAssistant(
        userText: String,
        snap: AlianzaCloudSnapshot,
        prof: FamilyProfile,
        fromVoice: Boolean,
    ) {
        updateLocal { it.copy(isThinking = true, errorText = null) }
        viewModelScope.launch {
            val myRoutines = snap.routines.filter { it.profileId == prof.id && !it.done }.map { it.text }
            val myReminders = snap.reminders.filter { it.profileId == prof.id }.map { "${it.time} ${it.text}" }
            val unreadMsgs = snap.messages
                .filter { it.fromName != prof.name && it.status != "Leído" }
                .map { "${it.fromName} -> ${it.toName}: ${it.text}" }
            val recentAlerts = snap.alerts.takeLast(5).map { "[${it.app}] ${it.text}" }
            val myContacts = snap.contacts.filter { it.profileId == prof.id }.map { it.alias }

            val speaker = _localState.value.lastSpeaker
            val speakerLabel = speaker?.let {
                "${it.displayName} (${it.confidencePercent}% - ${it.method.label})"
            }
            val contextSnapshot = AssistantContextSnapshot(
                activeName = prof.name,
                activeRole = prof.role,
                availability = prof.availability,
                weather = _localState.value.weatherDetail,
                currentTime = nowHHMM(),
                pendingRoutines = myRoutines,
                reminders = myReminders,
                unreadAlliance = unreadMsgs,
                notifications = recentAlerts,
                familyMembers = snap.profiles.map { "${it.name} (${it.role}, ${it.availability})" },
                contacts = myContacts,
                detectedSpeaker = speakerLabel,
            )

            aiRepository.generateAssistantReply(
                userMessage = userText,
                history = _localState.value.chatHistory,
                contextSnapshot = contextSnapshot,
            ).onSuccess { structured ->
                updateLocal { it.copy(isThinking = false, errorText = null) }
                val overridesSpeech = structured.actionType == "read_notifications" ||
                    structured.actionType == "check_weather"
                if (!overridesSpeech) {
                    reply(structured.reply, speak = true, autoListen = fromVoice)
                }
                executeStructuredAction(
                    actionType = structured.actionType,
                    target = structured.target.orEmpty(),
                    payload = structured.payload.orEmpty(),
                    time = structured.time.orEmpty(),
                )
            }.onFailure { err ->
                updateLocal {
                    it.copy(
                        isThinking = false,
                        errorText = err.message ?: "No se pudo consultar la información en este momento.",
                    )
                }
            }
        }
    }

    private fun executeStructuredAction(
        actionType: String,
        target: String,
        payload: String,
        time: String,
    ) {
        when (actionType) {
            "add_routine" -> if (payload.isNotBlank()) addRoutine(payload)
            "add_reminder" -> if (payload.isNotBlank()) addReminder(time.ifBlank { nowHHMM() }, payload)
            "send_alliance_message" -> if (payload.isNotBlank()) {
                sendAllianceMessage(target.ifBlank { "Toda la Familia" }, payload)
            }
            "prepare_call" -> if (target.isNotBlank()) prepareCommunication(CommChannel.CALL, target)
            "prepare_whatsapp" -> if (target.isNotBlank()) prepareCommunication(CommChannel.WHATSAPP, target, payload)
            "start_calm" -> {
                updateLocal { it.copy(selectedTab = HubTab.CALMA) }
                startCalmMode()
            }
            "read_notifications" -> readNotificationsAloud()
            "check_weather" -> refreshWeather(speakResult = true)
            "set_availability" -> {
                val status = AvailabilityStatus.entries.firstOrNull {
                    it.label.equals(target.trim(), ignoreCase = true)
                }
                if (status != null) setAvailability(status)
            }
        }
    }

    override fun onCleared() {
        AdaRuntimeHub.hasActiveViewModel = false
        AdaVoiceCoordinator.detachDelegate(commandDelegate)
        calmJob?.cancel()
        pulseJob?.cancel()
        super.onCleared()
    }

    private data class QuadLists(
        val profiles: List<FamilyProfile>,
        val routines: List<RoutineItem>,
        val reminders: List<ReminderItem>,
        val contacts: List<ContactItem>,
    )

    private data class SecondaryLists(
        val messages: List<AllianceMessage>,
        val rules: List<AllianceRule>,
        val alerts: List<PushAlert>,
        val webhooks: List<PushWebhookEndpoint>,
    )
}
