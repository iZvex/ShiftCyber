package ru.shiftcyber

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.shiftcyber.data.AppSettings
import ru.shiftcyber.data.ShiftDatabase
import ru.shiftcyber.data.ShiftEntity
import ru.shiftcyber.domain.CycleGenerator
import ru.shiftcyber.domain.ShiftMath
import ru.shiftcyber.notifications.ReminderScheduler
import ru.shiftcyber.ui.CalendarScreen
import ru.shiftcyber.ui.CycleGenerationRequest
import ru.shiftcyber.ui.CycleGeneratorDialog
import ru.shiftcyber.ui.HomeScreen
import ru.shiftcyber.ui.LocalCyberPalette
import ru.shiftcyber.ui.SettingsScreen
import ru.shiftcyber.ui.ShiftCyberTheme
import ru.shiftcyber.ui.ShiftEditorDialog
import ru.shiftcyber.ui.StatsScreen
import ru.shiftcyber.widget.WidgetUpdater
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private lateinit var db: ShiftDatabase

    private val askNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = ShiftDatabase.get(this)
        requestNotificationsIfNeeded()

        setContent {
            val shifts by db.shifts().observeAll().collectAsState(initial = emptyList())
            val storedSettings by db.settings().observe().collectAsState(initial = null)
            val activeSettings = storedSettings ?: AppSettings()

            LaunchedEffect(shifts, storedSettings) {
                withContext(Dispatchers.IO) {
                    WidgetUpdater.refreshWithSnapshot(
                        context = this@MainActivity,
                        shifts = shifts,
                        settings = activeSettings
                    )
                }
            }

            ShiftCyberTheme(dark = activeSettings.darkTheme) {
                ShiftCyberApp(
                    shifts = shifts,
                    settings = activeSettings,
                    onSaveShift = { shift ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val currentSettings = db.settings().getOnce() ?: activeSettings
                            val insertedId = db.shifts().upsert(shift)
                            val finalId = if (shift.id != 0L) shift.id else insertedId
                            ReminderScheduler.schedule(
                                context = applicationContext,
                                id = finalId,
                                title = shift.title,
                                startMillis = shift.startMillis,
                                dayBefore = currentSettings.notifyDayBefore,
                                sameDay = currentSettings.notifySameDay
                            )
                            WidgetUpdater.refreshFromDatabase(applicationContext)
                        }
                    },
                    onDeleteShift = { id ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.shifts().delete(id)
                            ReminderScheduler.cancel(applicationContext, id)
                            WidgetUpdater.refreshFromDatabase(applicationContext)
                        }
                    },
                    onAdjustHours = { id, delta ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val existing = db.shifts().getById(id) ?: return@launch
                            val adjusted = ShiftMath.adjustDurationHours(existing, delta)
                                ?.copy(isManual = true)
                                ?: return@launch
                            db.shifts().upsert(adjusted)
                            WidgetUpdater.refreshFromDatabase(applicationContext)
                        }
                    },
                    onGenerateCycle = { request ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val zoneId = ZoneId.systemDefault()
                            val currentSettings = db.settings().getOnce() ?: activeSettings
                            val candidates = CycleGenerator.generateWorkRestCycle(
                                anchorDate = request.anchorDate,
                                daysForward = request.daysForward,
                                workDays = request.workDays,
                                restDays = request.restDays,
                                shiftDurationHours = request.shiftDurationHours,
                                startHour = request.startHour,
                                startMinute = request.startMinute,
                                hourlyRate = request.hourlyRate,
                                title = request.title,
                                colorKey = request.colorKey,
                                zoneId = zoneId
                            )
                            val windowStart = request.anchorDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                            val windowEnd = request.anchorDate
                                .plusDays(request.daysForward.toLong())
                                .atStartOfDay(zoneId)
                                .toInstant()
                                .toEpochMilli()

                            val savedShifts = mutableListOf<ShiftEntity>()
                            lateinit var plan: CycleGenerator.CycleMergePlan

                            db.withTransaction {
                                val existingAll = db.shifts().getAll()
                                plan = CycleGenerator.resolveConflicts(
                                    candidates = candidates,
                                    existingShifts = existingAll,
                                    windowStartMillis = windowStart,
                                    windowEndMillis = windowEnd,
                                    replaceAutoInWindow = request.replaceAutoInWindow,
                                    manualPolicy = request.manualPolicy,
                                    zoneId = zoneId
                                )
                                if (plan.toDeleteIds.isNotEmpty()) {
                                    db.shifts().deleteByIds(plan.toDeleteIds)
                                }
                                plan.toInsert.forEach { shift ->
                                    val newId = db.shifts().upsert(shift)
                                    savedShifts += shift.copy(id = newId)
                                }
                            }

                            if (plan.toDeleteIds.isNotEmpty()) {
                                ReminderScheduler.cancelMany(applicationContext, plan.toDeleteIds)
                            }
                            savedShifts.forEach { shift ->
                                ReminderScheduler.schedule(
                                    context = applicationContext,
                                    id = shift.id,
                                    title = shift.title,
                                    startMillis = shift.startMillis,
                                    dayBefore = currentSettings.notifyDayBefore,
                                    sameDay = currentSettings.notifySameDay
                                )
                            }
                            WidgetUpdater.refreshFromDatabase(applicationContext)

                            val summary = buildString {
                                append("Создано: ${savedShifts.size}")
                                if (plan.keptIdenticalCount > 0) {
                                    append(", уже было: ${plan.keptIdenticalCount}")
                                }
                                if (plan.skippedManualCount > 0) {
                                    append(", сохранено ручных: ${plan.skippedManualCount}")
                                }
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, summary, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onUpdateSettings = { updated ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val previous = db.settings().getOnce() ?: activeSettings
                            db.settings().save(updated)
                            if (previous.notifyDayBefore != updated.notifyDayBefore ||
                                previous.notifySameDay != updated.notifySameDay
                            ) {
                                val allShifts = db.shifts().getAll()
                                ReminderScheduler.rescheduleAll(applicationContext, allShifts, updated)
                            }
                            WidgetUpdater.refreshFromDatabase(applicationContext)
                        }
                    },
                    onApplyRateToFutureShifts = { newRate ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val now = System.currentTimeMillis()
                            db.withTransaction {
                                val futureShifts = db.shifts().getAll().filter { it.startMillis > now }
                                if (futureShifts.isNotEmpty()) {
                                    db.shifts().upsertAll(futureShifts.map { it.copy(hourlyRate = newRate) })
                                }
                            }
                            WidgetUpdater.refreshFromDatabase(applicationContext)
                        }
                    },
                    onRefreshWidgetNow = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            WidgetUpdater.refreshFromDatabase(applicationContext)
                        }
                    },
                    onRequestNotificationPermission = {
                        requestNotificationsIfNeeded(force = true)
                    }
                )
            }
        }
    }

    private fun requestNotificationsIfNeeded(force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted || force) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private enum class Tab(val label: String) {
    HOME("Главная"),
    CALENDAR("Календарь"),
    STATS("Статистика"),
    SETTINGS("Настройки")
}

@Composable
private fun ShiftCyberApp(
    shifts: List<ShiftEntity>,
    settings: AppSettings,
    onSaveShift: (ShiftEntity) -> Unit,
    onDeleteShift: (Long) -> Unit,
    onAdjustHours: (Long, Int) -> Unit,
    onGenerateCycle: (CycleGenerationRequest) -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onApplyRateToFutureShifts: (Double) -> Unit,
    onRefreshWidgetNow: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val palette = LocalCyberPalette.current
    val zoneId = ZoneId.systemDefault()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var selectedEpochDay by rememberSaveable { mutableStateOf(LocalDate.now(zoneId).toEpochDay()) }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)

    var showEditor by remember { mutableStateOf(false) }
    var editingShift by remember { mutableStateOf<ShiftEntity?>(null) }
    var editorInitialDate by remember { mutableStateOf(LocalDate.now(zoneId)) }

    var showCycleDialog by remember { mutableStateOf(false) }
    var cycleAnchorDate by remember { mutableStateOf(LocalDate.now(zoneId)) }

    val bg = MaterialTheme.colorScheme.background

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg, palette.gradientEnd)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "SHIFT",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    Text(
                        "CYBER",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .border(1.dp, palette.purple.copy(alpha = 0.5f), CircleShape)
                        .background(palette.purple.copy(alpha = 0.12f), CircleShape),
                    Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = palette.purple,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab-content",
                modifier = Modifier.weight(1f)
            ) { currentTab ->
                when (currentTab) {
                    Tab.HOME -> HomeScreen(
                        shifts = shifts,
                        onAddShift = {
                            editingShift = null
                            editorInitialDate = LocalDate.now(zoneId)
                            showEditor = true
                        },
                        onEditShift = { shift ->
                            editingShift = shift
                            editorInitialDate = ShiftMath.localDateOf(shift.startMillis, zoneId)
                            showEditor = true
                        },
                        onDeleteShift = onDeleteShift,
                        onAdjustHours = onAdjustHours,
                        onOpenCalendar = { tab = Tab.CALENDAR }
                    )

                    Tab.CALENDAR -> CalendarScreen(
                        shifts = shifts,
                        selectedDate = selectedDate,
                        onSelectDate = { selectedEpochDay = it.toEpochDay() },
                        onAddForDate = { date ->
                            editingShift = null
                            editorInitialDate = date
                            showEditor = true
                        },
                        onEditShift = { shift ->
                            editingShift = shift
                            editorInitialDate = ShiftMath.localDateOf(shift.startMillis, zoneId)
                            showEditor = true
                        },
                        onDeleteShift = onDeleteShift,
                        onAdjustHours = onAdjustHours,
                        onOpenCycleGenerator = { anchor ->
                            cycleAnchorDate = anchor
                            showCycleDialog = true
                        }
                    )

                    Tab.STATS -> StatsScreen(shifts = shifts)

                    Tab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onUpdateSettings = onUpdateSettings,
                        onApplyRateToFutureShifts = onApplyRateToFutureShifts,
                        onRefreshWidgetNow = onRefreshWidgetNow,
                        onRequestNotificationPermission = onRequestNotificationPermission
                    )
                }
            }

            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 0.dp
            ) {
                val icons = listOf(
                    Icons.Rounded.SpaceDashboard,
                    Icons.Rounded.CalendarMonth,
                    Icons.Rounded.QueryStats,
                    Icons.Rounded.Tune
                )
                Tab.values().forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icons[index], contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.secondary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    }

    if (showEditor) {
        ShiftEditorDialog(
            editingShift = editingShift,
            initialDate = editorInitialDate,
            defaultHourlyRate = settings.hourlyRate,
            onDismiss = {
                showEditor = false
                editingShift = null
            },
            onSave = { shift ->
                onSaveShift(shift)
                showEditor = false
                editingShift = null
            },
            onDelete = { id ->
                onDeleteShift(id)
                showEditor = false
                editingShift = null
            }
        )
    }

    if (showCycleDialog) {
        CycleGeneratorDialog(
            initialAnchorDate = cycleAnchorDate,
            defaultHourlyRate = settings.hourlyRate,
            existingShifts = shifts,
            onDismiss = { showCycleDialog = false },
            onGenerate = { request ->
                onGenerateCycle(request)
                showCycleDialog = false
            }
        )
    }
}
