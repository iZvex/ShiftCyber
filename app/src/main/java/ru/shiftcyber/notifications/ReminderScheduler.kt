package ru.shiftcyber.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ru.shiftcyber.data.AppSettings
import ru.shiftcyber.data.ShiftEntity
import ru.shiftcyber.domain.ShiftMath
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val ALL_REMINDERS_TAG = "shift_reminders_all"
    private const val KIND_DAY_BEFORE = 0
    private const val KIND_SAME_DAY = 1

    fun schedule(
        context: Context,
        id: Long,
        title: String,
        startMillis: Long,
        dayBefore: Boolean,
        sameDay: Boolean
    ) {
        if (id <= 0L) return
        val workManager = WorkManager.getInstance(context)
        val now = System.currentTimeMillis()

        // Always clear previous work for this shift before scheduling updated times.
        cancel(context, id)

        if (startMillis <= now) return

        if (dayBefore) {
            val whenMillis = startMillis - TimeUnit.DAYS.toMillis(1)
            if (whenMillis > now) {
                enqueueReminder(
                    workManager = workManager,
                    shiftId = id,
                    kind = KIND_DAY_BEFORE,
                    uniqueName = uniqueWorkName(id, KIND_DAY_BEFORE),
                    delayMillis = whenMillis - now,
                    notificationTitle = "Завтра смена",
                    shiftTitle = title,
                    startMillis = startMillis
                )
            }
        }

        if (sameDay) {
            val whenMillis = startMillis - TimeUnit.HOURS.toMillis(1)
            if (whenMillis > now) {
                enqueueReminder(
                    workManager = workManager,
                    shiftId = id,
                    kind = KIND_SAME_DAY,
                    uniqueName = uniqueWorkName(id, KIND_SAME_DAY),
                    delayMillis = whenMillis - now,
                    notificationTitle = "Скоро смена",
                    shiftTitle = title,
                    startMillis = startMillis
                )
            }
        }
    }

    fun cancel(context: Context, id: Long) {
        if (id <= 0L) return
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(uniqueWorkName(id, KIND_DAY_BEFORE))
        workManager.cancelUniqueWork(uniqueWorkName(id, KIND_SAME_DAY))
        workManager.cancelAllWorkByTag(shiftTag(id))

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(notificationIdFor(id, KIND_DAY_BEFORE))
        manager?.cancel(notificationIdFor(id, KIND_SAME_DAY))
    }

    fun cancelMany(context: Context, ids: Collection<Long>) {
        ids.forEach { cancel(context, it) }
    }

    fun rescheduleAll(
        context: Context,
        shifts: List<ShiftEntity>,
        settings: AppSettings
    ) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(ALL_REMINDERS_TAG)
        if (!settings.notifyDayBefore && !settings.notifySameDay) return

        val now = System.currentTimeMillis()
        shifts
            .filter { it.id > 0L && it.startMillis > now }
            .forEach { shift ->
                schedule(
                    context = context,
                    id = shift.id,
                    title = shift.title,
                    startMillis = shift.startMillis,
                    dayBefore = settings.notifyDayBefore,
                    sameDay = settings.notifySameDay
                )
            }
    }

    private fun enqueueReminder(
        workManager: WorkManager,
        shiftId: Long,
        kind: Int,
        uniqueName: String,
        delayMillis: Long,
        notificationTitle: String,
        shiftTitle: String,
        startMillis: Long
    ) {
        val details = "$shiftTitle · ${ShiftMath.dateLabel(startMillis)}, ${ShiftMath.timeLabel(startMillis)}"
        val data = Data.Builder()
            .putLong("shiftId", shiftId)
            .putLong("expectedStartMillis", startMillis)
            .putString("title", notificationTitle)
            .putString("details", details)
            .putInt("notificationId", notificationIdFor(shiftId, kind))
            .build()

        val request = OneTimeWorkRequestBuilder<ShiftReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(ALL_REMINDERS_TAG)
            .addTag(shiftTag(shiftId))
            .build()

        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    private fun uniqueWorkName(id: Long, kind: Int): String = "shift_reminder_${id}_$kind"

    private fun shiftTag(id: Long): String = "shift_reminder_$id"

    fun notificationIdFor(id: Long, kind: Int): Int =
        ((id.hashCode() and 0x0FFF_FFFF) * 2) + kind
}
