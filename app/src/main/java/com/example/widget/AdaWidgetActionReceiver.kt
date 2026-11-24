package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.MainActivity
import com.example.floating.AdaFloatingService
import com.example.runtime.AdaExternalAction
import com.example.runtime.AdaRuntimeHub

class AdaWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(AdaWidgetProvider.EXTRA_WIDGET_ACTION) ?: intent.action ?: return
        when (action) {
            AdaWidgetProvider.ACTION_READ_MESSAGES -> {
                AdaRuntimeHub.dispatchAction(context, AdaExternalAction.ReadMessages)
            }
            AdaWidgetProvider.ACTION_READ_ALERTS -> {
                AdaRuntimeHub.dispatchAction(context, AdaExternalAction.ReadAlerts)
            }
            AdaWidgetProvider.ACTION_OPEN_VOICE -> {
                if (AdaRuntimeHub.hasActiveViewModel) {
                    AdaRuntimeHub.dispatchAction(context, AdaExternalAction.StartVoice)
                } else {
                    val launch = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(AdaWidgetProvider.EXTRA_WIDGET_ACTION, AdaWidgetProvider.ACTION_OPEN_VOICE)
                    }
                    context.startActivity(launch)
                }
            }
            AdaWidgetProvider.ACTION_TOGGLE_FLOATING -> {
                if (AdaRuntimeHub.state.value.floatingActive) {
                    AdaFloatingService.stop(context)
                } else if (Settings.canDrawOverlays(context)) {
                    AdaFloatingService.start(context)
                } else {
                    val overlaySettings = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(overlaySettings)
                }
            }
        }
        AdaWidgetProvider.updateAllWidgets(context)
    }
}
