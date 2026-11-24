package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PushAlert
import com.example.data.PushWebhookEndpoint
import com.example.ui.theme.AdaColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertasTabContent(
    alerts: List<PushAlert>,
    webhooks: List<PushWebhookEndpoint>,
    nativeNotificationAccess: Boolean,
    autoReadWhatsApp: Boolean,
    onToggleAutoReadWhatsApp: (Boolean) -> Unit,
    onReadAllNotifications: () -> Unit,
    onCaptureClipboard: (String) -> Unit,
    onTestAlert: () -> Unit,
    onDeleteAlert: (String) -> Unit,
    onClearAlerts: () -> Unit,
    onOpenNativeNotificationSettings: () -> Unit,
    onSyncNativeNotifications: () -> Unit,
    onAddWebhook: (name: String, url: String) -> Unit,
    onToggleWebhook: (id: String, nextActive: Boolean) -> Unit,
    onDeleteWebhook: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var webhookName by remember { mutableStateOf("") }
    var webhookUrl by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Real-time reader controls
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Lector de alertas en tiempo real",
                style = MaterialTheme.typography.titleSmall,
                color = AdaColors.TextPrimary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onReadAllNotifications,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AdaColors.SkyPrimary,
                        contentColor = Color(0xFF04111F),
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Leer en voz alta", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        onCaptureClipboard(text)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Capturar portapapeles")
                }

                OutlinedButton(
                    onClick = onTestAlert,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("test_alert_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = AdaColors.EmeraldCalm)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Probar alerta", color = AdaColors.EmeraldCalm)
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // WhatsApp Automatic Voice Reading Card
        Surface(
            color = if (autoReadWhatsApp) {
                AdaColors.EmeraldCalm.copy(alpha = 0.10f)
            } else {
                AdaColors.Background.copy(alpha = 0.65f)
            },
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                1.dp,
                if (autoReadWhatsApp) AdaColors.EmeraldCalm.copy(alpha = 0.45f) else AdaColors.Border
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    ) {
                        Text(
                            text = "Lectura automática de WhatsApp",
                            style = MaterialTheme.typography.titleSmall,
                            color = AdaColors.TextPrimary,
                        )
                        Text(
                            text = if (autoReadWhatsApp) {
                                "ADA leerá en voz alta los mensajes de WhatsApp apenas lleguen (salvo en modo En silencio)."
                            } else {
                                "Desactivado: los mensajes de WhatsApp solo se guardan en la bandeja."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AdaColors.TextMuted,
                        )
                    }
                    Switch(
                        checked = autoReadWhatsApp,
                        onCheckedChange = onToggleAutoReadWhatsApp,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF04111F),
                            checkedTrackColor = AdaColors.EmeraldCalm,
                        ),
                        modifier = Modifier.testTag("whatsapp_autoread_switch"),
                    )
                }

                if (autoReadWhatsApp && !nativeNotificationAccess) {
                    Text(
                        text = "Falta conceder el permiso de acceso a notificaciones abajo para escuchar WhatsApp en automático.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AdaColors.AmberAccent,
                    )
                }
            }
        }

        // Native Notification Listener Access
        Surface(
            color = AdaColors.Background.copy(alpha = 0.65f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, AdaColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (nativeNotificationAccess) Icons.Rounded.Shield else Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = if (nativeNotificationAccess) AdaColors.EmeraldCalm else AdaColors.AmberAccent,
                    )
                    Text(
                        text = if (nativeNotificationAccess) {
                            "Lectura nativa Android: Activa"
                        } else {
                            "Activa el acceso a notificaciones del sistema"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = AdaColors.TextPrimary,
                    )
                }

                Text(
                    text = "Permite que ADA capture alertas entrantes de tu teléfono y las sincronice en tu bandeja privada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdaColors.TextMuted,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onOpenNativeNotificationSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (nativeNotificationAccess) {
                                AdaColors.EmeraldCalm.copy(alpha = 0.2f)
                            } else {
                                AdaColors.SkyPrimary
                            },
                            contentColor = if (nativeNotificationAccess) {
                                AdaColors.EmeraldCalm
                            } else {
                                Color(0xFF04111F)
                            },
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = if (nativeNotificationAccess) "Ajustes de acceso" else "Activar acceso",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedButton(
                        onClick = onSyncNativeNotifications,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verificar estado")
                    }
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // Registered Push Webhooks
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Webhooks externos registrados",
                style = MaterialTheme.typography.titleSmall,
                color = AdaColors.TextPrimary,
            )
            OutlinedTextField(
                value = webhookName,
                onValueChange = { webhookName = it },
                placeholder = { Text("Nombre (ej: Alertas Hogar)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it },
                    placeholder = { Text("https://tu-servidor.com/webhook") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (webhookName.isNotBlank() && webhookUrl.isNotBlank()) {
                            onAddWebhook(webhookName.trim(), webhookUrl.trim())
                            webhookName = ""
                            webhookUrl = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AdaColors.SkyPrimary,
                        contentColor = Color(0xFF04111F),
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Registrar", fontWeight = FontWeight.Bold)
                }
            }

            if (webhooks.isEmpty()) {
                Text(
                    text = "No hay webhooks externos registrados todavía.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdaColors.TextMuted,
                )
            } else {
                webhooks.forEach { wh ->
                    Surface(
                        color = AdaColors.Background.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AdaColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${wh.name} · ${if (wh.active) "Activo" else "Pausado"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (wh.active) AdaColors.EmeraldCalm else AdaColors.TextMuted,
                                )
                                Text(
                                    text = wh.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AdaColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { onToggleWebhook(wh.id, !wh.active) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PowerSettingsNew,
                                    contentDescription = if (wh.active) "Pausar webhook" else "Activar webhook",
                                    tint = if (wh.active) AdaColors.EmeraldCalm else AdaColors.TextMuted,
                                )
                            }
                            IconButton(
                                onClick = { onDeleteWebhook(wh.id) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Eliminar webhook",
                                    tint = AdaColors.RoseSoft,
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // Inbox
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bandeja recibida",
                    style = MaterialTheme.typography.titleSmall,
                    color = AdaColors.TextPrimary,
                )
                if (alerts.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAlerts,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Limpiar todo", color = AdaColors.RoseSoft)
                    }
                }
            }

            if (alerts.isEmpty()) {
                Text(
                    text = "Sin alertas recibidas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdaColors.TextMuted,
                )
            } else {
                alerts.asReversed().take(20).forEach { alert ->
                    Surface(
                        color = AdaColors.Background.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AdaColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isWa = alert.app.equals("WhatsApp", ignoreCase = true)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${alert.app} · ${alert.time}" + if (isWa) " · WhatsApp" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isWa) AdaColors.EmeraldCalm else AdaColors.AmberAccent,
                                )
                                Text(
                                    text = alert.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdaColors.TextPrimary,
                                )
                            }
                            IconButton(
                                onClick = { onDeleteAlert(alert.id) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Eliminar notificación",
                                    tint = AdaColors.RoseSoft,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
