package ru.shiftcyber.domain

import ru.shiftcyber.data.ShiftEntity
import ru.shiftcyber.data.ShiftExtraItem
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object ShiftMath {
    const val MILLIS_PER_HOUR: Long = 3_600_000L
    const val MIN_DURATION_MILLIS: Long = 15 * 60_000L
    const val MAX_DURATION_MILLIS: Long = 48 * MILLIS_PER_HOUR

    val RU_LOCALE: Locale = Locale("ru", "RU")

    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM, EEE", RU_LOCALE)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", RU_LOCALE)
    private val monthYearFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", RU_LOCALE)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", RU_LOCALE)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", RU_LOCALE)

    data class PeriodStats(
        val shiftCount: Int,
        val totalHours: Double,
        val totalEarnings: Double,
        val averageShiftEarnings: Double
    )

    /**
     * Длительность почасовой смены в часах без вычета перерывов и без промежуточного округления.
     * Для простой дневной смены почасовой учёт не ведётся (0 ч, смена учитывается как 1 день).
     */
    fun hours(shift: ShiftEntity): Double =
        if (shift.isDaily) {
            0.0
        } else {
            (shift.endMillis - shift.startMillis).coerceAtLeast(0L).toDouble() / MILLIS_PER_HOUR
        }

    fun extraItems(shift: ShiftEntity): List<ShiftExtraItem> =
        if (shift.isDaily) ShiftJsonCodec.decodeExtras(shift.extrasJson) else emptyList()

    fun extrasTotal(shift: ShiftEntity): Double =
        extraItems(shift).sumOf { it.total }

    /**
     * Заработок за смену в рублях:
     * - Почасовая (HOURLY): часы × почасовая ставка.
     * - Дневная (DAILY): 1 день = стоимость дня (dayRate) + сумма всех сдельных доп. действий.
     */
    fun earnings(shift: ShiftEntity): Double =
        if (shift.isDaily) {
            shift.dayRate.coerceAtLeast(0.0) + extrasTotal(shift)
        } else {
            hours(shift) * shift.hourlyRate
        }

    fun overlapMillis(shift: ShiftEntity, fromMillis: Long, toMillis: Long): Long {
        if (toMillis <= fromMillis || shift.endMillis <= shift.startMillis) return 0L
        val start = maxOf(shift.startMillis, fromMillis)
        val end = minOf(shift.endMillis, toMillis)
        return (end - start).coerceAtLeast(0L)
    }

    fun hoursInPeriod(shift: ShiftEntity, fromMillis: Long, toMillis: Long): Double =
        if (shift.isDaily) {
            0.0
        } else {
            overlapMillis(shift, fromMillis, toMillis).toDouble() / MILLIS_PER_HOUR
        }

    fun earningsInPeriod(shift: ShiftEntity, fromMillis: Long, toMillis: Long): Double =
        if (shift.isDaily) {
            if (shift.startMillis in fromMillis until toMillis) earnings(shift) else 0.0
        } else {
            hoursInPeriod(shift, fromMillis, toMillis) * shift.hourlyRate
        }

    fun isShiftInPeriod(shift: ShiftEntity, fromMillis: Long, toMillis: Long): Boolean =
        if (shift.isDaily) {
            shift.startMillis in fromMillis until toMillis
        } else {
            overlapMillis(shift, fromMillis, toMillis) > 0L
        }

    fun calculatePeriodStats(
        shifts: List<ShiftEntity>,
        fromMillis: Long,
        toMillis: Long
    ): PeriodStats {
        if (toMillis <= fromMillis || shifts.isEmpty()) {
            return PeriodStats(0, 0.0, 0.0, 0.0)
        }
        val matching = shifts.filter { isShiftInPeriod(it, fromMillis, toMillis) }
        val totalHours = matching.sumOf { hoursInPeriod(it, fromMillis, toMillis) }
        val totalEarnings = matching.sumOf { earningsInPeriod(it, fromMillis, toMillis) }
        val count = matching.size
        val average = if (count == 0) 0.0 else totalEarnings / count
        return PeriodStats(
            shiftCount = count,
            totalHours = totalHours,
            totalEarnings = totalEarnings,
            averageShiftEarnings = average
        )
    }

    fun calculateAllTimeStats(shifts: List<ShiftEntity>): PeriodStats {
        val valid = shifts.filter { it.endMillis > it.startMillis }
        val totalHours = valid.sumOf { hours(it) }
        val totalEarnings = valid.sumOf { earnings(it) }
        val count = valid.size
        val average = if (count == 0) 0.0 else totalEarnings / count
        return PeriodStats(
            shiftCount = count,
            totalHours = totalHours,
            totalEarnings = totalEarnings,
            averageShiftEarnings = average
        )
    }

    fun canAdjustHours(shift: ShiftEntity, deltaHours: Int): Boolean {
        if (shift.isDaily) return false
        val newDuration = (shift.endMillis + deltaHours * MILLIS_PER_HOUR) - shift.startMillis
        return newDuration in MIN_DURATION_MILLIS..MAX_DURATION_MILLIS
    }

    fun adjustDurationHours(shift: ShiftEntity, deltaHours: Int): ShiftEntity? {
        if (!canAdjustHours(shift, deltaHours)) return null
        return shift.copy(endMillis = shift.endMillis + deltaHours * MILLIS_PER_HOUR)
    }

    fun localDateOf(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()

    fun dayBounds(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }

    fun dayBounds(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
        dayBounds(localDateOf(millis, zoneId), zoneId)

    fun monthBounds(
        year: Int,
        monthZeroBased: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long> {
        val ym = YearMonth.of(year, (monthZeroBased + 1).coerceIn(1, 12))
        return monthBounds(ym, zoneId)
    }

    fun monthBounds(
        yearMonth: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long> {
        val start = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }

    fun isCrossMidnight(shift: ShiftEntity, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        if (shift.endMillis <= shift.startMillis) return false
        val startDate = localDateOf(shift.startMillis, zoneId)
        val effectiveEndDate = localDateOf(shift.endMillis - 1L, zoneId)
        return effectiveEndDate != startDate
    }

    fun shiftsStartingOnDay(
        shifts: List<ShiftEntity>,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ShiftEntity> = shifts
        .filter { localDateOf(it.startMillis, zoneId) == date }
        .sortedBy { it.startMillis }

    fun shiftsOverlappingDay(
        shifts: List<ShiftEntity>,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ShiftEntity> {
        val (from, to) = dayBounds(date, zoneId)
        return shifts
            .filter { isShiftInPeriod(it, from, to) }
            .sortedBy { it.startMillis }
    }

    fun dateLabel(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).format(shortDateFormatter)

    fun dateTimeLabel(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).format(dateTimeFormatter)

    fun fullDateLabel(date: LocalDate): String =
        date.format(fullDateFormatter).replaceFirstChar { it.titlecase(RU_LOCALE) }

    fun monthTitleLabel(yearMonth: YearMonth): String =
        yearMonth.format(monthYearFormatter).replaceFirstChar { it.titlecase(RU_LOCALE) }

    fun timeLabel(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zoneId).format(timeFormatter)

    fun timeRangeLabel(shift: ShiftEntity, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (shift.isDaily) return "1 день"
        val startText = timeLabel(shift.startMillis, zoneId)
        val endZdt = Instant.ofEpochMilli(shift.endMillis).atZone(zoneId)
        val endText = endZdt.format(timeFormatter)
        val startDate = localDateOf(shift.startMillis, zoneId)
        val crossesIntoNextDay = endZdt.toLocalDate() != startDate && endZdt.toLocalTime() != LocalTime.MIDNIGHT
        return if (crossesIntoNextDay) "$startText–$endText (+1 дн.)" else "$startText–$endText"
    }

    fun formatCurrency(amount: Double, alwaysShowDecimals: Boolean = false): String {
        val symbols = DecimalFormatSymbols(RU_LOCALE).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        val roundedCents = (amount * 100.0).roundToLong() / 100.0
        val hasFraction = abs(roundedCents - roundedCents.roundToLong().toDouble()) >= 0.005
        val pattern = if (alwaysShowDecimals || hasFraction) "#,##0.00" else "#,##0"
        return "${DecimalFormat(pattern, symbols).format(roundedCents)} ₽"
    }

    fun formatHours(hours: Double): String {
        val symbols = DecimalFormatSymbols(RU_LOCALE).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        val normalized = (hours * 100.0).roundToLong() / 100.0
        return "${DecimalFormat("#,##0.##", symbols).format(normalized)} ч"
    }

    fun formatQuantity(qty: Double): String {
        val symbols = DecimalFormatSymbols(RU_LOCALE).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        val normalized = (qty * 100.0).roundToLong() / 100.0
        return DecimalFormat("#,##0.##", symbols).format(normalized)
    }

    fun formatRate(rate: Double): String {
        val symbols = DecimalFormatSymbols(RU_LOCALE).apply {
            decimalSeparator = '.'
        }
        val normalized = (rate * 100.0).roundToLong() / 100.0
        return DecimalFormat("0.##", symbols).format(normalized)
    }
}
