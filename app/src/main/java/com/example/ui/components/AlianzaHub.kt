package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.FamilyProfile
import com.example.data.HubTab
import com.example.data.RoutineItem
import com.example.data.RuleTrigger
import com.example.ui.theme.AdaColors
import com.example.viewmodel.AlianzaCloudSnapshot
import com.example.viewmodel.AlianzaLocalUiState

@Composable
fun AlianzaHub(
    snapshot: AlianzaCloudSnapshot,
    activeProfile: FamilyProfile,
    local: AlianzaLocalUiState,
    userEmail: String?,
    onSelectTab: (HubTab) -> Unit,
    onSendMessage: (to: String, text: String) -> Unit,
    onReadMessagesAloud: () -> Unit,
    onReadSingleMessage: (com.example.data.AllianceMessage) -> Unit,
    onMarkSingleRead: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onMarkMessagesRead: () -> Unit,
    onAddRule: (trigger: RuleTrigger, targetProfileId: String, text: String) -> Unit,
    onDeleteRule: (String) -> Unit,
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
    onAddRoutine: (String) -> Unit,
    onToggleRoutine: (RoutineItem) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onAddReminder: (time: String, text: String) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onAddContact: (alias: String, phone: String) -> Unit,
    onDeleteContact: (String) -> Unit,
    onDialPhone: (String) -> Unit,
    onOpenWhatsApp: (String) -> Unit,
    onStartCalm: () -> Unit,
    onResetCalm: () -> Unit,
    onRefreshWeather: () -> Unit,
    onChangeNetworkCode: (String) -> Unit,
    onDeleteActiveProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val myRoutines = snapshot.routines.filter { it.profileId == activeProfile.id }
    val myReminders = snapshot.reminders.filter { it.profileId == activeProfile.id }
    val myContacts = snapshot.contacts.filter { it.profileId == activeProfile.id }
    val unreadMessagesCount = snapshot.messages.count {
        it.fromName != activeProfile.name && it.status != "Leído"
    }

    Surface(
        color = AdaColors.Surface.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, AdaColors.Border),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = local.selectedTab.ordinal,
                containerColor = AdaColors.Background.copy(alpha = 0.75f),
                contentColor = AdaColors.SkyPrimary,
                edgePadding = 8.dp,
                divider = {},
            ) {
                HubTab.entries.forEach { tab ->
                    val selected = tab == local.selectedTab
                    val icon = when (tab) {
                        HubTab.ALIANZA -> Icons.Rounded.Groups
                        HubTab.PUSH -> Icons.Rounded.Notifications
                        HubTab.RUTINAS -> Icons.Rounded.Checklist
                        HubTab.CALMA -> Icons.Rounded.SelfImprovement
                    }
                    val tabTitle = if (tab == HubTab.ALIANZA && unreadMessagesCount > 0) {
                        "${tab.label} ($unreadMessagesCount)"
                    } else {
                        tab.label
                    }
                    Tab(
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                        text = { Text(tabTitle, style = MaterialTheme.typography.labelLarge) },
                        icon = { Icon(icon, contentDescription = null) },
                        selectedContentColor = AdaColors.SkyPrimary,
                        unselectedContentColor = AdaColors.TextMuted,
                        modifier = Modifier.testTag("hub_tab_${tab.name.lowercase()}"),
                    )
                }
            }

            HorizontalDivider(color = AdaColors.Border)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (local.selectedTab) {
                    HubTab.ALIANZA -> AlianzaTabContent(
                        activeProfile = activeProfile,
                        profiles = snapshot.profiles,
                        contacts = myContacts,
                        networkCode = snapshot.networkCode,
                        messages = snapshot.messages,
                        rules = snapshot.rules,
                        onSendMessage = onSendMessage,
                        onReadMessagesAloud = onReadMessagesAloud,
                        onReadSingleMessage = onReadSingleMessage,
                        onMarkSingleRead = onMarkSingleRead,
                        onDeleteMessage = onDeleteMessage,
                        onMarkMessagesRead = onMarkMessagesRead,
                        onAddRule = onAddRule,
                        onDeleteRule = onDeleteRule,
                        onDialPhone = onDialPhone,
                        onOpenWhatsApp = onOpenWhatsApp,
                    )

                    HubTab.PUSH -> AlertasTabContent(
                        alerts = snapshot.alerts,
                        webhooks = snapshot.webhooks,
                        nativeNotificationAccess = local.nativeNotificationAccess,
                        autoReadWhatsApp = local.autoReadWhatsApp,
                        onToggleAutoReadWhatsApp = onToggleAutoReadWhatsApp,
                        onReadAllNotifications = onReadAllNotifications,
                        onCaptureClipboard = onCaptureClipboard,
                        onTestAlert = onTestAlert,
                        onDeleteAlert = onDeleteAlert,
                        onClearAlerts = onClearAlerts,
                        onOpenNativeNotificationSettings = onOpenNativeNotificationSettings,
                        onSyncNativeNotifications = onSyncNativeNotifications,
                        onAddWebhook = onAddWebhook,
                        onToggleWebhook = onToggleWebhook,
                        onDeleteWebhook = onDeleteWebhook,
                    )

                    HubTab.RUTINAS -> RutinasTabContent(
                        routines = myRoutines,
                        reminders = myReminders,
                        contacts = myContacts,
                        onAddRoutine = onAddRoutine,
                        onToggleRoutine = onToggleRoutine,
                        onDeleteRoutine = onDeleteRoutine,
                        onAddReminder = onAddReminder,
                        onDeleteReminder = onDeleteReminder,
                        onAddContact = onAddContact,
                        onDeleteContact = onDeleteContact,
                        onDialPhone = onDialPhone,
                        onOpenWhatsApp = onOpenWhatsApp,
                    )

                    HubTab.CALMA -> CalmaRedTabContent(
                        activeProfile = activeProfile,
                        profileCount = snapshot.profiles.size,
                        networkCode = snapshot.networkCode,
                        userEmail = userEmail,
                        calmSeconds = local.calmSeconds,
                        calmRunning = local.calmRunning,
                        weatherDetail = local.weatherDetail,
                        onStartCalm = onStartCalm,
                        onResetCalm = onResetCalm,
                        onRefreshWeather = onRefreshWeather,
                        onChangeNetworkCode = onChangeNetworkCode,
                        onDeleteActiveProfile = onDeleteActiveProfile,
                        onSignOut = onSignOut,
                    )
                }
            }
        }
    }
}
