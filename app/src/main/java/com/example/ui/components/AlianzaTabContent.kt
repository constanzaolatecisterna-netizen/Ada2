package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import com.example.data.AllianceMessage
import com.example.data.AllianceRule
import com.example.data.AvailabilityStatus
import com.example.data.ContactItem
import com.example.data.FamilyProfile
import com.example.data.RuleTrigger
import com.example.ui.theme.AdaColors
import com.example.ui.theme.parseHexColor

private enum class MessageFilter(val label: String) {
    ALL("Todos"),
    FOR_ME("Para mí"),
    UNREAD("Sin leer"),
}

private val QUICK_CHECKINS = listOf(
    "Ya llegué bien",
    "Voy en camino",
    "¿Cómo están?",
    "Llámame cuando puedas",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlianzaTabContent(
    activeProfile: FamilyProfile,
    profiles: List<FamilyProfile>,
    contacts: List<ContactItem>,
    networkCode: String,
    messages: List<AllianceMessage>,
    rules: List<AllianceRule>,
    onSendMessage: (to: String, text: String) -> Unit,
    onReadMessagesAloud: () -> Unit,
    onReadSingleMessage: (AllianceMessage) -> Unit,
    onMarkSingleRead: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onMarkMessagesRead: () -> Unit,
    onAddRule: (trigger: RuleTrigger, targetProfileId: stringAlias, text: String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onDialPhone: (String) -> Unit,
    onOpenWhatsApp: (String) -> Unit,
) {
    var msgTarget by remember { mutableStateOf("Toda la Familia") }
    var targetMenuOpen by remember { mutableStateOf(false) }
    var msgText by remember { mutableStateOf("") }
    var messageFilter by remember { mutableStateOf(MessageFilter.ALL) }

    var ruleTrigger by remember { mutableStateOf(RuleTrigger.ROUTINE_DONE) }
    var triggerMenuOpen by remember { mutableStateOf(false) }
    var ruleTargetId by remember(profiles) { mutableStateOf(profiles.firstOrNull()?.id.orEmpty()) }
    var ruleTargetMenuOpen by remember { mutableStateOf(false) }
    var ruleText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Interactive Family Status & Direct Contact Strip
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Familia conectada (toca para escribir)",
                    style = MaterialTheme.typography.titleSmall,
                    color = AdaColors.TextPrimary,
                )
                Text(
                    text = networkCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = AdaColors.SkyPrimary,
                )
            }

            Surface(
                color = AdaColors.Background.copy(alpha = 0.65f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AdaColors.Border),
            ) {
                Column {
                    profiles.forEachIndexed { idx, p ->
                        val isMe = p.id == activeProfile.id
                        val isSelectedTarget = !isMe && msgTarget.equals(p.name, ignoreCase = true)
                        val savedContact = contacts.firstOrNull {
                            it.alias.equals(p.name, ignoreCase = true) && it.phone.isNotBlank()
                        }
                        val statusColor = when (p.availabilityStatus) {
                            AvailabilityStatus.DISPONIBLE -> AdaColors.EmeraldCalm
                            AvailabilityStatus.OCUPADO -> AdaColors.AmberAccent
                            AvailabilityStatus.ESTUDIANDO -> AdaColors.SkyPrimary
                            AvailabilityStatus.DESCANSANDO -> Color(0xFFA78BFA)
                            AvailabilityStatus.EN_SILENCIO -> AdaColors.RoseSoft
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelectedTarget) AdaColors.SkyPrimary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable(enabled = !isMe) { msgTarget = p.name }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(p.colorHex))
                                )
                                Column {
                                    Text(
                                        text = if (isMe) "${p.name} (tú)" else "${p.name} · ${p.role}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AdaColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = p.availability,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                    )
                                }
                            }
                            if (!isMe) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (savedContact != null) {
                                        IconButton(
                                            onClick = { onDialPhone(savedContact.phone) },
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Call,
                                                contentDescription = "Llamar a ${p.name}",
                                                tint = AdaColors.SkyPrimary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { onOpenWhatsApp(savedContact.phone) },
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ChatBubbleOutline,
                                                contentDescription = "WhatsApp a ${p.name}",
                                                tint = AdaColors.EmeraldCalm,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { msgTarget = p.name },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = "Escribir a ${p.name}",
                                            tint = if (isSelectedTarget) AdaColors.SkyPrimary else AdaColors.TextMuted,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (idx < profiles.lastIndex) {
                            HorizontalDivider(color = AdaColors.Border.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // Send Alliance Message + 1-Tap Quick Check-ins
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Enviar aviso a la Alianza",
                style = MaterialTheme.typography.titleSmall,
                color = AdaColors.TextPrimary,
            )

            Box {
                OutlinedButton(
                    onClick = { targetMenuOpen = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = "Para: $msgTarget",
                        color = AdaColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = AdaColors.TextMuted)
                }
                DropdownMenu(
                    expanded = targetMenuOpen,
                    onDismissRequest = { targetMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Toda la Familia") },
                        onClick = {
                            msgTarget = "Toda la Familia"
                            targetMenuOpen = false
                        },
                    )
                    profiles.filter { it.id != activeProfile.id }.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.name) },
                            onClick = {
                                msgTarget = member.name
                                targetMenuOpen = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QUICK_CHECKINS.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = { onSendMessage(msgTarget, preset) },
                        label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = AdaColors.Background.copy(alpha = 0.75f),
                            labelColor = AdaColors.SkyPrimary,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = msgText,
                    onValueChange = { msgText = it },
                    placeholder = { Text("Escribe a $msgTarget...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("alliance_message_input"),
                )
                Button(
                    onClick = {
                        if (msgText.isNotBlank()) {
                            onSendMessage(msgTarget, msgText.trim())
                            msgText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AdaColors.SkyPrimary,
                        contentColor = Color(0xFF04111F),
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("send_alliance_message_button"),
                ) {
                    Text("Enviar", fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // Filterable Rich Messages Feed
        val filteredMessages = remember(messages, messageFilter, activeProfile.name) {
            when (messageFilter) {
                MessageFilter.ALL -> messages
                MessageFilter.FOR_ME -> messages.filter {
                    it.toName.equals(activeProfile.name, ignoreCase = true) ||
                        it.toName.equals("Toda la Familia", ignoreCase = true)
                }
                MessageFilter.UNREAD -> messages.filter {
                    it.fromName != activeProfile.name && it.status != "Leído"
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Canal familiar",
                    style = MaterialTheme.typography.titleSmall,
                    color = AdaColors.TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onReadMessagesAloud,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = AdaColors.SkyPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Leer todo", color = AdaColors.SkyPrimary)
                    }
                    TextButton(
                        onClick = onMarkMessagesRead,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = null,
                            tint = AdaColors.EmeraldCalm,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Leídos", color = AdaColors.TextSecondary)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MessageFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = messageFilter == filter,
                        onClick = { messageFilter = filter },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = AdaColors.Background.copy(alpha = 0.6f),
                            selectedContainerColor = AdaColors.SkyPrimary.copy(alpha = 0.22f),
                            labelColor = AdaColors.TextMuted,
                            selectedLabelColor = AdaColors.SkyPrimary,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            if (filteredMessages.isEmpty()) {
                Text(
                    text = "Sin mensajes en este filtro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdaColors.TextMuted,
                )
            } else {
                filteredMessages.asReversed().take(15).forEach { m ->
                    val senderColor = profiles.firstOrNull {
                        it.name.equals(m.fromName, ignoreCase = true)
                    }?.let { parseHexColor(it.colorHex) } ?: AdaColors.SkyPrimary
                    val isUnreadForMe = m.fromName != activeProfile.name && m.status != "Leído"

                    Surface(
                        color = if (isUnreadForMe) {
                            AdaColors.SkyPrimary.copy(alpha = 0.10f)
                        } else {
                            AdaColors.Background.copy(alpha = 0.65f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isUnreadForMe) AdaColors.SkyPrimary.copy(alpha = 0.45f) else AdaColors.Border
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(senderColor)
                                    )
                                    Text(
                                        text = "${m.fromName} → ${m.toName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AdaColors.SkyPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "${m.time} · ${if (isUnreadForMe) "Nuevo" else m.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUnreadForMe) AdaColors.EmeraldCalm else AdaColors.TextMuted,
                                )
                            }
                            Text(
                                text = m.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AdaColors.TextPrimary,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                TextButton(
                                    onClick = { onReadSingleMessage(m) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Escuchar", style = MaterialTheme.typography.labelSmall)
                                }
                                if (m.fromName != activeProfile.name && !m.fromName.equals("ADA", ignoreCase = true)) {
                                    TextButton(
                                        onClick = { msgTarget = m.fromName },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Responder", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (isUnreadForMe) {
                                    TextButton(
                                        onClick = { onMarkSingleRead(m.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Icon(Icons.Rounded.MarkChatRead, contentDescription = null, modifier = Modifier.size(15.dp), tint = AdaColors.EmeraldCalm)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Leído", style = MaterialTheme.typography.labelSmall, color = AdaColors.EmeraldCalm)
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteMessage(m.id) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Eliminar mensaje",
                                        tint = AdaColors.RoseSoft,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // Cross-Profile Automatic Rules
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Reglas automáticas entre perfiles",
                style = MaterialTheme.typography.titleSmall,
                color = AdaColors.TextPrimary,
            )

            Box {
                OutlinedButton(
                    onClick = { triggerMenuOpen = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(ruleTrigger.label, color = AdaColors.TextPrimary, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = AdaColors.TextMuted)
                }
                DropdownMenu(
                    expanded = triggerMenuOpen,
                    onDismissRequest = { triggerMenuOpen = false },
                ) {
                    RuleTrigger.entries.forEach { trig ->
                        DropdownMenuItem(
                            text = { Text(trig.label) },
                            onClick = {
                                ruleTrigger = trig
                                triggerMenuOpen = false
                            },
                        )
                    }
                }
            }

            Box {
                val targetName = profiles.firstOrNull { it.id == ruleTargetId }?.name ?: "Familia"
                OutlinedButton(
                    onClick = { ruleTargetMenuOpen = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Avisar a $targetName", color = AdaColors.TextPrimary, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = AdaColors.TextMuted)
                }
                DropdownMenu(
                    expanded = ruleTargetMenuOpen,
                    onDismissRequest = { ruleTargetMenuOpen = false },
                ) {
                    profiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("Avisar a ${p.name}") },
                            onClick = {
                                ruleTargetId = p.id
                                ruleTargetMenuOpen = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = ruleText,
                    onValueChange = { ruleText = it },
                    placeholder = { Text("Aviso automático...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = {
                        if (ruleText.isNotBlank() && ruleTargetId.isNotBlank()) {
                            onAddRule(ruleTrigger, ruleTargetId, ruleText.trim())
                            ruleText = ""
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = AdaColors.IndigoSecondary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Agregar regla")
                }
            }

            rules.forEach { rule ->
                val targetName = profiles.firstOrNull { it.id == rule.targetProfileId }?.name ?: "Familia"
                val triggerLabel = RuleTrigger.fromWire(rule.trigger).label
                Surface(
                    color = AdaColors.Background.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AdaColors.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "$triggerLabel → $targetName: “${rule.text}”",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdaColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onDeleteRule(rule.id) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Quitar regla",
                                tint = AdaColors.RoseSoft,
                            )
                        }
                    }
                }
            }
        }
    }
}

private typealias stringAlias = String
