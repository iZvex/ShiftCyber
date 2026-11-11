package ru.shiftcyber.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.shiftcyber.data.AppSettings
import ru.shiftcyber.data.ShiftDatabase
import ru.shiftcyber.notifications.ReminderScheduler

class SystemSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
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
}
