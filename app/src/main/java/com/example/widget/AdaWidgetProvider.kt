package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.runtime.AdaRuntimeHub

class AdaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val EXTRA_WIDGET_ACTION = "com.example.widget.EXTRA_ACTION"
        const val ACTION_OPEN_VOICE = "start_voice"
        const val ACTION_READ_MESSAGES = "read_messages"
        const val ACTION_READ_ALERTS = "read_alerts"
        const val ACTION_TOGGLE_FLOATING = "toggle_floating"

        fun updateAllWidgets(context: Context) {
            val appCtx = context.applicationContext
            val manager = AppWidgetManager.getInstance(appCtx)
            val component = ComponentName(appCtx, AdaWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    updateSingleWidget(appCtx, manager, id)
                }
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val cached = AdaRuntimeHub.loadCachedSummary(context)
            val live = AdaRuntimeHub.state.value
            val floatingOn = live.floatingActive

            val subtitle = buildString {
                append(cached.lastSpeakerLabel)
                if (live.wakeWordArmed) append(" · Di \"ADA\"")
            }

            val views = RemoteViews(context.packageName, R.layout.ada_app_widget).apply {
                setTextViewText(R.id.widget_title, "ADA Alianza · ${cached.activeProfileName}")
                setTextViewText(R.id.widget_subtitle, subtitle)
                setTextViewText(R.id.widget_summary, cached.summaryLine)
                setTextViewText(R.id.widget_btn_floating, if (floatingOn) "Ocultar" else "Flotante")
                setTextViewText(
                    R.id.widget_btn_messages,
                    if (cached.unreadMessagesCount > 0) "Mensajes (${cached.unreadMessagesCount})" else "Mensajes"
                )
                setTextViewText(
                    R.id.widget_btn_alerts,
                    if (cached.alertsCount > 0) "Notif. (${cached.alertsCount})" else "Notif."
                )

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                setOnClickPendingIntent(
                    R.id.widget_orb_icon,
                    PendingIntent.getActivity(
                        context,
                        101,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )

                setOnClickPendingIntent(
                    R.id.widget_btn_speak,
                    actionPendingIntent(context, ACTION_OPEN_VOICE, 102)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_messages,
                    actionPendingIntent(context, ACTION_READ_MESSAGES, 103)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_alerts,
                    actionPendingIntent(context, ACTION_READ_ALERTS, 104)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_floating,
                    actionPendingIntent(context, ACTION_TOGGLE_FLOATING, 105)
                )
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun actionPendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent {
            val intent = Intent(context, AdaWidgetActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_WIDGET_ACTION, action)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
