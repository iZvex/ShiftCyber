package ru.shiftcyber.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shiftcyber.data.ShiftEntity
import ru.shiftcyber.data.ShiftExtraItem
import ru.shiftcyber.data.ShiftPayType
import ru.shiftcyber.domain.CycleGenerator
import ru.shiftcyber.domain.ShiftJsonCodec
import ru.shiftcyber.domain.ShiftMath
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class CycleGenerationRequest(
    val anchorDate: LocalDate,
    val workDays: Int,
    val restDays: Int,
    val shiftDurationHours: Int,
    val daysForward: Int,
    val startHour: Int,
    val startMinute: Int,
    val payType: String,
    val hourlyRate: Double,
    val dayRate: Double,
    val title: String,
    val colorKey: String,
    val replaceAutoInWindow: Boolean,
    val manualPolicy: CycleGenerator.ManualConflictPolicy
)

private fun parseStrictTime(raw: String): LocalTime? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(":")
    return when (parts.size) {
        1 -> {
            val hour = parts[0].toIntOrNull() ?: return null
            if (hour !in 0..23) null else LocalTime.of(hour, 0)
        }
        2 -> {
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) null else LocalTime.of(hour, minute)
        }
        else -> null
    }
}

private fun parseIsoDate(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

private fun parseNonNegativeDouble(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

@Composable
fun ShiftEditorDialog(
    editingShift: ShiftEntity?,
    initialDate: LocalDate,
    defaultHourlyRate: Double,
    defaultDailyRate: Double,
    onDismiss: () -> Unit,
    onSave: (ShiftEntity) -> Unit,
    onDelete: ((Long) -> Unit)? = null
) {
    val zoneId = ZoneId.systemDefault()
    val palette = LocalCyberPalette.current

    var payType by remember(editingShift) {
        mutableStateOf(editingShift?.payType ?: ShiftPayType.HOURLY)
    }
    var title by remember(editingShift) {
        mutableStateOf(editingShift?.title ?: "Рабочая смена")
    }
    var dateText by remember(editingShift, initialDate) {
        val d = editingShift?.let { ShiftMath.localDateOf(it.startMillis, zoneId) } ?: initialDate
        mutableStateOf(d.toString())
    }
    var startText by remember(editingShift) {
        mutableStateOf(editingShift?.let { ShiftMath.timeLabel(it.startMillis, zoneId) } ?: "09:00")
    }
    var endText by remember(editingShift) {
        mutableStateOf(editingShift?.let { ShiftMath.timeLabel(it.endMillis, zoneId) } ?: "21:00")
    }
    var hourlyRateText by remember(editingShift, defaultHourlyRate) {
        mutableStateOf(ShiftMath.formatRate(editingShift?.hourlyRate ?: defaultHourlyRate))
    }
    var dayRateText by remember(editingShift, defaultDailyRate) {
        val initialDayRate = if (editingShift != null && editingShift.isDaily) {
            editingShift.dayRate
        } else {
            defaultDailyRate
        }
        mutableStateOf(ShiftMath.formatRate(initialDayRate))
    }
    var colorKey by remember(editingShift) {
        mutableStateOf(editingShift?.colorKey ?: "violet")
    }

    val extraItems = remember(editingShift) {
        mutableStateListOf<ShiftExtraItem>().apply {
            if (editingShift != null) {
                addAll(ShiftJsonCodec.decodeExtras(editingShift.extrasJson))
            }
        }
    }
    var extraComment by remember { mutableStateOf("") }
    var extraQtyText by remember { mutableStateOf("") }
    var extraUnitPriceText by remember { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }

    val draftExtraPreview: ShiftExtraItem? = remember(extraComment, extraQtyText, extraUnitPriceText) {
        val qty = parseNonNegativeDouble(extraQtyText) ?: return@remember null
        val price = parseNonNegativeDouble(extraUnitPriceText) ?: return@remember null
        if (qty <= 0.0 && price <= 0.0 && extraComment.isBlank()) return@remember null
        ShiftExtraItem(comment = extraComment.trim(), quantity = qty, unitPrice = price)
    }

    val previewSummary: String? = remember(
        payType,
        dateText,
        startText,
        endText,
        hourlyRateText,
        dayRateText,
        extraItems.toList(),
        draftExtraPreview
    ) {
        val d = parseIsoDate(dateText) ?: return@remember null
        if (payType == ShiftPayType.DAILY) {
            val baseDay = parseNonNegativeDouble(dayRateText) ?: return@remember null
            val allExtras = extraItems.toList() + listOfNotNull(draftExtraPreview)
            val extrasSum = allExtras.sumOf { it.total }
            val total = baseDay + extrasSum
            if (extrasSum > 0.0) {
                "Дневная (1 день): ${ShiftMath.formatCurrency(baseDay)} + доп. ${ShiftMath.formatCurrency(extrasSum)} = ${ShiftMath.formatCurrency(total)}"
            } else {
                "Дневная смена (1 день): ${ShiftMath.formatCurrency(total)}"
            }
        } else {
            val st = parseStrictTime(startText) ?: return@remember null
            val et = parseStrictTime(endText) ?: return@remember null
            if (st == et) return@remember null
            val startMillis = d.atTime(st).atZone(zoneId).toInstant().toEpochMilli()
            val endDate = if (et.isAfter(st)) d else d.plusDays(1)
            val endMillis = endDate.atTime(et).atZone(zoneId).toInstant().toEpochMilli()
            val hr = parseNonNegativeDouble(hourlyRateText) ?: return@remember null
            val temp = ShiftEntity(startMillis = startMillis, endMillis = endMillis, hourlyRate = hr)
            val nightLabel = if (endDate != d && et != LocalTime.MIDNIGHT) "Ночная (+1 дн.) · " else ""
            "$nightLabel${ShiftMath.formatHours(ShiftMath.hours(temp))} · ${ShiftMath.formatCurrency(ShiftMath.earnings(temp))}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                text = if (editingShift == null) "Новая смена" else "Изменить смену",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Тип расчёта смены:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = payType == ShiftPayType.HOURLY,
                        onClick = { payType = ShiftPayType.HOURLY; error = null },
                        label = { Text("Почасовая") }
                    )
                    FilterChip(
                        selected = payType == ShiftPayType.DAILY,
                        onClick = { payType = ShiftPayType.DAILY; error = null },
                        label = { Text("Дневная (за день)") }
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("Название смены") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; error = null },
                    label = { Text("Дата (ГГГГ-ММ-ДД)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { dateText = LocalDate.now(zoneId).toString(); error = null },
                        label = { Text("Сегодня", fontSize = 12.sp) }
                    )
                    AssistChip(
                        onClick = { dateText = LocalDate.now(zoneId).plusDays(1).toString(); error = null },
                        label = { Text("Завтра", fontSize = 12.sp) }
                    )
                }

                if (payType == ShiftPayType.HOURLY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it; error = null },
                            label = { Text("Начало (ЧЧ:ММ)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it; error = null },
                            label = { Text("Окончание (ЧЧ:ММ)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = hourlyRateText,
                        onValueChange = { hourlyRateText = it; error = null },
                        label = { Text("Почасовая ставка (₽/час)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                } else {
                    OutlinedTextField(
                        value = dayRateText,
                        onValueChange = { dayRateText = it; error = null },
                        label = { Text("Стоимость дня (1 день = ₽)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        "Дополнение к заработку",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Комментарий → сколько сделано → сумма за 1 действие",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    extraItems.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = item.comment.ifBlank { "Доп. действие #${idx + 1}" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${ShiftMath.formatQuantity(item.quantity)} × ${ShiftMath.formatCurrency(item.unitPrice)} = +${ShiftMath.formatCurrency(item.total)}",
                                    fontSize = 11.sp,
                                    color = palette.green
                                )
                            }
                            IconButton(
                                onClick = { extraItems.removeAt(idx) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Удалить позицию",
                                    tint = palette.danger,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = extraComment,
                        onValueChange = { extraComment = it; error = null },
                        label = { Text("Комментарий (что сделано)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = extraQtyText,
                            onValueChange = { extraQtyText = it; error = null },
                            label = { Text("Сколько сделано") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = extraUnitPriceText,
                            onValueChange = { extraUnitPriceText = it; error = null },
                            label = { Text("За 1 действие (₽)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val qty = parseNonNegativeDouble(extraQtyText)
                            val unit = parseNonNegativeDouble(extraUnitPriceText)
                            if (qty == null || qty <= 0.0 || unit == null || unit <= 0.0) {
                                error = "Для доп. заработка укажи количество (> 0) и сумму за 1 действие (> 0)."
                            } else {
                                extraItems.add(
                                    ShiftExtraItem(
                                        comment = extraComment.trim(),
                                        quantity = qty,
                                        unitPrice = unit
                                    )
                                )
                                extraComment = ""
                                extraQtyText = ""
                                extraUnitPriceText = ""
                                error = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Добавить действие к смене", fontSize = 12.sp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Цвет:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        Triple("violet", "Фиолет", palette.purple),
                        Triple("cyan", "Неон", palette.cyan),
                        Triple("green", "Изумруд", palette.green)
                    ).forEach { (key, label, accent) ->
                        val selected = colorKey == key
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) accent.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { colorKey = key }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(accent, CircleShape)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }

                if (previewSummary != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = previewSummary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.green
                    )
                }

                if (editingShift != null && onDelete != null) {
                    TextButton(
                        onClick = { onDelete(editingShift.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = palette.danger
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Удалить эту смену", color = palette.danger)
                    }
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate = parseIsoDate(dateText)
                    if (parsedDate == null) {
                        error = "Укажи дату в формате ГГГГ-ММ-ДД (например, 2026-11-15)."
                        return@Button
                    }

                    if (payType == ShiftPayType.DAILY) {
                        val parsedDayRate = parseNonNegativeDouble(dayRateText)
                        if (parsedDayRate == null) {
                            error = "Укажи корректную стоимость дневной смены в рублях (0 или больше)."
                            return@Button
                        }

                        val hasDraftFields = extraComment.isNotBlank() ||
                            extraQtyText.isNotBlank() ||
                            extraUnitPriceText.isNotBlank()
                        val finalExtras = extraItems.toMutableList()
                        if (hasDraftFields) {
                            val qty = parseNonNegativeDouble(extraQtyText)
                            val unit = parseNonNegativeDouble(extraUnitPriceText)
                            if (qty == null || qty <= 0.0 || unit == null || unit <= 0.0) {
                                error = "Заполни количество и сумму за 1 действие (или очисти незавершённую строку доп. заработка)."
                                return@Button
                            }
                            finalExtras += ShiftExtraItem(
                                comment = extraComment.trim(),
                                quantity = qty,
                                unitPrice = unit
                            )
                        }

                        val dayStartMillis = parsedDate.atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()
                        val dayEndMillis = parsedDate.atTime(21, 0).atZone(zoneId).toInstant().toEpochMilli()

                        onSave(
                            ShiftEntity(
                                id = editingShift?.id ?: 0L,
                                startMillis = dayStartMillis,
                                endMillis = dayEndMillis,
                                title = title.trim().ifBlank { "Дневная смена" },
                                hourlyRate = editingShift?.hourlyRate ?: defaultHourlyRate,
                                breakMinutes = editingShift?.breakMinutes ?: 0,
                                colorKey = colorKey,
                                isManual = true,
                                payType = ShiftPayType.DAILY,
                                dayRate = parsedDayRate,
                                extrasJson = ShiftJsonCodec.encodeExtras(finalExtras)
                            )
                        )
                    } else {
                        val parsedStart = parseStrictTime(startText)
                        val parsedEnd = parseStrictTime(endText)
                        if (parsedStart == null || parsedEnd == null) {
                            error = "Укажи время от 00:00 до 23:59 (например, 09:00 и 21:00)."
                            return@Button
                        }
                        if (parsedStart == parsedEnd) {
                            error = "Время начала и окончания не должны совпадать."
                            return@Button
                        }

                        val startMillis = parsedDate.atTime(parsedStart).atZone(zoneId).toInstant().toEpochMilli()
                        val endDate = if (parsedEnd.isAfter(parsedStart)) parsedDate else parsedDate.plusDays(1)
                        val endMillis = endDate.atTime(parsedEnd).atZone(zoneId).toInstant().toEpochMilli()
                        if (endMillis - startMillis < ShiftMath.MIN_DURATION_MILLIS) {
                            error = "Минимальная длительность смены — 15 минут."
                            return@Button
                        }

                        val parsedHourly = parseNonNegativeDouble(hourlyRateText)
                        if (parsedHourly == null) {
                            error = "Укажи корректную почасовую ставку в рублях (0 или больше)."
                            return@Button
                        }
                        onSave(
                            ShiftEntity(
                                id = editingShift?.id ?: 0L,
                                startMillis = startMillis,
                                endMillis = endMillis,
                                title = title.trim().ifBlank { "Рабочая смена" },
                                hourlyRate = parsedHourly,
                                breakMinutes = editingShift?.breakMinutes ?: 0,
                                colorKey = colorKey,
                                isManual = true,
                                payType = ShiftPayType.HOURLY,
                                dayRate = editingShift?.dayRate ?: 0.0,
                                extrasJson = "[]"
                            )
                        )
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun CycleGeneratorDialog(
    initialAnchorDate: LocalDate,
    defaultHourlyRate: Double,
    defaultDailyRate: Double,
    existingShifts: List<ShiftEntity>,
    onDismiss: () -> Unit,
    onGenerate: (CycleGenerationRequest) -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    val palette = LocalCyberPalette.current

    var payType by remember { mutableStateOf(ShiftPayType.HOURLY) }
    var title by remember { mutableStateOf("Рабочая смена") }
    var dateText by remember(initialAnchorDate) { mutableStateOf(initialAnchorDate.toString()) }
    var workDaysText by remember { mutableStateOf("4") }
    var restDaysText by remember { mutableStateOf("2") }
    var shiftHoursText by remember { mutableStateOf("12") }
    var daysForwardText by remember { mutableStateOf("60") }
    var startTimeText by remember { mutableStateOf("09:00") }
    var hourlyRateText by remember(defaultHourlyRate) {
        mutableStateOf(ShiftMath.formatRate(defaultHourlyRate))
    }
    var dayRateText by remember(defaultDailyRate) {
        mutableStateOf(ShiftMath.formatRate(defaultDailyRate))
    }
    var replaceAuto by remember { mutableStateOf(true) }
    var overwriteManual by remember { mutableStateOf(false) }
    var pendingManualConfirmCount by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text("Генератор цикла смен", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    "Задай чередование «N рабочих / M выходных» (например, 4/2 или 2/2). Ручные смены по умолчанию защищены.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = payType == ShiftPayType.HOURLY,
                        onClick = { payType = ShiftPayType.HOURLY; pendingManualConfirmCount = null },
                        label = { Text("Почасовые") }
                    )
                    FilterChip(
                        selected = payType == ShiftPayType.DAILY,
                        onClick = { payType = ShiftPayType.DAILY; pendingManualConfirmCount = null },
                        label = { Text("Дневные (за день)") }
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null; pendingManualConfirmCount = null },
                    label = { Text("Название смены") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; error = null; pendingManualConfirmCount = null },
                    label = { Text("Дата начала цикла (ГГГГ-ММ-ДД)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = workDaysText,
                        onValueChange = { workDaysText = it; error = null; pendingManualConfirmCount = null },
                        label = { Text("Рабочих дней") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = restDaysText,
                        onValueChange = { restDaysText = it; error = null; pendingManualConfirmCount = null },
                        label = { Text("Выходных") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                if (payType == ShiftPayType.HOURLY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = shiftHoursText,
                            onValueChange = { shiftHoursText = it; error = null; pendingManualConfirmCount = null },
                            label = { Text("Часов за смену") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = daysForwardText,
                            onValueChange = { daysForwardText = it; error = null; pendingManualConfirmCount = null },
                            label = { Text("Дней периода") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startTimeText,
                            onValueChange = { startTimeText = it; error = null; pendingManualConfirmCount = null },
                            label = { Text("Начало (ЧЧ:ММ)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = hourlyRateText,
                            onValueChange = { hourlyRateText = it; error = null; pendingManualConfirmCount = null },
                            label = { Text("Ставка ₽/ч") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = daysForwardText,
                            onValueChange = { daysForwardText = it; error = null; pendingManualConfirmCount = null },
                            label = { Text("Дней периода") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = dayRateText,
                            onValueChange = { dayRateText = it; error = null; pendingManualConfirmCount = null },
                            label = { Text("За 1 день (₽)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replaceAuto = !replaceAuto; pendingManualConfirmCount = null },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = replaceAuto,
                        onCheckedChange = { replaceAuto = it; pendingManualConfirmCount = null }
                    )
                    Text("Обновить прежние авто-смены в этом периоде", fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { overwriteManual = !overwriteManual; pendingManualConfirmCount = null },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = overwriteManual,
                        onCheckedChange = { overwriteManual = it; pendingManualConfirmCount = null }
                    )
                    Text("Заменять ручные смены при совпадении", fontSize = 12.sp)
                }

                if (pendingManualConfirmCount != null) {
                    Text(
                        text = "Внимание: в периоде найдено ручных смен: $pendingManualConfirmCount. Нажми кнопку ещё раз для подтверждения перезаписи.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.danger
                    )
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val anchorDate = parseIsoDate(dateText)
                    if (anchorDate == null) {
                        error = "Проверь дату начала (формат ГГГГ-ММ-ДД)."
                        return@Button
                    }
                    val work = workDaysText.trim().toIntOrNull()
                    val rest = restDaysText.trim().toIntOrNull()
                    if (work == null || work !in 1..60 || rest == null || rest !in 1..60) {
                        error = "Рабочие и выходные дни должны быть от 1 до 60."
                        return@Button
                    }
                    val shiftHours = if (payType == ShiftPayType.HOURLY) {
                        shiftHoursText.trim().toIntOrNull()
                    } else {
                        12
                    }
                    if (shiftHours == null || shiftHours !in 1..24) {
                        error = "Длительность смены должна быть от 1 до 24 часов."
                        return@Button
                    }
                    val daysForward = daysForwardText.trim().toIntOrNull()
                    if (daysForward == null || daysForward !in 1..730) {
                        error = "Период генерации должен быть от 1 до 730 дней."
                        return@Button
                    }
                    val startTime = if (payType == ShiftPayType.HOURLY) {
                        parseStrictTime(startTimeText)
                    } else {
                        LocalTime.of(9, 0)
                    }
                    if (startTime == null) {
                        error = "Укажи время начала смены (например, 09:00 или 20:00)."
                        return@Button
                    }
                    val hr = if (payType == ShiftPayType.HOURLY) {
                        parseNonNegativeDouble(hourlyRateText)
                    } else {
                        defaultHourlyRate
                    }
                    val dr = if (payType == ShiftPayType.DAILY) {
                        parseNonNegativeDouble(dayRateText)
                    } else {
                        0.0
                    }
                    if (hr == null || dr == null) {
                        error = "Укажи корректную ставку в рублях (0 или больше)."
                        return@Button
                    }

                    val candidates = CycleGenerator.generateWorkRestCycle(
                        anchorDate = anchorDate,
                        daysForward = daysForward,
                        workDays = work,
                        restDays = rest,
                        shiftDurationHours = shiftHours,
                        startHour = startTime.hour,
                        startMinute = startTime.minute,
                        hourlyRate = hr,
                        dayRate = dr,
                        payType = payType,
                        title = title.trim().ifBlank { "Рабочая смена" },
                        zoneId = zoneId
                    )
                    val manualConflictCount = CycleGenerator.hasManualConflicts(
                        candidates = candidates,
                        existingShifts = existingShifts,
                        zoneId = zoneId
                    )

                    if (overwriteManual && manualConflictCount > 0 && pendingManualConfirmCount == null) {
                        pendingManualConfirmCount = manualConflictCount
                        return@Button
                    }

                    onGenerate(
                        CycleGenerationRequest(
                            anchorDate = anchorDate,
                            workDays = work,
                            restDays = rest,
                            shiftDurationHours = shiftHours,
                            daysForward = daysForward,
                            startHour = startTime.hour,
                            startMinute = startTime.minute,
                            payType = payType,
                            hourlyRate = hr,
                            dayRate = dr,
                            title = title.trim().ifBlank { "Рабочая смена" },
                            colorKey = "violet",
                            replaceAutoInWindow = replaceAuto,
                            manualPolicy = if (overwriteManual) {
                                CycleGenerator.ManualConflictPolicy.REPLACE_MANUAL
                            } else {
                                CycleGenerator.ManualConflictPolicy.SKIP_CONFLICTS
                            }
                        )
                    )
                }
            ) {
                Text(
                    if (pendingManualConfirmCount != null) {
                        "Подтвердить замену ($pendingManualConfirmCount)"
                    } else {
                        "Создать график"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
