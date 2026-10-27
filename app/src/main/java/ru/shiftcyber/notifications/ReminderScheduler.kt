package ru.shiftcyber.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun schedule(context: Context, id: Long, title: String, startMillis: Long, dayBefore: Boolean, sameDay: Boolean) {
        val now = System.currentTimeMillis()
        val moments = listOfNotNull(
            if (dayBefore) (startMillis - TimeUnit.DAYS.toMillis(1)) to "Завтра смена" else null,
            if (sameDay) (startMillis - TimeUnit.HOURS.toMillis(1)) to "Скоро смена" else null
        )
        moments.forEachIndexed { index, (whenMillis, label) ->
            val delay = (whenMillis - now).coerceAtLeast(0L)
            val data = Data.Builder().putString("title", label)
                .putString("details", "$title · ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(startMillis))}")
                .putInt("notificationId", (id * 10 + index).toInt()).build()
            val request = OneTimeWorkRequestBuilder<ShiftReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(data).build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
