package ru.shiftcyber.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shiftcyber.data.ShiftEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ShiftMathTest {

    private val moscowZone = ZoneId.of("Europe/Moscow")

    @Test
    fun `empty schedule produces zero stats`() {
        val (from, to) = ShiftMath.monthBounds(YearMonth.of(2026, 11), moscowZone)
        val stats = ShiftMath.calculatePeriodStats(emptyList(), from, to)
        assertEquals(0, stats.shiftCount)
        assertEquals(0.0, stats.totalHours, 1e-9)
        assertEquals(0.0, stats.totalEarnings, 1e-9)
        assertEquals(0.0, stats.averageShiftEarnings, 1e-9)
    }

    @Test
    fun `hours and earnings ignore legacy breakMinutes without rounding distortion`() {
        val start = LocalDate.of(2026, 11, 10).atTime(9, 0).atZone(moscowZone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 11, 10).atTime(21, 30).atZone(moscowZone).toInstant().toEpochMilli()
        val shift = ShiftEntity(
            id = 1L,
            startMillis = start,
            endMillis = end,
            hourlyRate = 333.33,
            breakMinutes = 60
        )

        assertEquals(12.5, ShiftMath.hours(shift), 1e-9)
        assertEquals(12.5 * 333.33, ShiftMath.earnings(shift), 1e-6)
    }

    @Test
    fun `adjustDurationHours prevents zero or negative duration on minus one hour`() {
        val start = 1_700_000_000_000L
        val oneHourShift = ShiftEntity(
            id = 1L,
            startMillis = start,
            endMillis = start + ShiftMath.MILLIS_PER_HOUR,
            hourlyRate = 400.0
        )

        assertFalse(ShiftMath.canAdjustHours(oneHourShift, -1))
        assertNull(ShiftMath.adjustDurationHours(oneHourShift, -1))

        val plusOne = ShiftMath.adjustDurationHours(oneHourShift, 1)
        assertNotNull(plusOne)
        assertEquals(2.0, ShiftMath.hours(plusOne!!), 1e-9)

        val backToOne = ShiftMath.adjustDurationHours(plusOne, -1)
        assertNotNull(backToOne)
        assertEquals(1.0, ShiftMath.hours(backToOne!!), 1e-9)
    }

    @Test
    fun `night shift across midnight and month-year boundary splits accurately`() {
        val start = LocalDate.of(2026, 12, 31).atTime(20, 0).atZone(moscowZone).toInstant().toEpochMilli()
        val end = LocalDate.of(2027, 1, 1).atTime(8, 0).atZone(moscowZone).toInstant().toEpochMilli()
        val nightShift = ShiftEntity(
            id = 42L,
            startMillis = start,
            endMillis = end,
            hourlyRate = 500.0
        )

        assertTrue(ShiftMath.isCrossMidnight(nightShift, moscowZone))
        assertEquals("20:00–08:00 (+1 дн.)", ShiftMath.timeRangeLabel(nightShift, moscowZone))
        assertEquals(12.0, ShiftMath.hours(nightShift), 1e-9)
        assertEquals(6000.0, ShiftMath.earnings(nightShift), 1e-9)

        val (decStart, decEnd) = ShiftMath.monthBounds(YearMonth.of(2026, 12), moscowZone)
        val (janStart, janEnd) = ShiftMath.monthBounds(YearMonth.of(2027, 1), moscowZone)

        val decStats = ShiftMath.calculatePeriodStats(listOf(nightShift), decStart, decEnd)
        val janStats = ShiftMath.calculatePeriodStats(listOf(nightShift), janStart, janEnd)

        assertEquals(1, decStats.shiftCount)
        assertEquals(4.0, decStats.totalHours, 1e-9)
        assertEquals(2000.0, decStats.totalEarnings, 1e-9)

        assertEquals(1, janStats.shiftCount)
        assertEquals(8.0, janStats.totalHours, 1e-9)
        assertEquals(4000.0, janStats.totalEarnings, 1e-9)

        // Conservation of total hours and total rubles across month/year boundary
        assertEquals(12.0, decStats.totalHours + janStats.totalHours, 1e-9)
        assertEquals(6000.0, decStats.totalEarnings + janStats.totalEarnings, 1e-9)
    }

    @Test
    fun `russian currency and hours formatting`() {
        assertEquals("3 600 ₽", ShiftMath.formatCurrency(3600.0))
        assertEquals("3 600,50 ₽", ShiftMath.formatCurrency(3600.5))
        assertEquals("12 ч", ShiftMath.formatHours(12.0))
        assertEquals("12,5 ч", ShiftMath.formatHours(12.5))
    }
}
