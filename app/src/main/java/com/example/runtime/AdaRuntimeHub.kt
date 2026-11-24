package com.example.runtime

import android.content.Context
import com.example.data.AllianceMessage
import com.example.data.FamilyProfile
import com.example.data.PushAlert
import com.example.notifications.AdaNotificationListenerService
import com.example.notifications.CapturedSystemAlert
import com.example.voice.NoiseFilterMode
import com.example.voice.SpeakerClassification
import com.example.widget.AdaWidgetProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface AdaExternalAction {
    data object StartVoice : AdaExternalAction
    data object ReadMessages : AdaExternalAction
    data object ReadAlerts : AdaExternalAction
    data object StopSpeech : AdaExternalAction
    data class ToggleWakeWord(val enabled: Boolean) : AdaExternalAction
    data class SetNoiseFilter(val mode: NoiseFilterMode) : AdaExternalAction
    data class ToggleAutoReadWhatsApp(val enabled: Boolean) : AdaExternalAction
    data class IncomingWhatsAppAlert(val alert: CapturedSystemAlert) : AdaExternalAction
}

@Serializable
data class CachedWidgetSummary(
    val uid: String = "",
    val activeProfileName: String = "Familia",
    val unreadMessagesCount: Int = 0,
    val alertsCount: Int = 0,
    val lastSpeakerLabel: String = "Sin voz reciente",
    val summaryLine: String = "Sin mensajes ni alertas pendientes.",
    val recentMessageLines: List<String> = emptyList(),
    val recentAlertLines: List<String> = emptyList(),
)

data class AdaRuntimeSnapshot(
    val uid: String = "",
    val activeProfileId: String = "",
    val activeProfileName: String = "Familia",
    val profiles: List<FamilyProfile> = emptyList(),
    val messages: List<AllianceMessage> = emptyList(),
    val alerts: List<PushAlert> = emptyList(),
    val weatherSummary: String = "Clima local",
    val lastSpeaker: SpeakerClassification? = null,
    val responseText: String = "¡Hola! Soy ADA, tu asistente en la Red Alianza.",
    val transcriptPreview: String = "",
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val speechPulseTick: Long = 0L,
    val wakeWordEnabled: Boolean = false,
    val wakeWordArmed: Boolean = false,
    val noiseFilterMode: NoiseFilterMode = NoiseFilterMode.STRICT,
    val floatingActive: Boolean = false,
    val autoReadWhatsApp: Boolean = true,
)

object AdaRuntimeHub {
    private const val PREFS_NAME = "ada_runtime_cache_v1"
    private const val KEY_SUMMARY = "widget_summary"

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(AdaRuntimeSnapshot())
    val state: StateFlow<AdaRuntimeSnapshot> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<AdaExternalAction>(extraBufferCapacity = 24)
    val actions: SharedFlow<AdaExternalAction> = _actions.asSharedFlow()

    @Volatile
    var hasActiveViewModel: Boolean = false

    fun updateState(
        context: Context,
        transform: (AdaRuntimeSnapshot) -> AdaRuntimeSnapshot,
    ) {
        val prev = _state.value
        val next = transform(prev)
        _state.value = next
        if (prev.messages != next.messages ||
            prev.alerts != next.alerts ||
            prev.activeProfileName != next.activeProfileName ||
            prev.lastSpeaker != next.lastSpeaker ||
            prev.wakeWordEnabled != next.wakeWordEnabled ||
            prev.floatingActive != next.floatingActive ||
            prev.autoReadWhatsApp != next.autoReadWhatsApp
        ) {
            persistSummary(context, next)
            AdaWidgetProvider.updateAllWidgets(context)
        }
    }

    fun setFloatingActive(context: Context, active: Boolean) {
        updateState(context) { it.copy(floatingActive = active) }
    }

    fun setAutoReadWhatsApp(context: Context, enabled: Boolean) {
        AdaNotificationListenerService.setAutoReadWhatsAppEnabled(context, enabled)
        updateState(context) { it.copy(autoReadWhatsApp = enabled) }
    }

    fun onIncomingSystemAlert(context: Context, alert: CapturedSystemAlert) {
        if (!alert.isWhatsApp) return
        val enabled = AdaNotificationListenerService.isAutoReadWhatsAppEnabled(context)
        if (!enabled) return
        if (hasActiveViewModel) {
            _actions.tryEmit(AdaExternalAction.IncomingWhatsAppAlert(alert))
        } else {
            val spoken = formatWhatsAppSpokenText(alert.text)
            AdaVoiceCoordinator.speak(context.applicationContext, spoken, autoListenAfter = false)
        }
    }

    fun formatWhatsAppSpokenText(rawText: String): String {
        val clean = rawText.trim()
        return "Nuevo WhatsApp de $clean"
    }

    fun dispatchAction(context: Context, action: AdaExternalAction) {
        if (hasActiveViewModel) {
            _actions.tryEmit(action)
        } else {
            handleStandaloneFallback(context.applicationContext, action)
        }
    }

    fun loadCachedSummary(context: Context): CachedWidgetSummary {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SUMMARY, null) ?: return CachedWidgetSummary()
        return runCatching { json.decodeFromString<CachedWidgetSummary>(raw) }
            .getOrDefault(CachedWidgetSummary())
    }

    private fun persistSummary(context: Context, snapshot: AdaRuntimeSnapshot) {
        val unread = snapshot.messages.count {
            it.fromName != snapshot.activeProfileName && it.status != "Leído"
        }
        val msgLines = snapshot.messages.takeLast(4).map {
            "${it.fromName} para ${it.toName}: ${it.text}"
        }
        val alertLines = snapshot.alerts.takeLast(4).map {
            "De ${it.app}: ${it.text}"
        }
        val speakerLabel = snapshot.lastSpeaker?.let {
            "${it.displayName} (${it.confidencePercent}%)"
        } ?: "Perfil: ${snapshot.activeProfileName}"

        val summaryLine = when {
            snapshot.alerts.isNotEmpty() -> {
                val latest = snapshot.alerts.last()
                "Alerta (${latest.app}): ${latest.text}"
            }
            snapshot.messages.isNotEmpty() -> {
                val latest = snapshot.messages.last()
                "${latest.fromName}: ${latest.text}"
            }
            else -> "Sin mensajes ni alertas pendientes."
        }

        val cached = CachedWidgetSummary(
            uid = snapshot.uid,
            activeProfileName = snapshot.activeProfileName,
            unreadMessagesCount = unread,
            alertsCount = snapshot.alerts.size,
            lastSpeakerLabel = speakerLabel,
            summaryLine = summaryLine,
            recentMessageLines = msgLines,
            recentAlertLines = alertLines,
        )
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SUMMARY, json.encodeToString(cached)).apply()
    }

    private fun handleStandaloneFallback(context: Context, action: AdaExternalAction) {
        when (action) {
            AdaExternalAction.ReadMessages -> {
                val snap = _state.value
                val lines = if (snap.messages.isNotEmpty()) {
                    snap.messages.takeLast(4).map { "${it.fromName} para ${it.toName}: ${it.text}" }
                } else {
                    loadCachedSummary(context).recentMessageLines
                }
                val text = if (lines.isEmpty()) {
                    "No hay mensajes recientes en la Red Alianza."
                } else {
                    "Mensajes de la Red Alianza: ${lines.joinToString(". ")}."
                }
                AdaVoiceCoordinator.speak(context, text, autoListenAfter = false)
            }
            AdaExternalAction.ReadAlerts -> {
                val snap = _state.value
                val nativeCaptured = AdaNotificationListenerService.capturedAlerts.value
                val lines = when {
                    snap.alerts.isNotEmpty() -> snap.alerts.takeLast(4).map { "De ${it.app}: ${it.text}" }
                    nativeCaptured.isNotEmpty() -> nativeCaptured.takeLast(4).map { "De ${it.app}: ${it.text}" }
                    else -> loadCachedSummary(context).recentAlertLines
                }
                val text = if (lines.isEmpty()) {
                    "No tienes notificaciones recientes en este momento."
                } else {
                    "Tus notificaciones recientes son: ${lines.joinToString(". ")}."
                }
                AdaVoiceCoordinator.speak(context, text, autoListenAfter = false)
            }
            AdaExternalAction.StopSpeech -> {
                AdaVoiceCoordinator.stopSpeech(context)
            }
            AdaExternalAction.StartVoice -> {
                AdaVoiceCoordinator.startDirectListening(context)
            }
            is AdaExternalAction.ToggleWakeWord -> {
                AdaVoiceCoordinator.setWakeWordEnabled(context, action.enabled)
            }
            is AdaExternalAction.SetNoiseFilter -> {
                AdaVoiceCoordinator.setNoiseFilterMode(context, action.mode)
            }
            is AdaExternalAction.ToggleAutoReadWhatsApp -> {
                setAutoReadWhatsApp(context, action.enabled)
            }
            is AdaExternalAction.IncomingWhatsAppAlert -> {
                val spoken = formatWhatsAppSpokenText(action.alert.text)
                AdaVoiceCoordinator.speak(context, spoken, autoListenAfter = false)
            }
        }
    }

    fun speakStandalone(context: Context, text: String) {
        AdaVoiceCoordinator.speak(context, text, autoListenAfter = false)
    }
}
