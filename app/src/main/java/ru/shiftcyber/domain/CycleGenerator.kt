package ru.shiftcyber.domain

import ru.shiftcyber.data.ShiftEntity
import ru.shiftcyber.data.ShiftPayType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object CycleGenerator {

    enum class ManualConflictPolicy {
        SKIP_CONFLICTS,
        REPLACE_MANUAL
    }

    data class CycleMergePlan(
        val toInsert: List<ShiftEntity>,
        val toDeleteIds: List<Long>,
        val keptIdenticalCount: Int,
        val skippedManualCount: Int,
        val skippedAutoCount: Int,
        val replacedAutoCount: Int,
        val replacedManualCount: Int
    )

    fun generateWorkRestCycle(
        anchorDate: LocalDate,
        daysForward: Int,
        workDays: Int,
        restDays: Int,
        shiftDurationHours: Int = 12,
        startHour: Int = 9,
        startMinute: Int = 0,
        hourlyRate: Double = 0.0,
        title: String = "Рабочая смена",
        colorKey: String = "violet",
        payType: String = ShiftPayType.HOURLY,
        dayRate: Double = 0.0,
        periodStartDate: LocalDate = anchorDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ShiftEntity> {
        require(workDays >= 1) { "Количество рабочих дней должно быть не меньше 1" }
        require(restDays >= 1) { "Количество выходных дней должно быть не меньше 1" }
        require(daysForward in 1..730) { "Горизонт планирования должен быть от 1 до 730 дней" }
        require(shiftDurationHours in 1..24) { "Длительность смены должна быть от 1 до 24 часов" }
        require(startHour in 0..23 && startMinute in 0..59) { "Некорректное время начала смены" }
        require(hourlyRate >= 0.0 && dayRate >= 0.0) { "Ставка не может быть отрицательной" }

        val pattern = List(workDays) { shiftDurationHours } + List(restDays) { 0 }
        val fromMillis = periodStartDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val toMillis = periodStartDate.plusDays(daysForward.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val anchorMillis = anchorDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

        return generate(
            anchorDateMillis = anchorMillis,
            fromMillis = fromMillis,
            toMillis = toMillis,
            cycleHours = pattern,
            startHour = startHour,
            hourlyRate = hourlyRate,
            title = title.ifBlank { "Рабочая смена" },
            startMinute = startMinute,
            colorKey = colorKey,
            payType = payType,
            dayRate = dayRate,
            zoneId = zoneId
        )
    }

    fun generate(
        anchorDateMillis: Long,
        fromMillis: Long,
        toMillis: Long,
        cycleHours: List<Int>,
        startHour: Int,
        hourlyRate: Double,
        title: String = "Рабочая смена",
        startMinute: Int = 0,
        colorKey: String = "violet",
        payType: String = ShiftPayType.HOURLY,
        dayRate: Double = 0.0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ShiftEntity> {
        require(cycleHours.isNotEmpty()) { "Цикл не должен быть пустым" }
        require(cycleHours.all { it in 0..24 }) { "Длительность должна быть от 0 до 24 часов" }
        require(hourlyRate >= 0.0 && dayRate >= 0.0) { "Ставка не может быть отрицательной" }
        if (toMillis <= fromMillis) return emptyList()

        val safeHour = startHour.coerceIn(0, 23)
        val safeMinute = startMinute.coerceIn(0, 59)
        val anchorDate = Instant.ofEpochMilli(anchorDateMillis).atZone(zoneId).toLocalDate()
        var cursorDate = Instant.ofEpochMilli(fromMillis).atZone(zoneId).toLocalDate()
        val endExclusiveDate = Instant.ofEpochMilli(toMillis - 1L).atZone(zoneId).toLocalDate().plusDays(1)

        val result = mutableListOf<ShiftEntity>()
        while (cursorDate.isBefore(endExclusiveDate)) {
            val days = ChronoUnit.DAYS.between(anchorDate, cursorDate)
            val size = cycleHours.size
            val cycleIndex = (((days % size) + size) % size).toInt()
            val hours = cycleHours[cycleIndex]
            if (hours > 0) {
                val startMillis = cursorDate
                    .atTime(safeHour, safeMinute)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
                val endMillis = startMillis + hours * ShiftMath.MILLIS_PER_HOUR
                if (startMillis in fromMillis until toMillis) {
                    result += ShiftEntity(
                        startMillis = startMillis,
                        endMillis = endMillis,
                        title = title.ifBlank { "Рабочая смена" },
                        hourlyRate = hourlyRate,
                        colorKey = colorKey,
                        isManual = false,
                        payType = payType,
                        dayRate = dayRate,
                        extrasJson = "[]"
                    )
                }
            }
            cursorDate = cursorDate.plusDays(1)
        }
        return result
    }

    fun resolveConflicts(
        candidates: List<ShiftEntity>,
        existingShifts: List<ShiftEntity>,
        windowStartMillis: Long,
        windowEndMillis: Long,
        replaceAutoInWindow: Boolean = true,
        manualPolicy: ManualConflictPolicy = ManualConflictPolicy.SKIP_CONFLICTS,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CycleMergePlan {
        val manualShifts = existingShifts.filter { it.isManual }
        val autoShifts = existingShifts.filter { !it.isManual }

        val keptAutoIds = mutableSetOf<Long>()
        val deleteIds = linkedSetOf<Long>()
        val toInsert = mutableListOf<ShiftEntity>()

        var keptIdenticalCount = 0
        var skippedManualCount = 0
        var skippedAutoCount = 0
        var replacedAutoCount = 0
        var replacedManualCount = 0

        for (candidate in candidates) {
            val conflictingManual = manualShifts.filter { shiftsConflict(it, candidate, zoneId) }
            if (conflictingManual.isNotEmpty()) {
                if (manualPolicy == ManualConflictPolicy.SKIP_CONFLICTS) {
                    skippedManualCount++
                    continue
                } else {
                    conflictingManual.forEach { manual ->
                        if (manual.id != 0L && deleteIds.add(manual.id)) {
                            replacedManualCount++
                        }
                    }
                }
            }

            val conflictingAuto = autoShifts.filter { shiftsConflict(it, candidate, zoneId) }
            val exactMatch = conflictingAuto.firstOrNull { existing ->
                existing.id != 0L &&
                    existing.id !in keptAutoIds &&
                    isSameShiftDefinition(existing, candidate)
            }

            if (exactMatch != null) {
                keptAutoIds += exactMatch.id
                keptIdenticalCount++
                if (replaceAutoInWindow) {
                    conflictingAuto
                        .filter { it.id != exactMatch.id && it.id != 0L }
                        .forEach { extra ->
                            if (deleteIds.add(extra.id)) replacedAutoCount++
                        }
                }
            } else if (conflictingAuto.isEmpty()) {
                toInsert += candidate
            } else if (replaceAutoInWindow) {
                conflictingAuto.forEach { oldAuto ->
                    if (oldAuto.id != 0L && deleteIds.add(oldAuto.id)) {
                        replacedAutoCount++
                    }
                }
                toInsert += candidate
            } else {
                skippedAutoCount++
            }
        }

        if (replaceAutoInWindow && windowEndMillis > windowStartMillis) {
            autoShifts
                .filter {
                    it.id != 0L &&
                        it.id !in keptAutoIds &&
                        it.startMillis in windowStartMillis until windowEndMillis
                }
                .forEach { staleAuto ->
                    if (deleteIds.add(staleAuto.id)) {
                        replacedAutoCount++
                    }
                }
        }

        return CycleMergePlan(
            toInsert = toInsert,
            toDeleteIds = deleteIds.toList(),
            keptIdenticalCount = keptIdenticalCount,
            skippedManualCount = skippedManualCount,
            skippedAutoCount = skippedAutoCount,
            replacedAutoCount = replacedAutoCount,
            replacedManualCount = replacedManualCount
        )
    }

    fun hasManualConflicts(
        candidates: List<ShiftEntity>,
        existingShifts: List<ShiftEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        val manualShifts = existingShifts.filter { it.isManual }
        if (manualShifts.isEmpty()) return 0
        return candidates.count { candidate ->
            manualShifts.any { manual -> shiftsConflict(manual, candidate, zoneId) }
        }
    }

    fun shiftsConflict(
        a: ShiftEntity,
        b: ShiftEntity,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val overlapsInTime = a.startMillis < b.endMillis && a.endMillis > b.startMillis
        if (overlapsInTime) return true
        return ShiftMath.localDateOf(a.startMillis, zoneId) == ShiftMath.localDateOf(b.startMillis, zoneId)
    }

    private fun isSameShiftDefinition(a: ShiftEntity, b: ShiftEntity): Boolean =
        a.startMillis == b.startMillis &&
            a.endMillis == b.endMillis &&
            a.title == b.title &&
            a.payType == b.payType &&
            abs(a.hourlyRate - b.hourlyRate) < 1e-6 &&
            abs(a.dayRate - b.dayRate) < 1e-6 &&
            a.colorKey == b.colorKey
}
