package ru.shiftcyber.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shiftcyber.data.ShiftEntity
import java.time.LocalDate
import java.time.ZoneId

class CycleGeneratorTest {

    private val moscowZone = ZoneId.of("Europe/Moscow")

    @Test
    fun `generates 4 work 2 rest cycle across month boundary accurately`() {
        val anchor = LocalDate.of(2026, 11, 28)
        val generated = CycleGenerator.generateWorkRestCycle(
            anchorDate = anchor,
            daysForward = 12,
            workDays = 4,
            restDays = 2,
            shiftDurationHours = 12,
            startHour = 8,
            startMinute = 0,
            hourlyRate = 400.0,
            title = "Дневная",
            zoneId = moscowZone
        )

        // 12 days = two full 4/2 cycles -> exactly 8 shifts
        assertEquals(8, generated.size)
        val generatedDates = generated.map { ShiftMath.localDateOf(it.startMillis, moscowZone) }
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 28),
                LocalDate.of(2026, 11, 29),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 1),
                // Dec 2 & Dec 3 are rest days
                LocalDate.of(2026, 12, 4),
                LocalDate.of(2026, 12, 5),
                LocalDate.of(2026, 12, 6),
                LocalDate.of(2026, 12, 7)
                // Dec 8 & Dec 9 are rest days
            ),
            generatedDates
        )
        assertTrue(generated.all { !it.isManual && ShiftMath.hours(it) == 12.0 })
    }

    @Test
    fun `midnight start hour 00_00 is included on first day`() {
        val anchor = LocalDate.of(2026, 11, 15)
        val generated = CycleGenerator.generateWorkRestCycle(
            anchorDate = anchor,
            daysForward = 6,
            workDays = 4,
            restDays = 2,
            shiftDurationHours = 8,
            startHour = 0,
            startMinute = 0,
            hourlyRate = 500.0,
            zoneId = moscowZone
        )
        assertEquals(4, generated.size)
        assertEquals(anchor, ShiftMath.localDateOf(generated.first().startMillis, moscowZone))
    }

    @Test
    fun `repeated generation produces zero duplicates`() {
        val anchor = LocalDate.of(2026, 11, 1)
        val candidates = CycleGenerator.generateWorkRestCycle(
            anchorDate = anchor,
            daysForward = 18,
            workDays = 4,
            restDays = 2,
            shiftDurationHours = 12,
            startHour = 9,
            hourlyRate = 350.0,
            zoneId = moscowZone
        )
        val existing = candidates.mapIndexed { index, shift -> shift.copy(id = (index + 1).toLong()) }
        val windowStart = anchor.atStartOfDay(moscowZone).toInstant().toEpochMilli()
        val windowEnd = anchor.plusDays(18).atStartOfDay(moscowZone).toInstant().toEpochMilli()

        val plan = CycleGenerator.resolveConflicts(
            candidates = candidates,
            existingShifts = existing,
            windowStartMillis = windowStart,
            windowEndMillis = windowEnd,
            replaceAutoInWindow = true,
            manualPolicy = CycleGenerator.ManualConflictPolicy.SKIP_CONFLICTS,
            zoneId = moscowZone
        )

        assertTrue(plan.toInsert.isEmpty())
        assertTrue(plan.toDeleteIds.isEmpty())
        assertEquals(12, plan.keptIdenticalCount)
    }

    @Test
    fun `manual shift inside generated period is preserved unless overwrite confirmed`() {
        val anchor = LocalDate.of(2026, 11, 1)
        val candidates = CycleGenerator.generateWorkRestCycle(
            anchorDate = anchor,
            daysForward = 6,
            workDays = 4,
            restDays = 2,
            shiftDurationHours = 12,
            startHour = 9,
            hourlyRate = 350.0,
            zoneId = moscowZone
        )

        // Manual custom shift on Nov 2
        val manualStart = LocalDate.of(2026, 11, 2).atTime(10, 0).atZone(moscowZone).toInstant().toEpochMilli()
        val manualShift = ShiftEntity(
            id = 99L,
            startMillis = manualStart,
            endMillis = manualStart + 8 * ShiftMath.MILLIS_PER_HOUR,
            title = "Подмена",
            hourlyRate = 500.0,
            isManual = true
        )

        val windowStart = anchor.atStartOfDay(moscowZone).toInstant().toEpochMilli()
        val windowEnd = anchor.plusDays(6).atStartOfDay(moscowZone).toInstant().toEpochMilli()

        val safePlan = CycleGenerator.resolveConflicts(
            candidates = candidates,
            existingShifts = listOf(manualShift),
            windowStartMillis = windowStart,
            windowEndMillis = windowEnd,
            replaceAutoInWindow = true,
            manualPolicy = CycleGenerator.ManualConflictPolicy.SKIP_CONFLICTS,
            zoneId = moscowZone
        )

        assertEquals(1, safePlan.skippedManualCount)
        assertEquals(3, safePlan.toInsert.size)
        assertTrue(safePlan.toDeleteIds.isEmpty())

        val overwritePlan = CycleGenerator.resolveConflicts(
            candidates = candidates,
            existingShifts = listOf(manualShift),
            windowStartMillis = windowStart,
            windowEndMillis = windowEnd,
            replaceAutoInWindow = true,
            manualPolicy = CycleGenerator.ManualConflictPolicy.REPLACE_MANUAL,
            zoneId = moscowZone
        )

        assertEquals(1, overwritePlan.replacedManualCount)
        assertEquals(listOf(99L), overwritePlan.toDeleteIds)
        assertEquals(4, overwritePlan.toInsert.size)
    }

    @Test
    fun `dst transition zone keeps cycle alignment and exact shift duration`() {
        val berlinZone = ZoneId.of("Europe/Berlin")
        // March 29, 2026 is spring-forward DST day in Europe/Berlin
        val anchor = LocalDate.of(2026, 3, 28)
        val generated = CycleGenerator.generateWorkRestCycle(
            anchorDate = anchor,
            daysForward = 6,
            workDays = 4,
            restDays = 2,
            shiftDurationHours = 12,
            startHour = 8,
            hourlyRate = 300.0,
            zoneId = berlinZone
        )

        assertEquals(4, generated.size)
        assertTrue(generated.all { ShiftMath.hours(it) == 12.0 })
        assertEquals(
            LocalDate.of(2026, 3, 31),
            ShiftMath.localDateOf(generated.last().startMillis, berlinZone)
        )
    }
}
