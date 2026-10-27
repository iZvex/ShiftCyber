package ru.shiftcyber.domain

import ru.shiftcyber.data.ShiftEntity
import java.util.Calendar
import java.util.Date

object ShiftMath {
    // Shift duration is calculated without unpaid-break deductions.
    fun hours(shift: ShiftEntity): Double =
        (shift.endMillis - shift.startMillis).coerceAtLeast(0L) / 3_600_000.0

    fun earnings(shift: ShiftEntity): Double = hours(shift) * shift.hourlyRate

    fun monthBounds(year: Int, monthZeroBased: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return start.timeInMillis to end.timeInMillis
    }

    fun dateLabel(millis: Long): String =
        java.text.SimpleDateFormat("d MMM, EEE", java.util.Locale("ru")).format(Date(millis))

    fun timeLabel(millis: Long): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date(millis))
}
