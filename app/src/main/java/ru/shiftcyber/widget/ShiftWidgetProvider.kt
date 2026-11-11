package ru.shiftcyber.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.shiftcyber.data.AppSettings
import ru.shiftcyber.data.ShiftDatabase
import ru.shiftcyber.notifications.ReminderScheduler

class ShiftWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    withTimeoutOrNull(5_000L) {
                        WidgetUpdater.refreshFromDatabase(context)
                        if (action == Intent.ACTION_BOOT_COMPLETED ||
                            action == Intent.ACTION_MY_PACKAGE_REPLACED
                        ) {
                            val db = ShiftDatabase.get(context.applicationContext)
                            val shifts = runCatching { db.shifts().getAll() }.getOrDefault(emptyList())
                            val settings = runCatching { db.settings().getOnce() }.getOrNull() ?: AppSettings()
                            ReminderScheduler.rescheduleAll(context.applicationContext, shifts, settings)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        // Render cached state immediately so widget never flashes blank, then sync from Room DB.
        WidgetUpdater.renderCached(context, ids)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(4_500L) {
                    WidgetUpdater.refreshFromDatabase(context, ids)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetUpdater.renderCached(context, intArrayOf(appWidgetId))
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(4_000L) {
                    WidgetUpdater.refreshFromDatabase(context, intArrayOf(appWidgetId))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
