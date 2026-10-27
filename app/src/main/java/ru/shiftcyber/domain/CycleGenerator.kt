package ru.shiftcyber.domain

import ru.shiftcyber.data.ShiftEntity
import java.util.Calendar

/**
 * Generates shifts from a cycle like [12, 12, 0, 0] (hours per cycle day).
 * A zero means a day off. The cycle repeats from anchorDateMillis.
 */
object CycleGenerator {
    fun generate(
        anchorDateMillis: Long,
        fromMillis: Long,
        toMillis: Long,
        cycleHours: List<Int>,
        startHour: Int,
        hourlyRate: Double,
        title: String = "Рабочая смена"
    ): List<ShiftEntity> {
        require(cycleHours.isNotEmpty()) { "Цикл не должен быть пустым" }
        require(cycleHours.all { it in 0..24 }) { "Длительность должна быть от 0 до 24 часов" }
        val anchor = Calendar.getInstance().apply {
            timeInMillis = anchorDateMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val cursor = Calendar.getInstance().apply { timeInMillis = fromMillis }
        cursor.set(Calendar.HOUR_OF_DAY, 0); cursor.set(Calendar.MINUTE, 0)
        cursor.set(Calendar.SECOND, 0); cursor.set(Calendar.MILLISECOND, 0)
        val result = mutableListOf<ShiftEntity>()
        while (cursor.timeInMillis < toMillis) {
            // Calendar-day difference avoids DST transitions shifting the cycle index.
            val days = java.time.temporal.ChronoUnit.DAYS.between(
                anchor.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                cursor.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            ).toInt()
            val cycleIndex = ((days % cycleHours.size) + cycleHours.size) % cycleHours.size
            val hours = cycleHours[cycleIndex]
            if (hours > 0) {
                val start = (cursor.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, startHour.coerceIn(0, 23)) }
                val end = (start.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, hours) }
                if (start.timeInMillis in fromMillis until toMillis) {
                    result += ShiftEntity(
                        startMillis = start.timeInMillis,
                        endMillis = end.timeInMillis,
                        title = title,
                        hourlyRate = hourlyRate
                    )
                }
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }
}
