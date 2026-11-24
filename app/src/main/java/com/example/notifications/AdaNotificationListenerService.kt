package com.example.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.runtime.AdaRuntimeHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class CapturedSystemAlert(
    val id: String,
    val app: String,
    val text: String,
    val packageName: String = "",
    val isWhatsApp: Boolean = false,
)

class AdaNotificationListenerService : NotificationListenerService() {

    private val recentSignatures = ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg == "android") return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return
        val isWhatsApp = pkg in WHATSAPP_PACKAGES
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()?.trim().orEmpty()

        val extractedBody = extractBestBody(extras, title)
        val combined = when {
            conversationTitle.isNotEmpty() && title.isNotEmpty() && extractedBody.isNotEmpty() ->
                "$conversationTitle ($title): $extractedBody"
            title.isNotEmpty() && extractedBody.isNotEmpty() ->
                "$title: $extractedBody"
            extractedBody.isNotEmpty() -> extractedBody
            else -> title
        }.take(400)

        if (combined.isEmpty()) return
        if (isWhatsApp && isWhatsAppNoise(combined)) return

        // Deduplicate burst updates for the same WhatsApp/app message within 6 seconds
        val signature = "$pkg|${combined.lowercase()}"
        val now = System.currentTimeMillis()
        val prevAt = recentSignatures[signature]
        if (prevAt != null && now - prevAt < 6_000L) return
        recentSignatures[signature] = now
        if (recentSignatures.size > 60) {
            recentSignatures.entries.removeIf { now - it.value > 20_000L }
        }

        val appLabel = if (isWhatsApp) "WhatsApp" else resolveAppName(pkg).take(40)
        val alertId = "nat_${sbn.postTime}_${kotlin.math.abs((pkg + combined).hashCode())}"
        val entry = CapturedSystemAlert(
            id = alertId,
            app = appLabel,
            text = combined,
            packageName = pkg,
            isWhatsApp = isWhatsApp,
        )

        val current = _capturedAlerts.value
        if (current.any { it.id == alertId }) return
        _capturedAlerts.value = (current + entry).takeLast(40)

        AdaRuntimeHub.onIncomingSystemAlert(applicationContext, entry)
    }

    private fun extractBestBody(extras: Bundle, title: String): String {
        // 1. Check MessagingStyle array (Android N+ used by WhatsApp for individual/group chats)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            val parcelables = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            val latestMsg = parcelables?.lastOrNull() as? Bundle
            if (latestMsg != null) {
                val msgText = latestMsg.getCharSequence("text")?.toString()?.trim().orEmpty()
                val sender = latestMsg.getCharSequence("sender")?.toString()?.trim().orEmpty()
                if (msgText.isNotEmpty()) {
                    val includeSender = sender.isNotEmpty() &&
                        !sender.equals(title, ignoreCase = true)
                    return if (includeSender) "$sender dice $msgText" else msgText
                }
            }
        }

        // 2. Check BigText / standard Text / InboxStyle lines
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        if (bigText.isNotEmpty()) return bigText

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() && !looksLikeGenericSummaryCount(text)) return text

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val lastLine = textLines?.lastOrNull()?.toString()?.trim().orEmpty()
        if (lastLine.isNotEmpty()) return lastLine

        return text
    }

    private fun looksLikeGenericSummaryCount(text: String): Boolean {
        val lower = text.lowercase()
        return lower.matches(Regex("^\\d+\\s+(mensajes nuevos|new messages)$"))
    }

    private fun isWhatsAppNoise(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("buscando mensajes nuevos") ||
            lower.contains("checking for new messages") ||
            lower.contains("whatsapp web está activo") ||
            lower.contains("whatsapp web is currently active") ||
            lower.contains("copia de seguridad en curso") ||
            lower.contains("backup in progress") ||
            lower.contains("preparando copia de seguridad") ||
            lower.matches(Regex("^whatsapp:\\s*\\d+\\s+(mensajes nuevos|new messages)$"))
    }

    private fun resolveAppName(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString().ifBlank { pkg }
        } catch (_: Exception) {
            pkg.substringAfterLast('.').ifBlank { pkg }
        }
    }

    companion object {
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private const val PREFS_NAME = "ada_notif_prefs_v1"
        private const val KEY_AUTO_READ_WHATSAPP = "auto_read_whatsapp"

        private val _capturedAlerts = MutableStateFlow<List<CapturedSystemAlert>>(emptyList())
        val capturedAlerts: StateFlow<List<CapturedSystemAlert>> = _capturedAlerts.asStateFlow()

        fun isAutoReadWhatsAppEnabled(context: Context): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_READ_WHATSAPP, true)
        }

        fun setAutoReadWhatsAppEnabled(context: Context, enabled: Boolean) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_AUTO_READ_WHATSAPP, enabled).apply()
        }

        fun hasNotificationAccess(context: Context): Boolean {
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                val component = ComponentName(context, AdaNotificationListenerService::class.java)
                enabled.contains(component.flattenToString()) ||
                    enabled.contains(component.flattenToShortString())
            } catch (_: Exception) {
                false
            }
        }

        fun openNotificationListenerSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
