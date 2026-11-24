package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.provider.Settings
import com.example.data.AvailabilityStatus
import com.example.data.CommChannel
import com.example.data.FamilyProfile
import com.example.data.QuickComm
import com.example.data.UiState
import com.example.floating.AdaFloatingService
import com.example.notifications.AdaNotificationListenerService
import com.example.ui.components.AlianzaHub
import com.example.ui.components.OrbStage
import com.example.ui.components.ProfileModal
import com.example.ui.theme.AdaColors
import com.example.ui.theme.parseHexColor
import com.example.viewmodel.AlianzaCloudSnapshot
import com.example.viewmodel.AlianzaLocalUiState
import com.example.viewmodel.AlianzaViewModel
import com.example.voice.NoiseFilterMode

@Composable
fun AdaMainScreen(
    viewModel: AlianzaViewModel,
    userEmail: String?,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiModel by viewModel.uiState.collectAsStateWithLifecycle()

    var profileModalOpen by rememberSaveable { mutableStateOf(false) }
    var mobileHubOpen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNativeNotificationAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val deferred = pendingMicAction
        pendingMicAction = null
        if (granted) {
            if (deferred != null) deferred() else viewModel.startVoiceListening()
        }
    }

    val coarseLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshWeather(speakResult = true)
    }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val runWithMicPermission: (() -> Unit) -> Unit = { action ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingMicAction = action
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val handleVoiceClick = {
        runWithMicPermission { viewModel.startVoiceListening() }
    }

    val handleToggleWakeWord: () -> Unit = {
        val next = !uiModel.local.wakeWordEnabled
        if (next) {
            runWithMicPermission { viewModel.setWakeWordEnabled(true) }
        } else {
            viewModel.setWakeWordEnabled(false)
        }
    }

    val handleToggleFloatingAda: () -> Unit = {
        if (uiModel.local.floatingActive) {
            AdaFloatingService.stop(context)
        } else if (Settings.canDrawOverlays(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            AdaFloatingService.start(context)
        } else {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(overlayIntent)
        }
    }

    val handleEnrollVoicePrint: (String) -> Unit = { profileId ->
        runWithMicPermission { viewModel.enrollVoicePrint(profileId) }
    }

    val handleWeatherRefresh = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.refreshWeather(speakResult = true)
        } else {
            coarseLocationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val openUri: (String, String) -> Unit = { action, uri ->
        runCatching {
            val intent = Intent(action, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    val handleOpenQuickComm: (QuickComm) -> Unit = { comm ->
        val action = if (comm.channel == CommChannel.CALL) {
            Intent.ACTION_DIAL
        } else {
            Intent.ACTION_VIEW
        }
        openUri(action, comm.uriString)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0E203C), AdaColors.Background),
                    radius = 1200f,
                )
            )
            .safeDrawingPadding()
    ) {
        when (val cloud = uiModel.cloudState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = AdaColors.SkyPrimary)
                    Text(
                        text = "Sincronizando Red Alianza...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AdaColors.TextMuted,
                    )
                }
            }

            is UiState.Error -> {
                Surface(
                    color = AdaColors.Surface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, AdaColors.RoseAlert.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "No se pudo cargar tu red familiar",
                            style = MaterialTheme.typography.titleMedium,
                            color = AdaColors.RoseSoft,
                        )
                        Text(
                            text = cloud.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = AdaColors.TextSecondary,
                        )
                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(containerColor = AdaColors.SkyPrimary),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Cerrar sesión", color = Color(0xFF04111F))
                        }
                    }
                }
            }

            is UiState.Success -> {
                val snapshot = cloud.data
                val activeProfile = snapshot.profiles.firstOrNull {
                    it.id == snapshot.activeProfileId
                } ?: snapshot.profiles.first()

                AdaDashboardContent(
                    snapshot = snapshot,
                    activeProfile = activeProfile,
                    local = uiModel.local,
                    userEmail = userEmail,
                    mobileHubOpen = mobileHubOpen,
                    onToggleMobileHub = { mobileHubOpen = !mobileHubOpen },
                    onOpenProfileModal = { profileModalOpen = true },
                    onSelectAvailability = viewModel::setAvailability,
                    onOrbClick = handleVoiceClick,
                    onToggleWakeWord = handleToggleWakeWord,
                    onCycleNoiseFilter = {
                        val next = if (uiModel.local.noiseFilterMode == NoiseFilterMode.STRICT) {
                            NoiseFilterMode.BALANCED
                        } else {
                            NoiseFilterMode.STRICT
                        }
                        viewModel.setNoiseFilterMode(next)
                    },
                    onToggleFloatingAda = handleToggleFloatingAda,
                    onToggleAutoReadWhatsApp = {
                        val next = !uiModel.local.autoReadWhatsApp
                        if (next && !uiModel.local.nativeNotificationAccess) {
                            AdaNotificationListenerService.openNotificationListenerSettings(context)
                        }
                        viewModel.setAutoReadWhatsApp(next)
                    },
                    onOpenVoiceProfiles = { profileModalOpen = true },
                    onToggleFluidMode = viewModel::toggleFluidMode,
                    onRepeatSpeech = viewModel::repeatSpeech,
                    onStopSpeech = viewModel::stopSpeech,
                    onDismissError = viewModel::dismissError,
                    onSubmitCommand = { cmd -> viewModel.submitCommand(cmd, fromVoice = false) },
                    onOpenQuickComm = handleOpenQuickComm,
                    onCloseQuickComm = viewModel::closeQuickComm,
                    onDismissBanner = viewModel::dismissBanner,
                    onOpenAlianzaInbox = {
                        viewModel.selectTab(com.example.data.HubTab.ALIANZA)
                        mobileHubOpen = true
                    },
                    onReadMessagesAloud = viewModel::readAllianceMessagesAloud,
                    onReadSingleMessage = viewModel::readSingleAllianceMessage,
                    onMarkSingleRead = viewModel::markMessageRead,
                    onDeleteMessage = viewModel::deleteAllianceMessage,
                    onSelectTab = viewModel::selectTab,
                    onSendMessage = viewModel::sendAllianceMessage,
                    onMarkMessagesRead = viewModel::markAllianceMessagesRead,
                    onAddRule = viewModel::addRule,
                    onDeleteRule = viewModel::deleteRule,
                    onToggleAutoReadWhatsAppSwitch = viewModel::setAutoReadWhatsApp,
                    onReadAllNotifications = viewModel::readNotificationsAloud,
                    onCaptureClipboard = viewModel::addClipboardAlert,
                    onTestAlert = viewModel::sendTestAlert,
                    onDeleteAlert = viewModel::deleteAlert,
                    onClearAlerts = viewModel::clearAllAlerts,
                    onOpenNativeNotificationSettings = {
                        AdaNotificationListenerService.openNotificationListenerSettings(context)
                    },
                    onSyncNativeNotifications = viewModel::refreshNativeNotificationAccess,
                    onAddWebhook = viewModel::addWebhook,
                    onToggleWebhook = viewModel::toggleWebhook,
                    onDeleteWebhook = viewModel::deleteWebhook,
                    onAddRoutine = viewModel::addRoutine,
                    onToggleRoutine = viewModel::toggleRoutine,
                    onDeleteRoutine = viewModel::deleteRoutine,
                    onAddReminder = { time, text ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.addReminder(time, text)
                    },
                    onDeleteReminder = viewModel::deleteReminder,
                    onAddContact = viewModel::addContact,
                    onDeleteContact = viewModel::deleteContact,
                    onDialPhone = { phone -> openUri(Intent.ACTION_DIAL, "tel:$phone") },
                    onOpenWhatsApp = { phone ->
                        val digits = phone.filter { it.isDigit() }
                        openUri(Intent.ACTION_VIEW, "https://wa.me/$digits")
                    },
                    onStartCalm = viewModel::startCalmMode,
                    onResetCalm = viewModel::resetCalmMode,
                    onRefreshWeather = handleWeatherRefresh,
                    onChangeNetworkCode = viewModel::updateNetworkCode,
                    onDeleteActiveProfile = viewModel::deleteActiveProfile,
                    onSignOut = onSignOut,
                )

                ProfileModal(
                    open = profileModalOpen,
                    profiles = snapshot.profiles,
                    activeProfileId = activeProfile.id,
                    networkCode = snapshot.networkCode,
                    enrolledVoiceProfileIds = uiModel.local.enrolledVoiceProfileIds,
                    enrollingProfileId = uiModel.local.enrollingProfileId,
                    enrollmentAudioLevel = uiModel.local.enrollmentAudioLevel,
                    lastSpeaker = uiModel.local.lastSpeaker,
                    onClose = { profileModalOpen = false },
                    onSelectProfile = viewModel::selectProfile,
                    onCreateProfile = viewModel::createProfile,
                    onChangeNetworkCode = viewModel::updateNetworkCode,
                    onEnrollVoicePrint = handleEnrollVoicePrint,
                    onClearVoicePrint = viewModel::clearVoicePrint,
                )
            }
        }
    }
}

@Composable
private fun AdaDashboardContent(
    snapshot: AlianzaCloudSnapshot,
    activeProfile: FamilyProfile,
    local: AlianzaLocalUiState,
    userEmail: String?,
    mobileHubOpen: Boolean,
    onToggleMobileHub: () -> Unit,
    onOpenProfileModal: () -> Unit,
    onSelectAvailability: (AvailabilityStatus) -> Unit,
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
    onDismissBanner: () -> Unit,
    onOpenAlianzaInbox: () -> Unit,
    onReadMessagesAloud: () -> Unit,
    onReadSingleMessage: (com.example.data.AllianceMessage) -> Unit,
    onMarkSingleRead: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onSelectTab: (com.example.data.HubTab) -> Unit,
    onSendMessage: (String, String) -> Unit,
    onMarkMessagesRead: () -> Unit,
    onAddRule: (com.example.data.RuleTrigger, String, String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleAutoReadWhatsAppSwitch: (Boolean) -> Unit,
    onReadAllNotifications: () -> Unit,
    onCaptureClipboard: (String) -> Unit,
    onTestAlert: () -> Unit,
    onDeleteAlert: (String) -> Unit,
    onClearAlerts: () -> Unit,
    onOpenNativeNotificationSettings: () -> Unit,
    onSyncNativeNotifications: () -> Unit,
    onAddWebhook: (String, String) -> Unit,
    onToggleWebhook: (String, Boolean) -> Unit,
    onDeleteWebhook: (String) -> Unit,
    onAddRoutine: (String) -> Unit,
    onToggleRoutine: (com.example.data.RoutineItem) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onAddReminder: (String, String) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onAddContact: (String, String) -> Unit,
    onDeleteContact: (String) -> Unit,
    onDialPhone: (String) -> Unit,
    onOpenWhatsApp: (String) -> Unit,
    onStartCalm: () -> Unit,
    onResetCalm: () -> Unit,
    onRefreshWeather: () -> Unit,
    onChangeNetworkCode: (String) -> Unit,
    onDeleteActiveProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    val unreadMessages = snapshot.messages.filter {
        it.fromName != activeProfile.name && it.status != "Leído"
    }
    val pendingRoutinesCount = snapshot.routines.count {
        it.profileId == activeProfile.id && !it.done
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        if (!isExpanded && mobileHubOpen) {
            BackHandler(onBack = onToggleMobileHub)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TopHeaderBar(
                activeProfile = activeProfile,
                isExpanded = isExpanded,
                mobileHubOpen = mobileHubOpen,
                hasAlerts = unreadMessages.isNotEmpty() || snapshot.alerts.isNotEmpty(),
                onOpenProfileModal = onOpenProfileModal,
                onSelectAvailability = onSelectAvailability,
                onToggleMobileHub = onToggleMobileHub,
            )

            if (unreadMessages.isNotEmpty() && !local.bannerDismissed) {
                val latest = unreadMessages.last()
                Surface(
                    color = AdaColors.SkyPrimary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AdaColors.SkyPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${latest.fromName}: ${latest.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AdaColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onReadMessagesAloud,
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Escuchar",
                                tint = AdaColors.SkyPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Escuchar",
                                color = AdaColors.SkyPrimary,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        TextButton(
                            onClick = onOpenAlianzaInbox,
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = "Responder",
                                color = AdaColors.EmeraldCalm,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        IconButton(
                            onClick = onDismissBanner,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cerrar aviso",
                                tint = AdaColors.TextMuted,
                            )
                        }
                    }
                }
            }

            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OrbStage(
                        orbMode = local.orbMode,
                        isListening = local.isListening,
                        isThinking = local.isThinking,
                        isSpeaking = local.isSpeaking,
                        speechPulseTick = local.speechPulseTick,
                        fluidMode = local.fluidMode,
                        wakeWordEnabled = local.wakeWordEnabled,
                        wakeWordArmed = local.wakeWordArmed,
                        noiseFilterMode = local.noiseFilterMode,
                        floatingActive = local.floatingActive,
                        autoReadWhatsApp = local.autoReadWhatsApp,
                        lastSpeaker = local.lastSpeaker,
                        checkPulse = local.checkPulse,
                        transcriptPreview = local.transcriptPreview,
                        responseText = local.responseText,
                        errorText = local.errorText,
                        chatHistory = local.chatHistory,
                        quickComm = local.quickComm,
                        weatherSummary = local.weatherSummary,
                        pendingCount = pendingRoutinesCount,
                        unreadCount = unreadMessages.size,
                        onOrbClick = onOrbClick,
                        onToggleWakeWord = onToggleWakeWord,
                        onCycleNoiseFilter = onCycleNoiseFilter,
                        onToggleFloatingAda = onToggleFloatingAda,
                        onToggleAutoReadWhatsApp = onToggleAutoReadWhatsApp,
                        onOpenVoiceProfiles = onOpenVoiceProfiles,
                        onToggleFluidMode = onToggleFluidMode,
                        onRepeatSpeech = onRepeatSpeech,
                        onStopSpeech = onStopSpeech,
                        onDismissError = onDismissError,
                        onSubmitCommand = onSubmitCommand,
                        onOpenQuickComm = onOpenQuickComm,
                        onCloseQuickComm = onCloseQuickComm,
                        modifier = Modifier
                            .weight(0.46f)
                            .verticalScroll(rememberScrollState()),
                    )

                    AlianzaHub(
                        snapshot = snapshot,
                        activeProfile = activeProfile,
                        local = local,
                        userEmail = userEmail,
                        onSelectTab = onSelectTab,
                        onSendMessage = onSendMessage,
                        onReadMessagesAloud = onReadMessagesAloud,
                        onReadSingleMessage = onReadSingleMessage,
                        onMarkSingleRead = onMarkSingleRead,
                        onDeleteMessage = onDeleteMessage,
                        onMarkMessagesRead = onMarkMessagesRead,
                        onAddRule = onAddRule,
                        onDeleteRule = onDeleteRule,
                        onToggleAutoReadWhatsApp = onToggleAutoReadWhatsAppSwitch,
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
                        onAddRoutine = onAddRoutine,
                        onToggleRoutine = onToggleRoutine,
                        onDeleteRoutine = onDeleteRoutine,
                        onAddReminder = onAddReminder,
                        onDeleteReminder = onDeleteReminder,
                        onAddContact = onAddContact,
                        onDeleteContact = onDeleteContact,
                        onDialPhone = onDialPhone,
                        onOpenWhatsApp = onOpenWhatsApp,
                        onStartCalm = onStartCalm,
                        onResetCalm = onResetCalm,
                        onRefreshWeather = onRefreshWeather,
                        onChangeNetworkCode = onChangeNetworkCode,
                        onDeleteActiveProfile = onDeleteActiveProfile,
                        onSignOut = onSignOut,
                        modifier = Modifier.weight(0.54f),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (mobileHubOpen) {
                        AlianzaHub(
                            snapshot = snapshot,
                            activeProfile = activeProfile,
                            local = local,
                            userEmail = userEmail,
                            onSelectTab = onSelectTab,
                            onSendMessage = onSendMessage,
                            onReadMessagesAloud = onReadMessagesAloud,
                            onReadSingleMessage = onReadSingleMessage,
                            onMarkSingleRead = onMarkSingleRead,
                            onDeleteMessage = onDeleteMessage,
                            onMarkMessagesRead = onMarkMessagesRead,
                            onAddRule = onAddRule,
                            onDeleteRule = onDeleteRule,
                            onToggleAutoReadWhatsApp = onToggleAutoReadWhatsAppSwitch,
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
                            onAddRoutine = onAddRoutine,
                            onToggleRoutine = onToggleRoutine,
                            onDeleteRoutine = onDeleteRoutine,
                            onAddReminder = onAddReminder,
                            onDeleteReminder = onDeleteReminder,
                            onAddContact = onAddContact,
                            onDeleteContact = onDeleteContact,
                            onDialPhone = onDialPhone,
                            onOpenWhatsApp = onOpenWhatsApp,
                            onStartCalm = onStartCalm,
                            onResetCalm = onResetCalm,
                            onRefreshWeather = onRefreshWeather,
                            onChangeNetworkCode = onChangeNetworkCode,
                            onDeleteActiveProfile = onDeleteActiveProfile,
                            onSignOut = onSignOut,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        OrbStage(
                            orbMode = local.orbMode,
                            isListening = local.isListening,
                            isThinking = local.isThinking,
                            isSpeaking = local.isSpeaking,
                            speechPulseTick = local.speechPulseTick,
                            fluidMode = local.fluidMode,
                            wakeWordEnabled = local.wakeWordEnabled,
                            wakeWordArmed = local.wakeWordArmed,
                            noiseFilterMode = local.noiseFilterMode,
                            floatingActive = local.floatingActive,
                            autoReadWhatsApp = local.autoReadWhatsApp,
                            lastSpeaker = local.lastSpeaker,
                            checkPulse = local.checkPulse,
                            transcriptPreview = local.transcriptPreview,
                            responseText = local.responseText,
                            errorText = local.errorText,
                            chatHistory = local.chatHistory,
                            quickComm = local.quickComm,
                            weatherSummary = local.weatherSummary,
                            pendingCount = pendingRoutinesCount,
                            unreadCount = unreadMessages.size,
                            onOrbClick = onOrbClick,
                            onToggleWakeWord = onToggleWakeWord,
                            onCycleNoiseFilter = onCycleNoiseFilter,
                            onToggleFloatingAda = onToggleFloatingAda,
                            onToggleAutoReadWhatsApp = onToggleAutoReadWhatsApp,
                            onOpenVoiceProfiles = onOpenVoiceProfiles,
                            onToggleFluidMode = onToggleFluidMode,
                            onRepeatSpeech = onRepeatSpeech,
                            onStopSpeech = onStopSpeech,
                            onDismissError = onDismissError,
                            onSubmitCommand = onSubmitCommand,
                            onOpenQuickComm = onOpenQuickComm,
                            onCloseQuickComm = onCloseQuickComm,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopHeaderBar(
    activeProfile: FamilyProfile,
    isExpanded: Boolean,
    mobileHubOpen: Boolean,
    hasAlerts: Boolean,
    onOpenProfileModal: () -> Unit,
    onSelectAvailability: (AvailabilityStatus) -> Unit,
    onToggleMobileHub: () -> Unit,
) {
    var statusMenuOpen by remember { mutableStateOf(false) }

    Surface(
        color = AdaColors.Background.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, AdaColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Active Profile Switcher Pill
            Surface(
                onClick = onOpenProfileModal,
                color = AdaColors.Surface,
                shape = CircleShape,
                border = BorderStroke(1.dp, AdaColors.BorderStrong),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("profile_picker_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(activeProfile.colorHex))
                    )
                    Text(
                        text = activeProfile.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = AdaColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = "Cambiar perfil",
                        tint = AdaColors.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Availability Selector
                Box {
                    Surface(
                        onClick = { statusMenuOpen = true },
                        color = AdaColors.Surface,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, AdaColors.BorderStrong),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = activeProfile.availability,
                                style = MaterialTheme.typography.labelSmall,
                                color = AdaColors.TextSecondary,
                                maxLines = 1,
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "Cambiar estado",
                                tint = AdaColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = statusMenuOpen,
                        onDismissRequest = { statusMenuOpen = false },
                    ) {
                        AvailabilityStatus.entries.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.label) },
                                onClick = {
                                    onSelectAvailability(st)
                                    statusMenuOpen = false
                                },
                            )
                        }
                    }
                }

                if (!isExpanded) {
                    Surface(
                        onClick = onToggleMobileHub,
                        color = if (mobileHubOpen) AdaColors.SkyPrimary else AdaColors.Surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AdaColors.SkyPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("toggle_mobile_hub_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = if (mobileHubOpen) Icons.Rounded.Close else Icons.Rounded.Layers,
                                contentDescription = if (mobileHubOpen) "Volver al orbe" else "Abrir Hub Alianza",
                                tint = if (mobileHubOpen) Color(0xFF04111F) else AdaColors.SkyPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            if (hasAlerts && !mobileHubOpen) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(AdaColors.SkyPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
