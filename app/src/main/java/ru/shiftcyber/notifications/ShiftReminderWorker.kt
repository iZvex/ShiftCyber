package ru.shiftcyber.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.shiftcyber.MainActivity
import ru.shiftcyber.data.ShiftDatabase
import ru.shiftcyber.domain.ShiftMath

class ShiftReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val shiftId = inputData.getLong("shiftId", 0L)
        val expectedStartMillis = inputData.getLong("expectedStartMillis", 0L)

        var resolvedDetails = inputData.getString("details") ?: "Проверь свой рабочий график"
        if (shiftId > 0L) {
            val shift = runCatching {
                ShiftDatabase.get(applicationContext).shifts().getById(shiftId)
            }.getOrNull() ?: return Result.success()

            if (expectedStartMillis > 0L && shift.startMillis != expectedStartMillis) {
                return Result.success()
            }
            if (shift.endMillis <= System.currentTimeMillis()) {
                return Result.success()
            }
            resolvedDetails = "${shift.title} · ${ShiftMath.dateLabel(shift.startMillis)}, ${ShiftMath.timeRangeLabel(shift)}"
        }

        val title = inputData.getString("title") ?: "Скоро смена"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "shift_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Напоминания о сменах",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Уведомления о предстоящих рабочих сменах Shift Cyber"
                }
            )
        }

        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            inputData.getInt("notificationId", 1001),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(resolvedDetails)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            manager.notify(inputData.getInt("notificationId", 1001), notification)
        }
        return Result.success()
    }
}
