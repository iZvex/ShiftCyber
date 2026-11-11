package ru.shiftcyber.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ru.shiftcyber.data.AppSettings
import ru.shiftcyber.data.ShiftEntity
import ru.shiftcyber.domain.ShiftMath
import ru.shiftcyber.sync.GoogleDriveSyncManager
import ru.shiftcyber.widget.ShiftCompactWidgetProvider
import ru.shiftcyber.widget.ShiftWidgetProvider
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private fun pluralizeShifts(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..19 -> "смен"
        mod10 == 1 -> "смена"
        mod10 in 2..4 -> "смены"
        else -> "смен"
    }
    return "$count $word"
}

@Composable
fun HomeScreen(
    shifts: List<ShiftEntity>,
    onAddShift: () -> Unit,
    onEditShift: (ShiftEntity) -> Unit,
    onDeleteShift: (Long) -> Unit,
    onAdjustHours: (Long, Int) -> Unit,
    onOpenCalendar: () -> Unit
) {
    val palette = LocalCyberPalette.current
    val zoneId = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val today = LocalDate.now(zoneId)

    val activeShift = shifts.firstOrNull { it.startMillis <= now && it.endMillis > now }
    val nextShift = activeShift ?: shifts.firstOrNull { it.startMillis > now }
    val upcoming = shifts.filter { it.endMillis > now }.take(6)

    val (todayStart, todayEnd) = ShiftMath.dayBounds(today, zoneId)
    val todayStats = ShiftMath.calculatePeriodStats(shifts, todayStart, todayEnd)

    val (monthStart, monthEnd) = ShiftMath.monthBounds(YearMonth.from(today), zoneId)
    val monthStats = ShiftMath.calculatePeriodStats(shifts, monthStart, monthEnd)

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "ТВОЙ РИТМ. ТВОИ ПРАВИЛА.",
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (activeShift != null) "Сейчас идёт смена ⚡" else "Привет, на смене? ⚡",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (nextShift != null) Modifier.clickable { onEditShift(nextShift) }
                        else Modifier
                    ),
                accent = if (activeShift != null) palette.green else palette.purple
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = when {
                                activeShift != null -> "СЕЙЧАС НА СМЕНЕ"
                                nextShift != null -> "СЛЕДУЮЩАЯ СМЕНА"
                                else -> "НЕТ БЛИЖАЙШИХ СМЕН"
                            },
                            fontSize = 11.sp,
                            color = if (activeShift != null) palette.green else MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            nextShift?.title ?: "Время отдохнуть",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        if (nextShift != null) {
                            Text(
                                "${ShiftMath.dateLabel(nextShift.startMillis, zoneId)} · ${ShiftMath.timeRangeLabel(nextShift, zoneId)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            val modeLabel = if (nextShift.isDaily) {
                                "Дневная смена"
                            } else {
                                ShiftMath.formatHours(ShiftMath.hours(nextShift))
                            }
                            Text(
                                "$modeLabel · ${ShiftMath.formatCurrency(ShiftMath.earnings(nextShift))}",
                                color = palette.green,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                "Добавь смену вручную или создай цикл 4/2 в календаре",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = if (activeShift != null) palette.green else palette.purple,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "ЗА МЕСЯЦ",
                    value = ShiftMath.formatCurrency(monthStats.totalEarnings),
                    subtitle = "${ShiftMath.formatHours(monthStats.totalHours)} · ${pluralizeShifts(monthStats.shiftCount)}",
                    icon = Icons.Rounded.AccountBalanceWallet,
                    accent = palette.green,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "ЗА СЕГОДНЯ",
                    value = ShiftMath.formatCurrency(todayStats.totalEarnings),
                    subtitle = ShiftMath.formatHours(todayStats.totalHours),
                    icon = Icons.Rounded.Schedule,
                    accent = palette.cyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAddShift,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить смену")
                }
                OutlinedButton(
                    onClick = onOpenCalendar,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Календарь")
                }
            }
        }

        item {
            Text(
                "БЛИЖАЙШИЕ ВЫХОДЫ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (upcoming.isEmpty()) {
            item {
                Text(
                    "Пока пусто — добавь смену или сгенерируй циклический график.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(upcoming, key = { it.id }) { shift ->
                ShiftRow(
                    shift = shift,
                    onClick = { onEditShift(shift) },
                    onDelete = { onDeleteShift(shift.id) },
                    onAdjustHours = { delta -> onAdjustHours(shift.id, delta) }
                )
            }
        }
    }
}

@Composable
fun CalendarScreen(
    shifts: List<ShiftEntity>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onAddForDate: (LocalDate) -> Unit,
    onEditShift: (ShiftEntity) -> Unit,
    onDeleteShift: (Long) -> Unit,
    onAdjustHours: (Long, Int) -> Unit,
    onOpenCycleGenerator: (LocalDate) -> Unit
) {
    val palette = LocalCyberPalette.current
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)

    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val displayedMonth = YearMonth.from(today).plusMonths(monthOffset.toLong())
    val (monthStart, monthEnd) = ShiftMath.monthBounds(displayedMonth, zoneId)
    val monthStats = ShiftMath.calculatePeriodStats(shifts, monthStart, monthEnd)
    val monthShifts = remember(shifts, monthStart, monthEnd) {
        shifts.filter { ShiftMath.isShiftInPeriod(it, monthStart, monthEnd) }
    }

    val daysInMonth = displayedMonth.lengthOfMonth()
    val firstDayOffset = displayedMonth.atDay(1).dayOfWeek.value - 1

    val startingByDay = remember(shifts, displayedMonth, zoneId) {
        shifts
            .filter {
                val d = ShiftMath.localDateOf(it.startMillis, zoneId)
                YearMonth.from(d) == displayedMonth
            }
            .groupBy { ShiftMath.localDateOf(it.startMillis, zoneId).dayOfMonth }
    }

    val (selStart, selEnd) = ShiftMath.dayBounds(selectedDate, zoneId)
    val selectedDayStats = ShiftMath.calculatePeriodStats(shifts, selStart, selEnd)
    val selectedDayShifts = remember(shifts, selectedDate, zoneId) {
        ShiftMath.shiftsOverlappingDay(shifts, selectedDate, zoneId)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Календарь", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(
                        ShiftMath.monthTitleLabel(displayedMonth),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (monthOffset != 0 || selectedDate != today) {
                    AssistChip(
                        onClick = {
                            monthOffset = 0
                            onSelectDate(today)
                        },
                        label = { Text("Сегодня", fontSize = 11.sp) }
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = {
                    monthOffset--
                    val targetMonth = YearMonth.from(today).plusMonths(monthOffset.toLong())
                    onSelectDate(targetMonth.atDay(1))
                }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Предыдущий месяц")
                }
                IconButton(onClick = {
                    monthOffset++
                    val targetMonth = YearMonth.from(today).plusMonths(monthOffset.toLong())
                    onSelectDate(targetMonth.atDay(1))
                }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Следующий месяц")
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach { dow ->
                        Text(
                            text = dow,
                            modifier = Modifier.weight(1f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                val cellCount = ((firstDayOffset + daysInMonth + 6) / 7) * 7
                for (week in 0 until (cellCount / 7)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (col in 0..6) {
                            val dayNumber = week * 7 + col - firstDayOffset + 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.86f)
                            ) {
                                if (dayNumber in 1..daysInMonth) {
                                    val cellDate = displayedMonth.atDay(dayNumber)
                                    val dayStartingShifts = startingByDay[dayNumber].orEmpty()
                                    val hasStartShift = dayStartingShifts.isNotEmpty()
                                    val hasNightCarryOver = !hasStartShift &&
                                        ShiftMath.shiftsOverlappingDay(shifts, cellDate, zoneId).isNotEmpty()
                                    val isToday = cellDate == today
                                    val isSelected = cellDate == selectedDate

                                    val cellBg = when {
                                        isSelected -> palette.purple.copy(alpha = 0.28f)
                                        hasStartShift -> palette.purple.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                    }
                                    val borderColor = when {
                                        isSelected -> palette.cyan
                                        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                        hasStartShift -> palette.purple.copy(alpha = 0.35f)
                                        else -> Color.Transparent
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(cellBg)
                                            .then(
                                                if (borderColor != Color.Transparent) {
                                                    Modifier.border(
                                                        width = if (isSelected) 1.6.dp else 1.dp,
                                                        color = borderColor,
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                } else Modifier
                                            )
                                            .clickable { onSelectDate(cellDate) }
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNumber",
                                            fontSize = 13.sp,
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (hasStartShift) {
                                            val allDaily = dayStartingShifts.all { it.isDaily }
                                            Spacer(Modifier.height(2.dp))
                                            Box(
                                                Modifier
                                                    .size(6.dp)
                                                    .background(
                                                        if (allDaily) palette.green else palette.purple,
                                                        CircleShape
                                                    )
                                            )
                                            Text(
                                                text = if (allDaily) {
                                                    "дн."
                                                } else {
                                                    ShiftMath.formatHours(
                                                        dayStartingShifts.sumOf { ShiftMath.hours(it) }
                                                    )
                                                },
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip
                                            )
                                        } else if (hasNightCarryOver) {
                                            Spacer(Modifier.height(3.dp))
                                            Box(
                                                Modifier
                                                    .size(5.dp)
                                                    .background(palette.cyan, CircleShape)
                                            )
                                            Text(
                                                text = "ночь",
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onAddForDate(selectedDate) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Смена вручную")
                }
                OutlinedButton(
                    onClick = { onOpenCycleGenerator(selectedDate) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Repeat, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Цикл 4/2...")
                }
            }
        }

        item {
            NeonCard(Modifier.fillMaxWidth(), accent = palette.cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ВЫБРАННЫЙ ДЕНЬ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            ShiftMath.fullDateLabel(selectedDate),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (selectedDayStats.shiftCount > 0) {
                                "${pluralizeShifts(selectedDayStats.shiftCount)} · ${ShiftMath.formatHours(selectedDayStats.totalHours)} · ${ShiftMath.formatCurrency(selectedDayStats.totalEarnings)}"
                            } else {
                                "Свободный день (выходной)"
                            },
                            fontSize = 12.sp,
                            color = if (selectedDayStats.shiftCount > 0) palette.green else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onAddForDate(selectedDate) }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Добавить смену на выбранный день",
                            tint = palette.cyan
                        )
                    }
                }
            }
        }

        if (selectedDayShifts.isNotEmpty()) {
            items(selectedDayShifts, key = { "sel_${it.id}" }) { shift ->
                val startsEarlier = ShiftMath.localDateOf(shift.startMillis, zoneId) != selectedDate
                ShiftRow(
                    shift = shift,
                    badgeNote = if (startsEarlier) "Ночная смена с предыдущего дня" else null,
                    onClick = { onEditShift(shift) },
                    onDelete = { onDeleteShift(shift.id) },
                    onAdjustHours = { delta -> onAdjustHours(shift.id, delta) }
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "СМЕНЫ МЕСЯЦА (${monthStats.shiftCount})",
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${ShiftMath.formatHours(monthStats.totalHours)} · ${ShiftMath.formatCurrency(monthStats.totalEarnings)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.green
                )
            }
        }

        if (monthShifts.isEmpty()) {
            item {
                Text(
                    "В этом месяце пока нет запланированных смен.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(monthShifts, key = { "month_${it.id}" }) { shift ->
                ShiftRow(
                    shift = shift,
                    onClick = { onEditShift(shift) },
                    onDelete = { onDeleteShift(shift.id) },
                    onAdjustHours = { delta -> onAdjustHours(shift.id, delta) }
                )
            }
        }
    }
}

private enum class StatsPeriodMode(val title: String) {
    MONTH("Месяц"),
    DAY("День"),
    CUSTOM("Период"),
    ALL("Всё время")
}

@Composable
fun StatsScreen(shifts: List<ShiftEntity>) {
    val palette = LocalCyberPalette.current
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)

    var mode by rememberSaveable { mutableStateOf(StatsPeriodMode.MONTH) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }

    var customFromText by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var customToText by rememberSaveable { mutableStateOf(today.toString()) }

    val targetMonth = YearMonth.from(today).plusMonths(monthOffset.toLong())
    val targetDay = today.plusDays(dayOffset.toLong())

    val stats: ShiftMath.PeriodStats
    val periodSubtitle: String
    when (mode) {
        StatsPeriodMode.MONTH -> {
            val (from, to) = ShiftMath.monthBounds(targetMonth, zoneId)
            stats = ShiftMath.calculatePeriodStats(shifts, from, to)
            periodSubtitle = ShiftMath.monthTitleLabel(targetMonth)
        }
        StatsPeriodMode.DAY -> {
            val (from, to) = ShiftMath.dayBounds(targetDay, zoneId)
            stats = ShiftMath.calculatePeriodStats(shifts, from, to)
            periodSubtitle = ShiftMath.fullDateLabel(targetDay)
        }
        StatsPeriodMode.CUSTOM -> {
            val fromDate = runCatching { LocalDate.parse(customFromText.trim()) }.getOrNull()
            val toDate = runCatching { LocalDate.parse(customToText.trim()) }.getOrNull()
            if (fromDate != null && toDate != null && !toDate.isBefore(fromDate)) {
                val fromMillis = fromDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val toMillis = toDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                stats = ShiftMath.calculatePeriodStats(shifts, fromMillis, toMillis)
                periodSubtitle = "$fromDate — $toDate"
            } else {
                stats = ShiftMath.PeriodStats(0, 0.0, 0.0, 0.0)
                periodSubtitle = "Укажи корректный диапазон дат"
            }
        }
        StatsPeriodMode.ALL -> {
            stats = ShiftMath.calculateAllTimeStats(shifts)
            periodSubtitle = "За всё время ведения графика"
        }
    }

    val (todayStart, todayEnd) = ShiftMath.dayBounds(today, zoneId)
    val todayStats = ShiftMath.calculatePeriodStats(shifts, todayStart, todayEnd)

    val prevMonth = targetMonth.minusMonths(1)
    val (prevFrom, prevTo) = ShiftMath.monthBounds(prevMonth, zoneId)
    val prevStats = ShiftMath.calculatePeriodStats(shifts, prevFrom, prevTo)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Статистика", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(periodSubtitle, color = MaterialTheme.colorScheme.secondary)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsPeriodMode.values().forEach { candidate ->
                    FilterChip(
                        selected = mode == candidate,
                        onClick = { mode = candidate },
                        label = { Text(candidate.title) }
                    )
                }
            }
        }

        if (mode == StatsPeriodMode.MONTH) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { monthOffset-- }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                        Text("Пред.")
                    }
                    if (monthOffset != 0) {
                        TextButton(onClick = { monthOffset = 0 }) {
                            Text("Текущий месяц")
                        }
                    }
                    OutlinedButton(onClick = { monthOffset++ }) {
                        Text("След.")
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        if (mode == StatsPeriodMode.DAY) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { dayOffset-- }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                        Text("−1 день")
                    }
                    if (dayOffset != 0) {
                        TextButton(onClick = { dayOffset = 0 }) {
                            Icon(Icons.Rounded.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Сегодня")
                        }
                    }
                    OutlinedButton(onClick = { dayOffset++ }) {
                        Text("+1 день")
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        if (mode == StatsPeriodMode.CUSTOM) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customFromText,
                        onValueChange = { customFromText = it },
                        label = { Text("С (ГГГГ-ММ-ДД)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customToText,
                        onValueChange = { customToText = it },
                        label = { Text("По (ГГГГ-ММ-ДД)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }

        item {
            MetricCard(
                title = "ЗАРАБОТОК ЗА ПЕРИОД",
                value = ShiftMath.formatCurrency(stats.totalEarnings),
                subtitle = "Учитываются почасовые, дневные смены и доп. действия",
                icon = Icons.Rounded.AccountBalanceWallet,
                accent = palette.green,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "ЧАСЫ В ПЕРИОДЕ",
                    value = ShiftMath.formatHours(stats.totalHours),
                    icon = Icons.Rounded.Schedule,
                    accent = palette.cyan,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "СМЕН В ПЕРИОДЕ",
                    value = "${stats.shiftCount}",
                    icon = Icons.Rounded.EventAvailable,
                    accent = palette.purple,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            NeonCard(Modifier.fillMaxWidth(), accent = palette.cyan) {
                Text(
                    "СРЕДНИЙ ДОХОД ЗА СМЕНУ",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (stats.shiftCount == 0) "—" else ShiftMath.formatCurrency(stats.averageShiftEarnings),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            NeonCard(Modifier.fillMaxWidth(), accent = palette.purple) {
                Text(
                    "ИТОГИ ЗА СЕГОДНЯ",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${pluralizeShifts(todayStats.shiftCount)} · ${ShiftMath.formatHours(todayStats.totalHours)} · ${ShiftMath.formatCurrency(todayStats.totalEarnings)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (mode == StatsPeriodMode.MONTH) {
            item {
                Text(
                    "СРАВНЕНИЕ С ПРЕДЫДУЩИМ МЕСЯЦЕМ (${ShiftMath.monthTitleLabel(prevMonth)})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${pluralizeShifts(prevStats.shiftCount)} · ${ShiftMath.formatHours(prevStats.totalHours)} · ${ShiftMath.formatCurrency(prevStats.totalEarnings)}",
                    fontSize = 15.sp
                )
            }
        }
    }
}

private data class FaqItem(
    val question: String,
    val answer: String
)

private val FunctionalFaqItems = listOf(
    FaqItem(
        question = "Как устроен главный экран?",
        answer = "На вкладке «Главная» отображается текущая идущая или ближайшая будущая смена, сводка заработка и часов за сегодня и за текущий месяц, а также список ближайших смен. Нажатие по карточке смены открывает её редактирование."
    ),
    FaqItem(
        question = "Чем отличается почасовая смена от дневной?",
        answer = "• Почасовая смена: заработок считается по формуле «часы × почасовая ставка», а ночные смены через полночь (например, 20:00–08:00) автоматически учитывают переход на следующие сутки.\n• Дневная смена: 1 выход (день) равен фиксированной стоимости дня независимо от длительности в часах. Тип смены можно выбрать при создании или переключить при изменении любой смены."
    ),
    FaqItem(
        question = "Как добавлять дополнительный заработок к дневной смене?",
        answer = "Открой или создай смену с типом «Дневная (за день)». В блоке «Дополнение к заработку» заполни поля: «Комментарий» (что сделано), «Сколько сделано» и «За 1 действие (₽)», затем нажми «Добавить действие к смене». Можно добавить несколько разных позиций — все они автоматически прибавятся к стоимости дня."
    ),
    FaqItem(
        question = "Как быстро менять часы и редактировать смены?",
        answer = "В любой карточке почасовой смены есть кнопки «−1 ч» и «+1 ч» для быстрой корректировки времени окончания (например, при задержке на смене). Чтобы изменить дату, точное время, ставку, тип смены или удалить смену, нажми на саму карточку или значок карандаша."
    ),
    FaqItem(
        question = "Как пользоваться календарём и генератором цикла 4/2?",
        answer = "Во вкладке «Календарь» нажми на любой день, чтобы увидеть смены и доход за эти сутки. Кнопка «Цикл 4/2...» открывает генератор повторяющегося графика «N рабочих / M выходных» (как для почасовых, так и для дневных смен). Повторный запуск не создаёт дубликатов, а смены, созданные или изменённые вручную, защищены от случайной перезаписи."
    ),
    FaqItem(
        question = "Как смотреть статистику за день, месяц или произвольный период?",
        answer = "Во вкладке «Статистика» переключай режимы вверху экрана: «Месяц» (со стрелками переключения месяцев и сравнением с прошлым месяцем), «День», «Период» (выбор любых дат «С» и «По») или «Всё время»."
    ),
    FaqItem(
        question = "Как работает синхронизация через сервисы Google?",
        answer = "График можно синхронизировать с облаком Google двумя способами: через привязку аккаунта Google (автоматическое и ручное сохранение в облачную папку приложения на Google Диске) либо через быстрый обмен файлом напрямую с приложением Google Диск на смартфоне — удобно для переноса графика между устройствами."
    ),
    FaqItem(
        question = "Как работают напоминания о сменах?",
        answer = "Приложение заранее предупреждает о предстоящем выходе на работу: за 24 часа (накануне смены) и за 1 час до начала смены. Если смена перенесена или удалена из графика, старое напоминание отменяется автоматически."
    ),
    FaqItem(
        question = "Как поддержать разработчика и отключить рекламу?",
        answer = "Во вкладке «Поддержка» можно отправить разовый донат (50 / 100 / 500 ₽) через защищённую форму ЮKassa. Сразу после подтверждения оплаты любая реклама в приложении отключается навсегда."
    ),
    FaqItem(
        question = "Как добавить виджет расписания на рабочий стол?",
        answer = "Доступны два виджета: «Shift Cyber — Расписание» (4×2) и «Shift Cyber — Мини» (2×2). Их можно закрепить одной кнопкой из приложения или выбрать в системном каталоге виджетов (на оболочке Xiaomi HyperOS сторонние виджеты находятся в меню «Виджеты» → «Приложения с несколькими виджетами» → «Все» → «Виджеты Android»). Нажатие на виджет открывает приложение."
    )
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    isSyncBusy: Boolean,
    onUpdateSettings: (AppSettings) -> Unit,
    onApplyRatesToFutureShifts: (Double, Double) -> Unit,
    onGoogleSignInClick: () -> Unit,
    onGoogleSignOutClick: () -> Unit,
    onGoogleUploadClick: () -> Unit,
    onGoogleDownloadClick: () -> Unit,
    onDriveFileExportClick: () -> Unit,
    onDriveFileImportClick: () -> Unit,
    onRefreshWidgetNow: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalCyberPalette.current

    var hourlyRateInput by remember(settings.hourlyRate) {
        mutableStateOf(ShiftMath.formatRate(settings.hourlyRate))
    }
    var dailyRateInput by remember(settings.dailyRate) {
        mutableStateOf(ShiftMath.formatRate(settings.dailyRate))
    }
    var rateError by remember { mutableStateOf<String?>(null) }
    var showSha1Helper by remember { mutableStateOf(false) }
    var expandedFaqIndex by rememberSaveable { mutableIntStateOf(-1) }

    val sha1Fingerprint = remember(context) { GoogleDriveSyncManager.getAppSigningSha1(context) }

    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
    ) {
        item {
            Text("Настройки", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Ставки, синхронизация Google, виджеты и FAQ", color = MaterialTheme.colorScheme.secondary)
        }

        // 1. Ставки (Почасовая + Дневная)
        item {
            NeonCard(Modifier.fillMaxWidth(), accent = palette.green) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Payments,
                            contentDescription = null,
                            tint = palette.green,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ставки по умолчанию", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Час: ${ShiftMath.formatCurrency(settings.hourlyRate)} · День: ${ShiftMath.formatCurrency(settings.dailyRate)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hourlyRateInput,
                            onValueChange = { hourlyRateInput = it; rateError = null },
                            label = { Text("Почасовая (₽/ч)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = dailyRateInput,
                            onValueChange = { dailyRateInput = it; rateError = null },
                            label = { Text("Дневная (₽/день)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    if (rateError != null) {
                        Text(rateError!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val hr = hourlyRateInput.trim().replace(',', '.').toDoubleOrNull()
                                val dr = dailyRateInput.trim().replace(',', '.').toDoubleOrNull()
                                if (hr == null || hr < 0.0 || !hr.isFinite() || dr == null || dr < 0.0 || !dr.isFinite()) {
                                    rateError = "Укажи неотрицательные суммы для обеих ставок."
                                } else {
                                    onUpdateSettings(settings.copy(hourlyRate = hr, dailyRate = dr))
                                    Toast.makeText(context, "Ставки сохранены", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Сохранить")
                        }
                        OutlinedButton(
                            onClick = {
                                val hr = hourlyRateInput.trim().replace(',', '.').toDoubleOrNull()
                                val dr = dailyRateInput.trim().replace(',', '.').toDoubleOrNull()
                                if (hr == null || hr < 0.0 || !hr.isFinite() || dr == null || dr < 0.0 || !dr.isFinite()) {
                                    rateError = "Укажи неотрицательные суммы."
                                } else {
                                    onUpdateSettings(settings.copy(hourlyRate = hr, dailyRate = dr))
                                    onApplyRatesToFutureShifts(hr, dr)
                                    Toast.makeText(context, "Ставки применены к будущим сменам", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("К будущим")
                        }
                    }
                }
            }
        }

        // 2. Синхронизация через сервисы Google
        item {
            NeonCard(Modifier.fillMaxWidth(), accent = palette.cyan) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (settings.googleAccountEmail.isNotBlank()) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = palette.cyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Синхронизация Google", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (settings.googleAccountEmail.isNotBlank()) {
                                    "Аккаунт: ${settings.googleAccountEmail}"
                                } else {
                                    "Облачное хранение в Google Диске и автобэкап Android"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (settings.lastSyncMillis > 0L) {
                                Text(
                                    text = "Последняя синхронизация: ${ShiftMath.dateTimeLabel(settings.lastSyncMillis)}",
                                    fontSize = 11.sp,
                                    color = palette.green
                                )
                            }
                        }
                        if (isSyncBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }

                    if (settings.googleAccountEmail.isBlank()) {
                        Button(
                            onClick = onGoogleSignInClick,
                            enabled = !isSyncBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.CloudSync, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Войти через Google")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onGoogleUploadClick,
                                enabled = !isSyncBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("В облако", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onGoogleDownloadClick,
                                enabled = !isSyncBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Из облака", fontSize = 12.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Автосинхронизация", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Обновлять копию в Google Cloud при изменениях",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoGoogleSync,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings(settings.copy(autoGoogleSync = enabled))
                                }
                            )
                        }

                        TextButton(
                            onClick = onGoogleSignOutClick,
                            enabled = !isSyncBusy
                        ) {
                            Text("Отключить аккаунт Google", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        "Прямой обмен через приложение Google Диск:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDriveFileExportClick,
                            enabled = !isSyncBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Сохранить в Диск", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onDriveFileImportClick,
                            enabled = !isSyncBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Загрузить с Диска", fontSize = 12.sp)
                        }
                    }

                    TextButton(
                        onClick = { showSha1Helper = !showSha1Helper },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            if (showSha1Helper) "Скрыть данные OAuth SHA-1" else "Показать SHA-1 для Google Cloud Console",
                            fontSize = 11.sp
                        )
                    }

                    AnimatedVisibility(visible = showSha1Helper) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Package: ru.shiftcyber", fontSize = 11.sp)
                                Text("SHA-1: $sha1Fingerprint", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("SHA-1", sha1Fingerprint))
                                    Toast.makeText(context, "SHA-1 скопирован", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Скопировать SHA-1", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // 3. Тема оформления
        item {
            NeonCard(Modifier.fillMaxWidth(), accent = palette.purple) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (settings.darkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        contentDescription = null,
                        tint = palette.purple,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Тема приложения", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.darkTheme) "Тёмная · Cyberpunk Neon" else "Светлая · VKUI Clean",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.darkTheme,
                        onCheckedChange = { enabled ->
                            onUpdateSettings(settings.copy(darkTheme = enabled))
                        }
                    )
                }
            }
        }

        // 4. Уведомления
        item {
            NeonCard(Modifier.fillMaxWidth(), accent = palette.cyan) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = palette.cyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Уведомления о сменах", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Автоматически обновляются при изменении или удалении смены",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!hasNotificationPermission) {
                        OutlinedButton(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Разрешить показ уведомлений (Android 13+)")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Накануне смены", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("За 24 часа до начала смены", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.notifyDayBefore,
                            onCheckedChange = { enabled ->
                                onUpdateSettings(settings.copy(notifyDayBefore = enabled))
                            }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("В день смены", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("За 1 час до начала смены", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.notifySameDay,
                            onCheckedChange = { enabled ->
                                onUpdateSettings(settings.copy(notifySameDay = enabled))
                            }
                        )
                    }
                }
            }
        }

        // 5. Виджеты рабочего стола
        item {
            NeonCard(Modifier.fillMaxWidth(), accent = palette.cyan) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Widgets,
                            contentDescription = null,
                            tint = palette.cyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Виджеты Shift Cyber", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Доступны в каталоге виджетов Android (4×2 и Мини 2×2) и по кнопкам ниже",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            96 to "Плотный",
                            88 to "Неон",
                            70 to "Стекло"
                        ).forEach { (alphaValue, label) ->
                            val selected = when (alphaValue) {
                                96 -> settings.widgetTransparency >= 93
                                70 -> settings.widgetTransparency <= 75
                                else -> settings.widgetTransparency in 76..92
                            }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onUpdateSettings(settings.copy(widgetTransparency = alphaValue))
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                requestPinWidget(context, ComponentName(context, ShiftWidgetProvider::class.java))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Виджет 4×2", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                requestPinWidget(context, ComponentName(context, ShiftCompactWidgetProvider::class.java))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Мини 2×2", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onRefreshWidgetNow()
                            Toast.makeText(context, "Виджеты обновлены", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Обновить данные виджетов")
                    }
                }
            }
        }

        // 6. FAQ по функционалу (вместо описания БД; без описания раздела настроек)
        item {
            NeonCard(Modifier.fillMaxWidth(), accent = palette.purple) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.HelpOutline,
                            contentDescription = null,
                            tint = palette.purple,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("FAQ — Функции приложения", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "Нажми на вопрос, чтобы открыть краткую инструкцию",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    FunctionalFaqItems.forEachIndexed { index, faq ->
                        val isOpen = expandedFaqIndex == index
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .clickable {
                                    expandedFaqIndex = if (isOpen) -1 else index
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = faq.question,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            AnimatedVisibility(visible = isOpen) {
                                Text(
                                    text = faq.answer,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun requestPinWidget(context: Context, provider: ComponentName) {
    try {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(
                context,
                "Лаунчер не поддерживает автозакрепление. Добавь виджет через каталог виджетов рабочего стола.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            val sent = manager.requestPinAppWidget(provider, null, null)
            Toast.makeText(
                context,
                if (sent) {
                    "Запрос отправлен лаунчеру. Подтверди добавление на экран."
                } else {
                    "Лаунчер отклонил запрос. Открой каталог виджетов рабочего стола."
                },
                Toast.LENGTH_LONG
            ).show()
        }
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Ошибка закрепления: ${e.message ?: "неизвестная ошибка"}",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
fun ShiftRow(
    shift: ShiftEntity,
    badgeNote: String? = null,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onAdjustHours: ((Int) -> Unit)? = null
) {
    val palette = LocalCyberPalette.current
    val accent = when (shift.colorKey) {
        "green" -> palette.green
        "cyan" -> palette.cyan
        else -> palette.purple
    }
    val canDecrease = ShiftMath.canAdjustHours(shift, -1)
    val canIncrease = ShiftMath.canAdjustHours(shift, 1)
    val extras = remember(shift.extrasJson, shift.payType) { ShiftMath.extraItems(shift) }
    val extrasSum = remember(extras) { extras.sumOf { it.total } }

    NeonCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        accent = accent
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(50.dp)
                    .background(accent, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shift.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (shift.isDaily) "дневная" else if (shift.isManual) "ручн." else "цикл",
                        fontSize = 10.sp,
                        color = if (shift.isDaily) palette.green else MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = "${ShiftMath.dateLabel(shift.startMillis)} · ${ShiftMath.timeRangeLabel(shift)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (shift.isDaily && extras.isNotEmpty()) {
                    val extrasText = if (extras.size == 1 && extras.first().comment.isNotBlank()) {
                        "${extras.first().comment}: ${ShiftMath.formatQuantity(extras.first().quantity)} × ${ShiftMath.formatCurrency(extras.first().unitPrice)} (+${ShiftMath.formatCurrency(extrasSum)})"
                    } else {
                        "Доп. заработок: ${extras.size} поз. (+${ShiftMath.formatCurrency(extrasSum)})"
                    }
                    Text(
                        text = extrasText,
                        fontSize = 11.sp,
                        color = palette.green,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (badgeNote != null) {
                    Text(
                        text = badgeNote,
                        fontSize = 11.sp,
                        color = palette.cyan
                    )
                }
                if (!shift.isDaily && onAdjustHours != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Часы:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { onAdjustHours(-1) },
                            enabled = canDecrease,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("−1 ч", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = { onAdjustHours(1) },
                            enabled = canIncrease,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("+1 ч", fontSize = 12.sp)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = ShiftMath.formatCurrency(ShiftMath.earnings(shift)),
                    color = palette.green,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = if (shift.isDaily) "1 день" else ShiftMath.formatHours(ShiftMath.hours(shift)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row {
                    if (onClick != null) {
                        IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Изменить смену",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Удалить смену",
                                tint = palette.danger,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    NeonCard(modifier, accent) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderAlpha by animateFloatAsState(targetValue = 0.24f, label = "glow")
    Column(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = accent.copy(alpha = 0.12f),
                spotColor = accent.copy(alpha = 0.16f)
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), RoundedCornerShape(22.dp))
            .border(1.dp, accent.copy(alpha = borderAlpha), RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content
    )
}
