package ru.shiftcyber.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shiftcyber.data.ShiftEntity
import java.time.LocalDate
import java.time.ZoneId

class WidgetStateTest {

    private val moscowZone = ZoneId.of("Europe/Moscow")

    @Test
    fun `empty schedule renders clean empty widget state`() {
        val now = LocalDate.of(2026, 11, 11).atTime(12, 0).atZone(moscowZone).toInstant().toEpochMilli()
        val state = WidgetUpdater.buildWidgetState(
            shifts = emptyList(),
            nowMillis = now,
            zoneId = moscowZone
        )

        assertEquals("Нет ближайших смен", state.status)
        assertEquals("—", state.hours)
        assertEquals("— ₽", state.earnings)
        assertEquals("Следующая смена: не запланирована", state.next)
    }

    @Test
    fun `active shift shows current shift earnings and next shift`() {
        val now = LocalDate.of(2026, 11, 11).atTime(14, 0).atZone(moscowZone).toInstant().toEpochMilli()
        val shift1Start = LocalDate.of(2026, 11, 11).atTime(9, 0).atZone(moscowZone).toInstant().toEpochMilli()
        val shift1End = LocalDate.of(2026, 11, 11).atTime(21, 0).atZone(moscowZone).toInstant().toEpochMilli()
        val shift2Start = LocalDate.of(2026, 11, 12).atTime(9, 0).atZone(moscowZone).toInstant().toEpochMilli()
        val shift2End = LocalDate.of(2026, 11, 12).atTime(21, 0).atZone(moscowZone).toInstant().toEpochMilli()

        val shifts = listOf(
            ShiftEntity(id = 1L, startMillis = shift1Start, endMillis = shift1End, title = "Дневная", hourlyRate = 400.0),
            ShiftEntity(id = 2L, startMillis = shift2Start, endMillis = shift2End, title = "Дневная", hourlyRate = 400.0)
        )

        val state = WidgetUpdater.buildWidgetState(
            shifts = shifts,
            nowMillis = now,
            zoneId = moscowZone
        )

        assertEquals("Сейчас: Дневная", state.status)
        assertEquals("12 ч", state.hours)
        assertEquals("4 800 ₽", state.earnings)
        assertTrue(state.next.startsWith("Далее:"))
    }
}
