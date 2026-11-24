package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FamilyProfile
import com.example.ui.theme.AdaColors
import com.example.ui.theme.parseHexColor
import com.example.voice.SpeakerClassification

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileModal(
    open: Boolean,
    profiles: List<FamilyProfile>,
    activeProfileId: String,
    networkCode: String,
    enrolledVoiceProfileIds: Set<String>,
    enrollingProfileId: String?,
    enrollmentAudioLevel: Float,
    lastSpeaker: SpeakerClassification?,
    onClose: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (name: String, role: String, phone: String) -> Unit,
    onChangeNetworkCode: (String) -> Unit,
    onEnrollVoicePrint: (String) -> Unit,
    onClearVoicePrint: (String) -> Unit,
) {
    if (!open) return

    BackHandler(onBack = onClose)

    var showCreateForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var codeInput by remember(networkCode) { mutableStateOf(networkCode) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            color = AdaColors.Surface,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, AdaColors.BorderStrong),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Perfiles de la Alianza",
                            style = MaterialTheme.typography.titleMedium,
                            color = AdaColors.TextPrimary,
                        )
                        Text(
                            text = "Elige tu perfil o suma a un familiar a $networkCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdaColors.TextMuted,
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_profile_modal_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar selector de perfiles",
                            tint = AdaColors.TextSecondary,
                        )
                    }
                }

                HorizontalDivider(color = AdaColors.Border)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        profiles.chunked(2).forEach { rowProfiles ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                rowProfiles.forEach { profile ->
                                    val isSelected = profile.id == activeProfileId
                                    Surface(
                                        onClick = {
                                            onSelectProfile(profile.id)
                                            onClose()
                                        },
                                        color = if (isSelected) {
                                            AdaColors.SkyPrimary.copy(alpha = 0.16f)
                                        } else {
                                            AdaColors.Background.copy(alpha = 0.65f)
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) AdaColors.SkyPrimary.copy(alpha = 0.65f) else AdaColors.Border,
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 104.dp)
                                            .testTag("profile_card_${profile.id}"),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(parseHexColor(profile.colorHex)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = profile.name.take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color(0xFF04111F),
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = AdaColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val hasVoicePrint = profile.id in enrolledVoiceProfileIds
                                            Text(
                                                text = if (hasVoicePrint) "${profile.role} · Voz guardada" else profile.role,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (hasVoicePrint) AdaColors.EmeraldCalm else AdaColors.TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                if (rowProfiles.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Speaker Voiceprint Enrollment Card for Active Profile
                    val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
                    if (activeProfile != null) {
                        val isEnrolled = activeProfile.id in enrolledVoiceProfileIds
                        val isRecordingThis = enrollingProfileId == activeProfile.id
                        Surface(
                            color = AdaColors.Background.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isEnrolled) AdaColors.EmeraldCalm.copy(alpha = 0.45f) else AdaColors.Border
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isEnrolled) Icons.Rounded.VerifiedUser else Icons.Rounded.Mic,
                                        contentDescription = null,
                                        tint = if (isEnrolled) AdaColors.EmeraldCalm else AdaColors.SkyPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "Huella vocal de ${activeProfile.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AdaColors.TextPrimary,
                                    )
                                }
                                Text(
                                    text = if (isRecordingThis) {
                                        "Habla 3 segundos ahora: “Hola ADA, soy ${activeProfile.name}”..."
                                    } else if (isEnrolled) {
                                        "ADA ya distingue la voz de ${activeProfile.name} automáticamente al escuchar."
                                    } else {
                                        "Graba 3 segundos para que ADA reconozca quién habla y no confunda voces."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdaColors.TextMuted,
                                )
                                if (lastSpeaker != null) {
                                    Text(
                                        text = "Último hablante: ${lastSpeaker.displayName} (${lastSpeaker.confidencePercent}% · ${lastSpeaker.method.label})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AdaColors.SkyPrimary,
                                    )
                                }
                                if (isRecordingThis) {
                                    LinearProgressIndicator(
                                        progress = { enrollmentAudioLevel.coerceIn(0.05f, 1f) },
                                        color = AdaColors.EmeraldCalm,
                                        trackColor = AdaColors.Border,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Button(
                                        onClick = { onEnrollVoicePrint(activeProfile.id) },
                                        enabled = enrollingProfileId == null,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AdaColors.SkyPrimary,
                                            contentColor = Color(0xFF04111F),
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("enroll_voiceprint_button"),
                                    ) {
                                        Text(
                                            text = when {
                                                isRecordingThis -> "Grabando voz..."
                                                isEnrolled -> "Actualizar huella (3s)"
                                                else -> "Grabar huella vocal (3s)"
                                            },
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    if (isEnrolled && enrollingProfileId == null) {
                                        OutlinedButton(
                                            onClick = { onClearVoicePrint(activeProfile.id) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Borrar huella",
                                                tint = AdaColors.RoseSoft,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = AdaColors.Border)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "¿Falta alguien de la familia?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AdaColors.TextSecondary,
                        )
                        TextButton(
                            onClick = { showCreateForm = !showCreateForm },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonAdd,
                                contentDescription = null,
                                tint = AdaColors.SkyPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showCreateForm) "Cancelar" else "Crear perfil",
                                color = AdaColors.SkyPrimary,
                            )
                        }
                    }

                    if (showCreateForm) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Nombre (ej. Abuela Rosa)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = role,
                                onValueChange = { role = it },
                                label = { Text("Rol (ej. Abuela, Tío)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Teléfono WhatsApp (opcional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        onCreateProfile(name, role, phone)
                                        name = ""
                                        role = ""
                                        phone = ""
                                        showCreateForm = false
                                        onClose()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AdaColors.SkyPrimary,
                                    contentColor = Color(0xFF04111F),
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            ) {
                                Text("Guardar y entrar al perfil", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = AdaColors.Border)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.uppercase() },
                            label = { Text("Código de Red") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                if (codeInput.isNotBlank()) {
                                    onChangeNetworkCode(codeInput)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdaColors.SurfaceElevated,
                                contentColor = AdaColors.TextPrimary,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Unirse")
                        }
                    }
                }
            }
        }
    }
}
