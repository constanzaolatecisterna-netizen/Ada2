package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChatTurn
import com.example.data.CommChannel
import com.example.data.OrbCheckPulse
import com.example.data.OrbVisualMode
import com.example.data.QuickComm
import com.example.ui.theme.AdaColors
import com.example.voice.NoiseFilterMode
import com.example.voice.SpeakerClassification
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrbStage(
    orbMode: OrbVisualMode,
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    speechPulseTick: Long,
    fluidMode: Boolean,
    wakeWordEnabled: Boolean,
    wakeWordArmed: Boolean,
    noiseFilterMode: NoiseFilterMode,
    floatingActive: Boolean,
    autoReadWhatsApp: Boolean,
    lastSpeaker: SpeakerClassification?,
    checkPulse: OrbCheckPulse?,
    transcriptPreview: String,
    responseText: String,
    errorText: String?,
    chatHistory: List<ChatTurn>,
    quickComm: QuickComm?,
    weatherSummary: String,
    pendingCount: Int,
    unreadCount: Int,
    onOrbClick: () -> Unit,
    onToggleWakeWord: () -> Unit,
    onCycleNoiseFilter: () -> Unit,
    onToggleFloatingAda: () -> Unit,
    onToggleAutoReadWhatsApp: () -> Unit,
    onOpenVoiceProfiles: () -> Unit,
    onToggleFluidMode: () -> Unit,
    onRepeatSpeech: () -> Unit,
    onStopSpeech: () -> Unit,
    onDismissError: () -> Unit,
    onSubmitCommand: (String) -> Unit,
    onOpenQuickComm: (QuickComm) -> Unit,
    onCloseQuickComm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputValue by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }

    val isConfirming = checkPulse != null

    val statusText = when {
        checkPulse != null -> checkPulse.label
        isThinking -> stringResource(R.string.status_thinking_ada)
        orbMode == OrbVisualMode.LISTENING || isListening -> stringResource(R.string.status_listening)
        orbMode == OrbVisualMode.SPEAKING || isSpeaking -> stringResource(R.string.status_speaking_ada)
        orbMode == OrbVisualMode.CALM -> stringResource(R.string.status_calm)
        orbMode == OrbVisualMode.ALERT -> stringResource(R.string.status_alert)
        wakeWordArmed -> stringResource(R.string.status_wake_armed)
        else -> stringResource(R.string.status_online)
    }

    val statusColor = when {
        isConfirming -> AdaColors.EmeraldCalm
        wakeWordArmed -> Color(0xFF7DD3FC)
        else -> AdaColors.SkyPrimary
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Context summary line
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = AdaColors.SkyPrimary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Núcleo ADA Alianza",
                    style = MaterialTheme.typography.labelMedium,
                    color = AdaColors.TextPrimary,
                )
            }
            Text(
                text = "· $weatherSummary · $pendingCount rutinas pendientes",
                style = MaterialTheme.typography.labelSmall,
                color = AdaColors.TextMuted,
            )
            if (unreadCount > 0) {
                Text(
                    text = "· $unreadCount ${if (unreadCount == 1) "aviso familiar" else "avisos familiares"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AdaColors.SkyPrimary,
                )
            }
        }

        // Central living orb
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LivingHolographicOrb(
                orbMode = orbMode,
                isSpeaking = isSpeaking,
                isListening = isListening,
                isThinking = isThinking,
                speechPulseTick = speechPulseTick,
                isConfirming = isConfirming,
                onClick = onOrbClick,
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
            )

            if (lastSpeaker != null) {
                Surface(
                    onClick = onOpenVoiceProfiles,
                    color = AdaColors.Surface,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, AdaColors.EmeraldCalm.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("speaker_classification_badge"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = "Hablante detectado",
                            tint = AdaColors.EmeraldCalm,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "${lastSpeaker.displayName} · ${lastSpeaker.confidencePercent}% (${lastSpeaker.method.label})",
                            style = MaterialTheme.typography.labelSmall,
                            color = AdaColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (transcriptPreview.isNotBlank()) {
                Text(
                    text = "“$transcriptPreview”",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = AdaColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Response + quick controls + command input
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                color = AdaColors.Surface.copy(alpha = 0.84f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, AdaColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ADA",
                            style = MaterialTheme.typography.titleSmall,
                            color = AdaColors.SkyPrimary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (chatHistory.isNotEmpty()) {
                                TextButton(
                                    onClick = { showHistory = !showHistory },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("toggle_history_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = AdaColors.TextSecondary,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showHistory) "Ocultar" else "Historial (${chatHistory.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AdaColors.TextSecondary,
                                    )
                                }
                            }

                            TextButton(
                                onClick = if (isSpeaking) onStopSpeech else onRepeatSpeech,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("speech_toggle_button"),
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSpeaking) AdaColors.RoseSoft else AdaColors.SkyPrimary,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSpeaking) "Detener voz" else "Leer en voz alta",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSpeaking) AdaColors.RoseSoft else AdaColors.SkyPrimary,
                                )
                            }
                        }
                    }

                    Text(
                        text = responseText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AdaColors.TextPrimary,
                    )

                    if (!errorText.isNullOrBlank()) {
                        Surface(
                            color = AdaColors.RoseAlert.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AdaColors.RoseAlert.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = errorText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdaColors.RoseSoft,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 6.dp),
                                )
                                IconButton(
                                    onClick = onDismissError,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("dismiss_error_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Cerrar aviso de error",
                                        tint = AdaColors.RoseSoft,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showHistory && chatHistory.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            chatHistory.takeLast(8).forEach { turn ->
                                Text(
                                    text = "${turn.speaker} (${turn.time}): ${turn.text}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (turn.isUser) AdaColors.SkyPrimary else AdaColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            if (quickComm != null) {
                Surface(
                    color = AdaColors.EmeraldSolid.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, AdaColors.EmeraldCalm.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = if (quickComm.channel == CommChannel.WHATSAPP) {
                                Icons.Rounded.ChatBubbleOutline
                            } else {
                                Icons.Rounded.Call
                            },
                            contentDescription = null,
                            tint = AdaColors.EmeraldCalm,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Acción lista",
                                style = MaterialTheme.typography.labelMedium,
                                color = AdaColors.EmeraldCalm,
                            )
                            Text(
                                text = "${if (quickComm.channel == CommChannel.WHATSAPP) "WhatsApp" else "Llamada"} a ${quickComm.alias} (${quickComm.phone})",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdaColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = { onOpenQuickComm(quickComm) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdaColors.EmeraldCalm,
                                contentColor = Color(0xFF031A12),
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Abrir", fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = onCloseQuickComm,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cerrar tarjeta de contacto",
                                tint = AdaColors.TextMuted,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = wakeWordEnabled,
                    onClick = onToggleWakeWord,
                    label = {
                        Text(
                            text = if (wakeWordEnabled) "Di \"ADA\": Activo" else "Activar diciendo \"ADA\"",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AdaColors.Surface,
                        selectedContainerColor = AdaColors.EmeraldCalm.copy(alpha = 0.22f),
                        labelColor = AdaColors.TextSecondary,
                        selectedLabelColor = AdaColors.EmeraldCalm,
                        iconColor = AdaColors.SkyPrimary,
                        selectedLeadingIconColor = AdaColors.EmeraldCalm,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("wake_word_chip"),
                )
                FilterChip(
                    selected = floatingActive,
                    onClick = onToggleFloatingAda,
                    label = {
                        Text(
                            text = if (floatingActive) "ADA Flotante: On" else "ADA Flotante",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.PictureInPictureAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AdaColors.Surface,
                        selectedContainerColor = AdaColors.SkyPrimary.copy(alpha = 0.22f),
                        labelColor = AdaColors.TextSecondary,
                        selectedLabelColor = AdaColors.SkyPrimary,
                        iconColor = AdaColors.SkyPrimary,
                        selectedLeadingIconColor = AdaColors.SkyPrimary,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("floating_ada_chip"),
                )
                FilterChip(
                    selected = autoReadWhatsApp,
                    onClick = onToggleAutoReadWhatsApp,
                    label = {
                        Text(
                            text = if (autoReadWhatsApp) "WhatsApp Auto: On" else "Auto-leer WhatsApp",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AdaColors.Surface,
                        selectedContainerColor = AdaColors.EmeraldCalm.copy(alpha = 0.22f),
                        labelColor = AdaColors.TextSecondary,
                        selectedLabelColor = AdaColors.EmeraldCalm,
                        iconColor = AdaColors.AmberAccent,
                        selectedLeadingIconColor = AdaColors.EmeraldCalm,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("whatsapp_autoread_chip"),
                )
                QuickCommandChip(
                    label = "Filtro: ${noiseFilterMode.label}",
                    icon = Icons.Rounded.Tune,
                    tint = AdaColors.EmeraldCalm,
                    onClick = onCycleNoiseFilter,
                )
                QuickCommandChip(
                    label = "Huellas de voz",
                    icon = Icons.Rounded.RecordVoiceOver,
                    tint = AdaColors.SkyPrimary,
                    onClick = onOpenVoiceProfiles,
                )
                FilterChip(
                    selected = fluidMode,
                    onClick = onToggleFluidMode,
                    label = {
                        Text(
                            text = if (fluidMode) "Auto-réplica: On" else "Auto-réplica",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AdaColors.Surface,
                        selectedContainerColor = AdaColors.SkyPrimary.copy(alpha = 0.22f),
                        labelColor = AdaColors.TextSecondary,
                        selectedLabelColor = AdaColors.SkyPrimary,
                        iconColor = AdaColors.SkyPrimary,
                        selectedLeadingIconColor = AdaColors.SkyPrimary,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("fluid_mode_chip"),
                )
                QuickCommandChip("Leer mensajes", Icons.Rounded.ChatBubbleOutline, AdaColors.SkyPrimary) {
                    onSubmitCommand("leer mensajes")
                }
                QuickCommandChip("Notificaciones", Icons.Rounded.Notifications, AdaColors.AmberAccent) {
                    onSubmitCommand("lee mis notificaciones")
                }
                QuickCommandChip("Resumen", Icons.Rounded.WbSunny, AdaColors.AmberAccent) {
                    onSubmitCommand("resumen del día")
                }
                QuickCommandChip("Clima", Icons.Rounded.Cloud, AdaColors.SkyPrimary) {
                    onSubmitCommand("cómo está el clima")
                }
                QuickCommandChip("Calma", Icons.Rounded.SelfImprovement, AdaColors.EmeraldCalm) {
                    onSubmitCommand("modo calma")
                }
            }

            // Command Input Bar
            Surface(
                color = AdaColors.Surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, AdaColors.BorderStrong),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledIconButton(
                        onClick = onOrbClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) {
                                AdaColors.RoseAlert.copy(alpha = 0.2f)
                            } else {
                                AdaColors.SkyPrimary.copy(alpha = 0.16f)
                            },
                            contentColor = if (isListening) AdaColors.RoseSoft else AdaColors.SkyPrimary,
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("mic_input_button"),
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = if (isListening) "Detener micrófono" else "Activar micrófono",
                        )
                    }

                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        placeholder = {
                            Text("Escribe aquí...", color = AdaColors.TextSubtle)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val trimmed = inputValue.trim()
                                if (trimmed.isNotEmpty()) {
                                    inputValue = ""
                                    onSubmitCommand(trimmed)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = AdaColors.TextPrimary,
                            unfocusedTextColor = AdaColors.TextPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_input"),
                    )

                    Button(
                        onClick = {
                            val trimmed = inputValue.trim()
                            if (trimmed.isNotEmpty()) {
                                inputValue = ""
                                onSubmitCommand(trimmed)
                            }
                        },
                        enabled = !isThinking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AdaColors.SkyPrimary,
                            contentColor = Color(0xFF04111F),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("send_command_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enviar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCommandChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = AdaColors.Surface,
            labelColor = AdaColors.TextSecondary,
        ),
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun LivingHolographicOrb(
    orbMode: OrbVisualMode,
    isSpeaking: Boolean,
    isListening: Boolean,
    isThinking: Boolean,
    speechPulseTick: Long,
    isConfirming: Boolean,
    onClick: () -> Unit,
) {
    val wordBurst = remember { Animatable(0f) }
    LaunchedEffect(speechPulseTick, isSpeaking) {
        if (isSpeaking && speechPulseTick > 0L) {
            wordBurst.snapTo(1f)
            wordBurst.animateTo(0f, animationSpec = tween(280))
        } else if (!isSpeaking) {
            wordBurst.snapTo(0f)
        }
    }

    val infinite = rememberInfiniteTransition(label = "orb_anim")
    val outerAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "outer_angle",
    )
    val innerAngle by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
        label = "inner_angle",
    )
    val lightArcAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing)),
        label = "light_arc_angle",
    )
    val voicePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_350, easing = LinearEasing)),
        label = "voice_phase",
    )
    val slowPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3_600, easing = LinearEasing)),
        label = "slow_phase",
    )
    val confirmWave by animateFloatAsState(
        targetValue = if (isConfirming) 1.25f else 0.9f,
        animationSpec = tween(650),
        label = "confirm_wave",
    )

    val speakingNow = isSpeaking || orbMode == OrbVisualMode.SPEAKING
    val corePrimary = when {
        isConfirming -> AdaColors.EmeraldCalm
        orbMode == OrbVisualMode.ALERT -> AdaColors.RoseAlert
        orbMode == OrbVisualMode.CALM -> AdaColors.EmeraldCalm
        speakingNow -> AdaColors.EmeraldCalm
        orbMode == OrbVisualMode.LISTENING || isListening -> Color(0xFF7DD3FC)
        else -> AdaColors.SkyPrimary
    }
    val coreSecondary = when {
        isConfirming -> AdaColors.EmeraldSolid
        orbMode == OrbVisualMode.ALERT -> Color(0xFFBE123C)
        orbMode == OrbVisualMode.CALM || speakingNow -> Color(0xFF059669)
        else -> AdaColors.IndigoSecondary
    }
    val ringTint = if (speakingNow) AdaColors.EmeraldCalm else AdaColors.SkyPrimary
    val orbActionDesc = if (isListening) {
        "Detener escucha"
    } else {
        "Tocar para hablar con el asistente"
    }

    Box(
        modifier = Modifier
            .size(216.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("orb_voice_button")
            .semantics { contentDescription = orbActionDesc },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            // Expanding light ripple waves while speaking or listening
            if (speakingNow || isListening) {
                val baseT = (voicePhase / (2.0 * PI)).toFloat()
                for (i in 0 until 2) {
                    val phase = (baseT + i * 0.5f) % 1f
                    val waveR = maxR * (0.68f + phase * 0.30f)
                    val alpha = (1f - phase) * if (speakingNow) (0.48f + wordBurst.value * 0.25f) else 0.32f
                    drawCircle(
                        color = corePrimary.copy(alpha = alpha.coerceIn(0f, 1f)),
                        radius = waveR,
                        center = center,
                        style = Stroke(width = (3.2f - phase * 2f).coerceAtLeast(1f).dp.toPx()),
                    )
                }
            }

            // Outer orbital dashed light ring
            rotate(outerAngle, pivot = center) {
                drawCircle(
                    color = ringTint.copy(alpha = if (speakingNow) 0.52f + wordBurst.value * 0.2f else 0.35f),
                    radius = maxR * 0.94f,
                    center = center,
                    style = Stroke(
                        width = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 16f, 5f, 16f)),
                    ),
                )
                drawCircle(
                    color = ringTint.copy(alpha = 0.22f),
                    radius = maxR * 0.86f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // Inner hexagon light ring counter-rotating
            rotate(innerAngle, pivot = center) {
                val hexR = maxR * 0.80f
                val hexPath = Path()
                for (i in 0 until 6) {
                    val rad = (PI / 3.0) * i - (PI / 2.0)
                    val x = center.x + (hexR * cos(rad)).toFloat()
                    val y = center.y + (hexR * sin(rad)).toFloat()
                    if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
                }
                hexPath.close()
                drawPath(
                    path = hexPath,
                    color = AdaColors.IndigoSecondary.copy(alpha = if (speakingNow) 0.55f else 0.40f),
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
                    ),
                )
            }

            if (isConfirming) {
                drawCircle(
                    color = AdaColors.EmeraldCalm.copy(alpha = 0.6f),
                    radius = maxR * 0.68f * confirmWave,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }

            // Steady sphere geometry; only its internal & surrounding light moves
            val orbRadius = maxR * 0.65f
            val glowBreath = 0.5f + 0.5f * sin(if (speakingNow) voicePhase * 2f else slowPhase)
            val auraReach = orbRadius * (1.30f + (if (speakingNow) 0.14f * glowBreath + wordBurst.value * 0.12f else 0.05f * glowBreath))
            val auraAlpha = if (speakingNow) {
                0.38f + 0.20f * glowBreath + wordBurst.value * 0.22f
            } else if (isListening || isThinking) {
                0.34f + 0.14f * glowBreath
            } else {
                0.26f + 0.08f * glowBreath
            }

            // Outer pulsing radial light aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(corePrimary.copy(alpha = auraAlpha.coerceIn(0f, 0.85f)), Color.Transparent),
                    center = center,
                    radius = auraReach,
                ),
                radius = auraReach,
                center = center,
            )

            // Orbiting internal light hotspot inside steady sphere
            val lightX = when {
                speakingNow -> cos(voicePhase * 2f) * orbRadius * 0.30f
                isThinking || isListening -> cos(voicePhase) * orbRadius * 0.24f
                else -> -orbRadius * 0.22f + cos(slowPhase) * orbRadius * 0.06f
            }
            val lightY = when {
                speakingNow -> sin(voicePhase * 3f) * orbRadius * 0.28f
                isThinking || isListening -> sin(voicePhase) * orbRadius * 0.22f
                else -> -orbRadius * 0.24f + sin(slowPhase) * orbRadius * 0.06f
            }
            val hotspotRadius = orbRadius * (1.28f + if (speakingNow) wordBurst.value * 0.24f else 0f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.94f), corePrimary, coreSecondary),
                    center = Offset(center.x + lightX, center.y + lightY),
                    radius = hotspotRadius,
                ),
                radius = orbRadius,
                center = center,
            )

            // Secondary sweeping inner volumetric light sheen when speaking or listening
            if (speakingNow || isListening) {
                val sweepX = cos(-voicePhase * 1.5f) * orbRadius * 0.36f
                val sweepY = sin(-voicePhase * 2f) * orbRadius * 0.32f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (speakingNow) 0.36f + wordBurst.value * 0.24f else 0.20f),
                            Color.Transparent,
                        ),
                        center = Offset(center.x + sweepX, center.y + sweepY),
                        radius = orbRadius * 0.72f,
                    ),
                    radius = orbRadius,
                    center = center,
                )
            }

            // Sweeping perimeter light arcs rotating around the sphere
            if (speakingNow || isListening || isThinking) {
                val arcAlpha = if (speakingNow) 0.65f + wordBurst.value * 0.30f else 0.42f
                val arcR = orbRadius * 1.07f
                val arcTopLeft = Offset(center.x - arcR, center.y - arcR)
                val arcSize = Size(arcR * 2f, arcR * 2f)
                drawArc(
                    color = Color.White.copy(alpha = arcAlpha.coerceIn(0f, 0.95f)),
                    startAngle = lightArcAngle,
                    sweepAngle = 72f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = corePrimary.copy(alpha = arcAlpha.coerceIn(0f, 0.95f)),
                    startAngle = lightArcAngle + 180f,
                    sweepAngle = 72f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Subtle inner glass ring
            drawCircle(
                color = Color.White.copy(alpha = if (speakingNow) 0.42f else 0.28f),
                radius = orbRadius * 0.84f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            if (isConfirming) {
                val checkPath = Path().apply {
                    moveTo(center.x - orbRadius * 0.28f, center.y + orbRadius * 0.02f)
                    lineTo(center.x - orbRadius * 0.06f, center.y + orbRadius * 0.24f)
                    lineTo(center.x + orbRadius * 0.30f, center.y - orbRadius * 0.20f)
                }
                drawPath(
                    path = checkPath,
                    color = Color(0xFF04111F),
                    style = Stroke(
                        width = 4.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            } else {
                // Centered blinking ADA eyes (no mouth)
                val blinkNorm = (slowPhase / (2.0 * PI)).toFloat()
                val isBlinking = blinkNorm < 0.035f
                val eyeW = 12.dp.toPx()
                val baseEyeH = 19.dp.toPx()
                val eyeH = if (isBlinking) 4.dp.toPx() else baseEyeH
                val eyeOffset = 18.dp.toPx()

                drawRoundRect(
                    color = Color(0xFF04111F),
                    topLeft = Offset(center.x - eyeOffset - eyeW / 2f, center.y - eyeH / 2f),
                    size = Size(eyeW, eyeH),
                    cornerRadius = CornerRadius(eyeW / 2f, eyeW / 2f),
                )
                drawRoundRect(
                    color = Color(0xFF04111F),
                    topLeft = Offset(center.x + eyeOffset - eyeW / 2f, center.y - eyeH / 2f),
                    size = Size(eyeW, eyeH),
                    cornerRadius = CornerRadius(eyeW / 2f, eyeW / 2f),
                )
            }
        }
    }
}
