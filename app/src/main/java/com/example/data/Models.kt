package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable

enum class AvailabilityStatus(val label: String) {
    DISPONIBLE("Disponible"),
    OCUPADO("Ocupado"),
    ESTUDIANDO("Estudiando"),
    DESCANSANDO("Descansando"),
    EN_SILENCIO("En silencio");

    companion object {
        fun fromLabel(label: String?): AvailabilityStatus =
            entries.firstOrNull { it.label == label } ?: DISPONIBLE
    }
}

enum class OrbVisualMode {
    IDLE,
    LISTENING,
    SPEAKING,
    CALM,
    ALERT,
}

enum class HubTab(val label: String) {
    ALIANZA("Alianza"),
    PUSH("Alertas"),
    RUTINAS("Rutinas"),
    CALMA("Calma/Red"),
}

enum class RuleTrigger(val wireValue: String, val label: String) {
    ROUTINE_DONE("routine_done", "Al completar rutina"),
    CALM_STARTED("calm_started", "Al iniciar modo calma"),
    STATUS_BUSY("status_busy", "Al ponerse Ocupado");

    companion object {
        fun fromWire(value: String?): RuleTrigger =
            entries.firstOrNull { it.wireValue == value } ?: ROUTINE_DONE
    }
}

@Keep
@IgnoreExtraProperties
data class UserNetworkConfig(
    @DocumentId val id: String = "",
    val uid: String = "",
    val networkCode: String = "ALIANZA-HOGAR",
    val activeProfileId: String = "prof_constanza",
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class FamilyProfile(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val role: String = "Familia",
    val colorHex: String = "#38BDF8",
    val persona: String = "ada",
    val availability: String = "Disponible",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val availabilityStatus: AvailabilityStatus
        get() = AvailabilityStatus.fromLabel(availability)
}

@Keep
@IgnoreExtraProperties
data class RoutineItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val profileId: String = "",
    val text: String = "",
    val done: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ReminderItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val profileId: String = "",
    val time: String = "09:00",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ContactItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val profileId: String = "",
    val alias: String = "",
    val phone: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AllianceMessage(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val fromName: String = "",
    val toName: String = "",
    val text: String = "",
    val time: String = "",
    val status: String = "Entregado",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AllianceRule(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val trigger: String = "routine_done",
    val targetProfileId: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PushAlert(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val app: String = "",
    val text: String = "",
    val time: String = "",
    val source: String = "native",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PushWebhookEndpoint(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val url: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class ChatTurn(
    val id: String,
    val isUser: Boolean,
    val speaker: String,
    val text: String,
    val time: String,
)

enum class CommChannel {
    CALL,
    WHATSAPP,
}

data class QuickComm(
    val channel: CommChannel,
    val alias: String,
    val phone: String,
    val uriString: String,
)

data class OrbCheckPulse(
    val id: Long,
    val label: String,
)

@Serializable
data class GeminiStructuredReply(
    val reply: String = "",
    val actionType: String = "none",
    val target: String? = null,
    val payload: String? = null,
    val time: String? = null,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
