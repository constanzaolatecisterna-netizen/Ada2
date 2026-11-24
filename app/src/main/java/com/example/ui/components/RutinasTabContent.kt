package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ContactItem
import com.example.data.ReminderItem
import com.example.data.RoutineItem
import com.example.ui.theme.AdaColors

@Composable
fun RutinasTabContent(
    routines: List<RoutineItem>,
    reminders: List<ReminderItem>,
    contacts: List<ContactItem>,
    onAddRoutine: (String) -> Unit,
    onToggleRoutine: (RoutineItem) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onAddReminder: (time: String, text: String) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onAddContact: (alias: String, phone: String) -> Unit,
    onDeleteContact: (String) -> Unit,
    onDialPhone: (String) -> Unit,
    onOpenWhatsApp: (String) -> Unit,
) {
    var routineText by remember { mutableStateOf("") }
    var remTime by remember { mutableStateOf("09:00") }
    var remText by remember { mutableStateOf("") }
    var contactAlias by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Routines & Medication Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Rutinas y medicamentos",
                style = MaterialTheme.typography.titleSmall,
                color = AdaColors.TextPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = routineText,
                    onValueChange = { routineText = it },
                    placeholder = { Text("Ej: Tomar pastilla, leer 20m...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("routine_input"),
                )
                Button(
                    onClick = {
                        if (routineText.isNotBlank()) {
                            onAddRoutine(routineText.trim())
                            routineText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AdaColors.EmeraldCalm,
                        contentColor = Color(0xFF031A12),
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("add_routine_button"),
                ) {
                    Text("Agregar", fontWeight = FontWeight.Bold)
                }
            }

            if (routines.isEmpty()) {
                Text(
                    text = "Sin rutinas todavía.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdaColors.TextMuted,
                )
            } else {
                routines.forEach { rt ->
                    Surface(
                        color = AdaColors.Background.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AdaColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleRoutine(rt) }
                                .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = rt.done,
                                onCheckedChange = { onToggleRoutine(rt) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AdaColors.EmeraldCalm,
                                    uncheckedColor = AdaColors.TextMuted,
                                    checkmarkColor = Color(0xFF031A12),
                                ),
                            )
                            Text(
                                text = rt.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (rt.done) TextDecoration.LineThrough else null
                                ),
                                color = if (rt.done) AdaColors.TextMuted else AdaColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onDeleteRoutine(rt.id) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Quitar rutina",
                                    tint = AdaColors.RoseSoft,
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // Voice Reminders
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Recordatorios por voz",
                style = MaterialTheme.typography.titleSmall,
                color = AdaColors.TextPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = remTime,
                    onValueChange = { remTime = it.take(5) },
                    label = { Text("HH:MM") },
                    singleLine = true,
                    modifier = Modifier.width(96.dp),
                )
                OutlinedTextField(
                    value = remText,
                    onValueChange = { remText = it },
                    placeholder = { Text("Ej: Llamar a abuelos...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = {
                    if (remText.isNotBlank() && remTime.isNotBlank()) {
                        onAddReminder(remTime.trim(), remText.trim())
                        remText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AdaColors.PinkAccent,
                    contentColor = Color(0xFF1F0614),
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("add_reminder_button"),
            ) {
                Text("Programar recordatorio", fontWeight = FontWeight.Bold)
            }

            if (reminders.isEmpty()) {
                Text(
                    text = "Sin recordatorios programados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdaColors.TextMuted,
                )
            } else {
                reminders.forEach { rem ->
                    Surface(
                        color = AdaColors.Background.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AdaColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${rem.time} · ",
                                style = MaterialTheme.typography.labelSmall,
                                color = AdaColors.PinkAccent,
                            )
                            Text(
                                text = rem.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AdaColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onDeleteReminder(rem.id) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Quitar recordatorio",
                                    tint = AdaColors.RoseSoft,
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // Quick Contacts
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Contactos rápidos",
                style = MaterialTheme.typography.titleSmall,
                color = AdaColors.TextPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = contactAlias,
                    onValueChange = { contactAlias = it },
                    placeholder = { Text("Apodo (Mamá)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    placeholder = { Text("+569...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = {
                    if (contactAlias.isNotBlank()) {
                        onAddContact(contactAlias.trim(), contactPhone.trim())
                        contactAlias = ""
                        contactPhone = ""
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
                Text("Guardar contacto", fontWeight = FontWeight.Bold)
            }

            if (contacts.isEmpty()) {
                Text(
                    text = "Sin contactos guardados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdaColors.TextMuted,
                )
            } else {
                contacts.forEach { contact ->
                    Surface(
                        color = AdaColors.Background.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AdaColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.alias,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AdaColors.SkyPrimary,
                                )
                                Text(
                                    text = contact.phone.ifBlank { "Sin número" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdaColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (contact.phone.isNotBlank()) {
                                TextButton(
                                    onClick = { onDialPhone(contact.phone) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = "Llamar a ${contact.alias}",
                                        tint = AdaColors.EmeraldCalm,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Llamar", color = AdaColors.EmeraldCalm)
                                }
                                TextButton(
                                    onClick = { onOpenWhatsApp(contact.phone) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubbleOutline,
                                        contentDescription = "WhatsApp a ${contact.alias}",
                                        tint = AdaColors.SkyPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeleteContact(contact.id) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Eliminar contacto ${contact.alias}",
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
