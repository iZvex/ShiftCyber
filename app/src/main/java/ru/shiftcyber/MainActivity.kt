package ru.shiftcyber

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import kotlinx.coroutines.launch
import ru.shiftcyber.data.*
import ru.shiftcyber.domain.ShiftMath
import ru.shiftcyber.notifications.ReminderScheduler
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

private val NeonPurple = Color(0xFFB49AFF)
private val NeonCyan = Color(0xFF65E7F2)
private val NeonGreen = Color(0xFF72F0B2)
private val DarkBg = Color(0xFF0B0D16)
private val DarkCard = Color(0xFF171A29)
private val LightBg = Color(0xFFF4F3FA)
private val LightCard = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {
    private lateinit var db: ShiftDatabase
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = ShiftDatabase.get(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val shifts by db.shifts().observeAll().collectAsState(initial = emptyList())
            val settings by db.settings().observe().collectAsState(initial = null)
            var dark by remember { mutableStateOf(true) }
            LaunchedEffect(settings) { settings?.let { dark = it.darkTheme } }
            ShiftCyberTheme(dark) {
                ShiftCyberApp(
                    shifts = shifts,
                    dark = dark,
                    onToggleTheme = {
                        dark = !dark
                        lifecycleScope.launch { db.settings().save((settings ?: AppSettings()).copy(darkTheme = dark)) }
                    },
                    onAddShift = { start, end, title, rate, breakMin ->
                        lifecycleScope.launch {
                            val id = db.shifts().upsert(ShiftEntity(
                                startMillis = start, endMillis = end, title = title,
                                hourlyRate = rate, breakMinutes = breakMin, isManual = true
                            ))
                            val s = settings ?: AppSettings(hourlyRate = rate)
                            ReminderScheduler.schedule(this@MainActivity, id, title, start, s.notifyDayBefore, s.notifySameDay)
                        }
                    },
                    onDelete = { id -> lifecycleScope.launch { db.shifts().delete(id) } },
                    onGenerate = { anchor, days, cycle, startHour, rate ->
                        lifecycleScope.launch {
                            val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -3); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0) }.timeInMillis
                            val to = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0) }.timeInMillis
                            val generated = ru.shiftcyber.domain.CycleGenerator.generate(anchor, from, to, cycle, startHour, rate)
                            val saved = mutableListOf<Pair<Long, ShiftEntity>>()
                            db.withTransaction {
                                generated.forEach { shift ->
                                    val insertedId = db.shifts().upsert(shift)
                                    saved += insertedId to shift.copy(id = insertedId)
                                }
                            }
                            saved.forEach { (insertedId, shift) ->
                                ReminderScheduler.schedule(
                                    this@MainActivity, insertedId, shift.title, shift.startMillis, true, true
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ShiftCyberTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = NeonPurple, secondary = NeonCyan, tertiary = NeonGreen,
        background = DarkBg, surface = DarkCard, onBackground = Color(0xFFF6F3FF), onSurface = Color(0xFFF6F3FF)
    ) else lightColorScheme(
        primary = Color(0xFF7046C8), secondary = Color(0xFF087F91), tertiary = Color(0xFF168454),
        background = LightBg, surface = LightCard, onBackground = Color(0xFF191627), onSurface = Color(0xFF191627)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class Tab(val label: String) {
    HOME("Главная"), CALENDAR("Календарь"), STATS("Статистика"), SETTINGS("Настройки")
}

@Composable
private fun ShiftCyberApp(
    shifts: List<ShiftEntity>, dark: Boolean,
    onToggleTheme: () -> Unit,
    onAddShift: (Long, Long, String, Double, Int) -> Unit,
    onDelete: (Long) -> Unit,
    onGenerate: (Long, Int, List<Int>, Int, Double) -> Unit
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCycle by remember { mutableStateOf(false) }
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, if (dark) Color(0xFF12172A) else Color(0xFFE9E6F7))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SHIFT", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("CYBER", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                Box(Modifier.size(44.dp).border(1.dp, NeonPurple.copy(alpha=.5f), CircleShape).background(NeonPurple.copy(alpha=.1f), CircleShape), Alignment.Center) {
                    Icon(Icons.Rounded.Bolt, null, tint = NeonPurple, modifier = Modifier.size(25.dp))
                }
            }
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab-content", modifier = Modifier.weight(1f)) { selected ->
                when (selected) {
                    Tab.HOME -> HomeScreen(
                        shifts,
                        onAdd = { selectedDateMillis = System.currentTimeMillis(); showAdd = true },
                        onCalendar = { tab = Tab.CALENDAR }
                    )
                    Tab.CALENDAR -> CalendarScreen(
                        shifts,
                        onAdd = { dateMillis -> selectedDateMillis = dateMillis; showAdd = true },
                        onDelete = onDelete,
                        onCycle = { showCycle = true }
                    )
                    Tab.STATS -> StatsScreen(shifts)
                    Tab.SETTINGS -> SettingsScreen(dark, onToggleTheme)
                }
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f), tonalElevation = 0.dp) {
                listOf(Icons.Rounded.SpaceDashboard, Icons.Rounded.CalendarMonth, Icons.Rounded.QueryStats, Icons.Rounded.Tune).forEachIndexed { index, icon ->
                    val item = Tab.values()[index]
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icon, null) },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.secondary, indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha=.14f))
                    )
                }
            }
        }
    }
    if (showAdd) AddShiftDialog(
        initialDateMillis = selectedDateMillis,
        onDismiss = { showAdd = false },
        onSave = { a,b,c,d,e -> onAddShift(a,b,c,d,e); showAdd = false }
    )
    if (showCycle) CycleDialog(onDismiss = { showCycle = false }, onGenerate = { a,b,c,d,e -> onGenerate(a,b,c,d,e); showCycle = false })
}

@Composable
private fun HomeScreen(shifts: List<ShiftEntity>, onAdd: () -> Unit, onCalendar: () -> Unit) {
    val now = System.currentTimeMillis()
    val next = shifts.firstOrNull { it.endMillis > now }
    val month = Calendar.getInstance()
    val bounds = ShiftMath.monthBounds(month.get(Calendar.YEAR), month.get(Calendar.MONTH))
    val monthShifts = shifts.filter { it.startMillis in bounds.first until bounds.second }
    val hours = monthShifts.sumOf { ShiftMath.hours(it) }
    val money = monthShifts.sumOf { ShiftMath.earnings(it) }
    LazyColumn(contentPadding = PaddingValues(start=18.dp,end=18.dp,top=4.dp,bottom=20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("ТВОЙ РИТМ. ТВОИ ПРАВИЛА.", fontSize = 11.sp, letterSpacing = 1.8.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Привет, на смене? ⚡", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        item {
            NeonCard(Modifier.fillMaxWidth(), accent = NeonPurple) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (next == null) "НЕТ БЛИЖАЙШИХ СМЕН" else "СЛЕДУЮЩАЯ СМЕНА", fontSize=11.sp, color=MaterialTheme.colorScheme.secondary, letterSpacing=1.sp, fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(next?.title ?: "Время отдохнуть", fontSize=22.sp, fontWeight=FontWeight.Bold)
                        if (next != null) {
                            Text("${ShiftMath.dateLabel(next.startMillis)} · ${ShiftMath.timeLabel(next.startMillis)}–${ShiftMath.timeLabel(next.endMillis)}", color=MaterialTheme.colorScheme.onSurface.copy(alpha=.72f), fontSize=13.sp)
                        } else Text("Добавь смену или создай повторяющийся цикл", fontSize=13.sp, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.72f))
                    }
                    Icon(Icons.Rounded.Bolt, null, tint=NeonPurple, modifier=Modifier.size(38.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MetricCard("ЗАРАБОТОК", String.format(Locale.getDefault(), "%.0f €", money), Icons.Rounded.AccountBalanceWallet, NeonGreen, Modifier.weight(1f))
                MetricCard("ЧАСЫ", String.format(Locale.getDefault(), "%.1f ч", hours), Icons.Rounded.Schedule, NeonCyan, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick=onAdd, modifier=Modifier.weight(1f), shape=RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Добавить смену")
                }
                OutlinedButton(onClick=onCalendar, modifier=Modifier.weight(1f), shape=RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text("Календарь")
                }
            }
        }
        item { Text("БЛИЖАЙШИЕ ВЫХОДЫ", fontSize=12.sp, fontWeight=FontWeight.Bold, letterSpacing=1.5.sp, color=MaterialTheme.colorScheme.secondary) }
        if (shifts.filter { it.endMillis > now }.take(4).isEmpty()) {
            item { Text("Пока пусто — добавь смену, чтобы начать.", color=MaterialTheme.colorScheme.onBackground.copy(alpha=.65f)) }
        } else items(shifts.filter { it.endMillis > now }.take(4)) { shift -> ShiftRow(shift) }
    }
}

@Composable
private fun CalendarScreen(shifts: List<ShiftEntity>, onAdd: (Long) -> Unit, onDelete: (Long) -> Unit, onCycle: () -> Unit) {
    var monthOffset by remember { mutableIntStateOf(0) }
    val month = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
    val year = month.get(Calendar.YEAR); val monthIndex = month.get(Calendar.MONTH)
    val bounds = ShiftMath.monthBounds(year, monthIndex)
    val days = Calendar.getInstance().apply { set(year, monthIndex, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDow = ((Calendar.getInstance().apply { set(year, monthIndex, 1) }.get(Calendar.DAY_OF_WEEK) + 5) % 7)
    val byDay = shifts.filter { it.startMillis in bounds.first until bounds.second }.groupBy {
        Calendar.getInstance().apply { timeInMillis = it.startMillis }.get(Calendar.DAY_OF_MONTH)
    }
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Календарь", fontSize=25.sp, fontWeight=FontWeight.Bold)
                Text(SimpleDateFormat("LLLL yyyy", Locale("ru")).format(month.time).replaceFirstChar { it.uppercase() }, color=MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick={monthOffset--}) { Icon(Icons.Rounded.ChevronLeft, null) }
            IconButton(onClick={monthOffset++}) { Icon(Icons.Rounded.ChevronRight, null) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            listOf("ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС").forEach { Text(it, modifier=Modifier.weight(1f), fontSize=10.sp, color=MaterialTheme.colorScheme.onBackground.copy(alpha=.55f), textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
        }
        Spacer(Modifier.height(8.dp))
        val cellCount = ((firstDow + days + 6) / 7) * 7
        for (week in 0 until cellCount/7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                for (col in 0..6) {
                    val day = week*7+col-firstDow+1
                    Box(Modifier.weight(1f).aspectRatio(0.78f).padding(vertical=2.dp)) {
                        if (day in 1..days) {
                            val has = byDay[day].orEmpty().isNotEmpty()
                            val isToday = day == Calendar.getInstance().get(Calendar.DAY_OF_MONTH) && monthOffset == 0
                            Column(
                                Modifier.fillMaxSize()
                                    .background(if (has) NeonPurple.copy(alpha=.15f) else MaterialTheme.colorScheme.surface.copy(alpha=.65f), RoundedCornerShape(12.dp))
                                    .then(if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp)) else Modifier)
                                    .clickable {
                                        val selected = Calendar.getInstance().apply {
                                            set(year, monthIndex, day, 12, 0, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                        onAdd(selected)
                                    }
                                    .padding(6.dp),
                                horizontalAlignment=Alignment.CenterHorizontally
                            ) {
                                Text("$day", fontSize=13.sp, fontWeight=if (isToday) FontWeight.Bold else FontWeight.Normal)
                                if (has) {
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.size(6.dp).background(NeonPurple, CircleShape))
                                    Text("${byDay[day]!!.size} смен", fontSize=8.sp, color=MaterialTheme.colorScheme.secondary, maxLines=1)
                                } else Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick={ onAdd(System.currentTimeMillis()) }, modifier=Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null); Text("Вручную") }
            OutlinedButton(onClick=onCycle, modifier=Modifier.weight(1f)) { Icon(Icons.Rounded.Repeat, null); Text("Создать цикл") }
        }
        Spacer(Modifier.height(12.dp))
        Text("СМЕНЫ МЕСЯЦА", fontSize=11.sp, letterSpacing=1.5.sp, color=MaterialTheme.colorScheme.secondary, fontWeight=FontWeight.Bold)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp), contentPadding=PaddingValues(bottom=12.dp)) {
            items(shifts.filter { it.startMillis in bounds.first until bounds.second }) { ShiftRow(it, onDelete = { onDelete(it.id) }) }
        }
    }
}

@Composable
private fun StatsScreen(shifts: List<ShiftEntity>) {
    val cal = Calendar.getInstance()
    val bounds = ShiftMath.monthBounds(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    val month = shifts.filter { it.startMillis in bounds.first until bounds.second }
    val hours = month.sumOf { ShiftMath.hours(it) }
    val pay = month.sumOf { ShiftMath.earnings(it) }
    val previous = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
    val prevBounds = ShiftMath.monthBounds(previous.get(Calendar.YEAR), previous.get(Calendar.MONTH))
    val prev = shifts.filter { it.startMillis in prevBounds.first until prevBounds.second }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp), contentPadding=PaddingValues(bottom=20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text("Статистика", fontSize=26.sp, fontWeight=FontWeight.Bold); Text("Твои цифры за месяц", color=MaterialTheme.colorScheme.secondary) }
        item { MetricCard("ПЛАНОВЫЙ ДОХОД", String.format(Locale.getDefault(), "%.2f €", pay), Icons.Rounded.Euro, NeonGreen, Modifier.fillMaxWidth()) }
        item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MetricCard("ОТРАБОТАНО", String.format(Locale.getDefault(), "%.1f ч", hours), Icons.Rounded.Schedule, NeonCyan, Modifier.weight(1f))
            MetricCard("СМЕН", "${month.size}", Icons.Rounded.EventAvailable, NeonPurple, Modifier.weight(1f))
        } }
        item { NeonCard(Modifier.fillMaxWidth(), NeonCyan) {
            Text("СРЕДНИЙ ДОХОД ЗА СМЕНУ", fontSize=11.sp, color=MaterialTheme.colorScheme.secondary, fontWeight=FontWeight.Bold)
            Text(if (month.isEmpty()) "—" else String.format(Locale.getDefault(), "%.2f €", pay/month.size), fontSize=25.sp, fontWeight=FontWeight.Bold)
        } }
        item { Text("СРАВНЕНИЕ С ПРОШЛЫМ МЕСЯЦЕМ", fontSize=11.sp, color=MaterialTheme.colorScheme.secondary, fontWeight=FontWeight.Bold) }
        item { Text("${prev.size} смен · ${String.format(Locale.getDefault(), "%.1f", prev.sumOf { ShiftMath.hours(it) })} ч · ${String.format(Locale.getDefault(), "%.2f", prev.sumOf { ShiftMath.earnings(it) })} €", fontSize=16.sp) }
        item { Text("Расчёт основан на ставке каждой смены и не включает налоги, надбавки или переработки.", fontSize=12.sp, color=MaterialTheme.colorScheme.onBackground.copy(alpha=.6f)) }
    }
}

@Composable
private fun SettingsScreen(dark: Boolean, onToggleTheme: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp), verticalArrangement=Arrangement.spacedBy(12.dp), contentPadding=PaddingValues(bottom=24.dp)) {
        item { Text("Настройки", fontSize=26.sp, fontWeight=FontWeight.Bold); Text("Подстрой приложение под себя", color=MaterialTheme.colorScheme.secondary) }
        item { NeonCard(Modifier.fillMaxWidth(), NeonPurple) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(if (dark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, null, tint=NeonPurple, modifier=Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Тема приложения", fontWeight=FontWeight.SemiBold)
                    Text(if (dark) "Тёмная · Cyberpunk" else "Светлая · Neon", fontSize=12.sp, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
                }
                Switch(checked=dark, onCheckedChange={onToggleTheme()})
            }
        } }
        item { SettingInfo(Icons.Rounded.NotificationsActive, "Уведомления", "Напоминания о сменах планируются при добавлении смены.") }
        item { SettingInfo(Icons.Rounded.Widgets, "Виджет", "Добавь Shift Cyber через меню виджетов на главном экране. Размер можно менять удержанием.") }
        item { SettingInfo(Icons.Rounded.Storage, "Локальные данные", "График хранится на устройстве. Резервное копирование пока не реализовано.") }
        item { SettingInfo(Icons.Rounded.Palette, "Дизайн", "Неоновые акценты, мягкие градиенты и анимированные переходы.") }
    }
}

@Composable
private fun SettingInfo(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    NeonCard(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
        Row(verticalAlignment=Alignment.Top) {
            Icon(icon, null, tint=MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize=12.sp, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.65f))
            }
        }
    }
}

@Composable
private fun ShiftRow(shift: ShiftEntity, onDelete: (() -> Unit)? = null) {
    NeonCard(Modifier.fillMaxWidth(), when(shift.colorKey) { "green" -> NeonGreen; "cyan" -> NeonCyan; else -> NeonPurple }) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(46.dp).background(NeonPurple, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(shift.title, fontWeight=FontWeight.SemiBold)
                Text("${ShiftMath.dateLabel(shift.startMillis)} · ${ShiftMath.timeLabel(shift.startMillis)}–${ShiftMath.timeLabel(shift.endMillis)}", fontSize=12.sp, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.62f))
            }
            Column(horizontalAlignment=Alignment.End) {
                Text(String.format(Locale.getDefault(), "%.0f €", ShiftMath.earnings(shift)), color=NeonGreen, fontWeight=FontWeight.Bold)
                Text(String.format(Locale.getDefault(), "%.1f ч", ShiftMath.hours(shift)), fontSize=11.sp, color=MaterialTheme.colorScheme.secondary)
            }
            if (onDelete != null) IconButton(onClick=onDelete) { Icon(Icons.Rounded.DeleteOutline, null, tint=Color(0xFFFF7F9C)) }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier) {
    NeonCard(modifier, accent) {
        Icon(icon, null, tint=accent, modifier=Modifier.size(23.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize=10.sp, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.62f), letterSpacing=1.sp, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize=22.sp, fontWeight=FontWeight.Bold, maxLines=1)
    }
}

@Composable
private fun NeonCard(modifier: Modifier = Modifier, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    val alpha by animateFloatAsState(targetValue=.16f, label="glow")
    Column(
        modifier.shadow(12.dp, RoundedCornerShape(22.dp), ambientColor=accent.copy(alpha=.12f), spotColor=accent.copy(alpha=.16f))
            .background(MaterialTheme.colorScheme.surface.copy(alpha=.95f), RoundedCornerShape(22.dp))
            .border(1.dp, accent.copy(alpha=alpha), RoundedCornerShape(22.dp))
            .padding(16.dp),
        content=content
    )
}

@Composable
private fun AddShiftDialog(initialDateMillis: Long, onDismiss: () -> Unit, onSave: (Long,Long,String,Double,Int)->Unit) {
    var title by remember { mutableStateOf("Рабочая смена") }
    var date by remember(initialDateMillis) { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(initialDateMillis))) }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("18:00") }
    var rate by remember { mutableStateOf("15") }
    var breakMin by remember { mutableStateOf("30") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={ Text("Новая смена", fontWeight=FontWeight.Bold) },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title,{title=it},label={Text("Название")},singleLine=true)
                OutlinedTextField(date,{date=it},label={Text("Дата (ГГГГ-ММ-ДД)")},singleLine=true)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start,{start=it},label={Text("Начало")}, modifier=Modifier.weight(1f),singleLine=true)
                    OutlinedTextField(end,{end=it},label={Text("Конец")}, modifier=Modifier.weight(1f),singleLine=true)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(rate,{rate=it},label={Text("€/час")}, modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                    OutlinedTextField(breakMin,{breakMin=it},label={Text("Перерыв, мин")}, modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                }
                if (error != null) Text(error!!, color=MaterialTheme.colorScheme.error, fontSize=12.sp)
            }
        },
        confirmButton={ Button(onClick={
            try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient=false }.parse(date)!!
                val day = Calendar.getInstance().apply { time=d }
                fun parseTime(s:String): Pair<Int,Int> {
                    val p=s.split(":"); require(p.size==2)
                    return p[0].toInt().coerceIn(0,23) to p[1].toInt().coerceIn(0,59)
                }
                val (sh,sm)=parseTime(start); val (eh,em)=parseTime(end)
                val a=Calendar.getInstance().apply { time=d; set(Calendar.HOUR_OF_DAY,sh); set(Calendar.MINUTE,sm); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
                val b=Calendar.getInstance().apply { time=d; set(Calendar.HOUR_OF_DAY,eh); set(Calendar.MINUTE,em); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0); if (timeInMillis <= a.timeInMillis) add(Calendar.DAY_OF_MONTH,1) }
                val r=rate.replace(",",".").toDouble()
                require(r >= 0)
                onSave(a.timeInMillis,b.timeInMillis,title.ifBlank{"Рабочая смена"},r,breakMin.toIntOrNull()?.coerceAtLeast(0) ?: 0)
            } catch (e: Exception) { error="Проверь дату, время и ставку." }
        }) { Text("Сохранить") } },
        dismissButton={ TextButton(onClick=onDismiss){Text("Отмена")} }
    )
}

@Composable
private fun CycleDialog(onDismiss: () -> Unit, onGenerate: (Long,Int,List<Int>,Int,Double)->Unit) {
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var cycle by remember { mutableStateOf("12,12,0,0") }
    var days by remember { mutableStateOf("90") }
    var startHour by remember { mutableStateOf("9") }
    var rate by remember { mutableStateOf("15") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("Повторяющийся график")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Укажи длительность каждой даты цикла в часах. 0 — выходной.", fontSize=12.sp, color=MaterialTheme.colorScheme.secondary)
                OutlinedTextField(date,{date=it},label={Text("Дата начала (ГГГГ-ММ-ДД")},singleLine=true)
                OutlinedTextField(cycle,{cycle=it},label={Text("Цикл, часы через запятую")},singleLine=true)
                OutlinedTextField(days,{days=it},label={Text("Генерировать на дней")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startHour,{startHour=it},label={Text("Начало, час")},modifier=Modifier.weight(1f),singleLine=true)
                    OutlinedTextField(rate,{rate=it},label={Text("Ставка €/ч")},modifier=Modifier.weight(1f),singleLine=true)
                }
                if (error != null) Text(error!!, color=MaterialTheme.colorScheme.error, fontSize=12.sp)
            }
        },
        confirmButton={Button(onClick={
            try {
                val anchor=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply { isLenient=false }.parse(date)!!.time
                val pattern=cycle.split(",").map { it.trim().toInt() }
                require(pattern.isNotEmpty() && pattern.all { it in 0..24 })
                val n=days.toInt().coerceIn(1,730)
                val h=startHour.toInt().coerceIn(0,23)
                val r=rate.replace(",",".").toDouble()
                require(r >= 0)
                onGenerate(anchor,n,pattern,h,r)
            } catch (e: Exception) { error="Проверь дату, цикл и числовые значения." }
        }) {Text("Создать график")} },
        dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}}
    )
}
