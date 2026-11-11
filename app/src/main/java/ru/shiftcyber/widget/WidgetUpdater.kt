package ru.shiftcyber.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.shiftcyber.MainActivity
import ru.shiftcyber.R
import ru.shiftcyber.data.AppSettings
import ru.shiftcyber.data.ShiftDatabase
import ru.shiftcyber.data.ShiftEntity
import ru.shiftcyber.domain.ShiftMath
import java.time.ZoneId

object WidgetUpdater {
    const val PREFS_NAME = "shift_widget"
    private const val PREF_PREVIEW_PUBLISHED = "preview_published_v2"

    data class WidgetState(
        val status: String,
        val details: String,
        val hours: String,
        val earnings: String,
        val next: String,
        val transparency: Int = 88
    )

    fun buildWidgetState(
        shifts: List<ShiftEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        transparency: Int = 88,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): WidgetState {
        val sorted = shifts.sortedBy { it.startMillis }
        val active = sorted.firstOrNull { it.startMillis <= nowMillis && it.endMillis > nowMillis }
        val primary = active ?: sorted.firstOrNull { it.startMillis > nowMillis }
        val following = if (primary != null) {
            sorted.firstOrNull {
                it.id != primary.id && it.startMillis >= primary.endMillis
            } ?: sorted.firstOrNull {
                it.id != primary.id && it.startMillis > primary.startMillis
            }
        } else {
            null
        }

        val status = when {
            active != null -> "Сейчас: ${active.title}"
            primary != null -> primary.title
            else -> "Нет ближайших смен"
        }

        val details = when {
            active != null ->
                "Идёт смена · ${ShiftMath.timeRangeLabel(active, zoneId)}"
            primary != null ->
                "${ShiftMath.dateLabel(primary.startMillis, zoneId)} · ${ShiftMath.timeRangeLabel(primary, zoneId)}"
            else -> "Нажми, чтобы добавить смену в Shift Cyber"
        }

        val hours = when {
            primary == null -> "—"
            primary.isDaily -> "1 день"
            else -> ShiftMath.formatHours(ShiftMath.hours(primary))
        }
        val earnings = primary?.let { ShiftMath.formatCurrency(ShiftMath.earnings(it)) } ?: "— ₽"

        val nextText = when {
            following != null ->
                "Далее: ${ShiftMath.dateLabel(following.startMillis, zoneId)} · ${ShiftMath.timeLabel(following.startMillis, zoneId)}"
            primary == null ->
                "Следующая смена: не запланирована"
            else ->
                "Других будущих смен пока нет"
        }

        return WidgetState(
            status = status,
            details = details,
            hours = hours,
            earnings = earnings,
            next = nextText,
            transparency = transparency.coerceIn(40, 100)
        )
    }

    suspend fun refreshFromDatabase(
        context: Context,
        targetFullIds: IntArray? = null,
        targetCompactIds: IntArray? = null
    ) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val db = ShiftDatabase.get(appContext)
        val shifts = runCatching { db.shifts().getAll() }.getOrDefault(emptyList())
        val settings = runCatching { db.settings().getOnce() }.getOrNull()
        val state = buildWidgetState(
            shifts = shifts,
            nowMillis = System.currentTimeMillis(),
            transparency = settings?.widgetTransparency ?: 88
        )
        saveToPrefs(appContext, state)
        pushToWidgets(appContext, state, targetFullIds, targetCompactIds)
        publishCatalogPreviewsIfSupported(appContext)
    }

    fun refreshWithSnapshot(
        context: Context,
        shifts: List<ShiftEntity>,
        settings: AppSettings?
    ) {
        val appContext = context.applicationContext
        val state = buildWidgetState(
            shifts = shifts,
            nowMillis = System.currentTimeMillis(),
            transparency = settings?.widgetTransparency ?: 88
        )
        saveToPrefs(appContext, state)
        pushToWidgets(appContext, state, null, null)
        publishCatalogPreviewsIfSupported(appContext)
    }

    fun renderCached(
        context: Context,
        targetFullIds: IntArray? = null,
        targetCompactIds: IntArray? = null
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val state = WidgetState(
            status = prefs.getString("status", "Нет ближайших смен") ?: "Нет ближайших смен",
            details = prefs.getString("details", "Нажми, чтобы открыть Shift Cyber")
                ?: "Нажми, чтобы открыть Shift Cyber",
            hours = prefs.getString("hours", "—") ?: "—",
            earnings = prefs.getString("earnings", "— ₽") ?: "— ₽",
            next = prefs.getString("next", "Следующая смена: не запланирована")
                ?: "Следующая смена: не запланирована",
            transparency = prefs.getInt("transparency", 88)
        )
        pushToWidgets(appContext, state, targetFullIds, targetCompactIds)
    }

    private fun publishCatalogPreviewsIfSupported(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_PREVIEW_PUBLISHED, false)) return

        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val fullPreview = RemoteViews(context.packageName, R.layout.shift_widget).apply {
                setTextViewText(R.id.widget_status, "Дневная смена")
                setTextViewText(R.id.widget_details, "Сегодня · 09:00–21:00")
                setTextViewText(R.id.widget_hours, "12 ч")
                setTextViewText(R.id.widget_earnings, "4 200 ₽")
                setTextViewText(R.id.widget_next, "Далее: завтра · 09:00")
            }
            val compactPreview = RemoteViews(context.packageName, R.layout.shift_widget_compact).apply {
                setTextViewText(R.id.widget_compact_status, "Дневная смена")
                setTextViewText(R.id.widget_compact_details, "Сегодня · 09:00–21:00")
                setTextViewText(R.id.widget_compact_earnings, "4 200 ₽")
            }
            val okFull = manager.setWidgetPreview(
                ComponentName(context, ShiftWidgetProvider::class.java),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                fullPreview
            )
            val okCompact = manager.setWidgetPreview(
                ComponentName(context, ShiftCompactWidgetProvider::class.java),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                compactPreview
            )
            if (okFull || okCompact) {
                prefs.edit().putBoolean(PREF_PREVIEW_PUBLISHED, true).apply()
            }
        }
    }

    private fun saveToPrefs(context: Context, state: WidgetState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("status", state.status)
            .putString("details", state.details)
            .putString("hours", state.hours)
            .putString("earnings", state.earnings)
            .putString("next", state.next)
            .putInt("transparency", state.transparency)
            .apply()
    }

    private fun pushToWidgets(
        context: Context,
        state: WidgetState,
        targetFullIds: IntArray?,
        targetCompactIds: IntArray?
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val bgRes = when {
            state.transparency >= 93 -> R.drawable.widget_bg_solid
            state.transparency <= 75 -> R.drawable.widget_bg_glass
            else -> R.drawable.widget_bg_translucent
        }
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullIds = targetFullIds?.takeIf { it.isNotEmpty() }
            ?: manager.getAppWidgetIds(ComponentName(context, ShiftWidgetProvider::class.java))
        fullIds.forEach { id ->
            runCatching {
                val views = RemoteViews(context.packageName, R.layout.shift_widget)
                views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
                views.setTextViewText(R.id.widget_status, state.status)
                views.setTextViewText(R.id.widget_details, state.details)
                views.setTextViewText(R.id.widget_hours, state.hours)
                views.setTextViewText(R.id.widget_earnings, state.earnings)
                views.setTextViewText(R.id.widget_next, state.next)

                val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
                val maxHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
                when {
                    maxHeightDp in 1..104 -> {
                        views.setViewVisibility(R.id.widget_divider, View.GONE)
                        views.setViewVisibility(R.id.widget_metrics_row, View.GONE)
                        views.setViewVisibility(R.id.widget_next, View.GONE)
                    }
                    maxHeightDp in 105..144 -> {
                        views.setViewVisibility(R.id.widget_divider, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_metrics_row, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_next, View.GONE)
                    }
                    else -> {
                        views.setViewVisibility(R.id.widget_divider, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_metrics_row, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_next, View.VISIBLE)
                    }
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    id,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                manager.updateAppWidget(id, views)
            }
        }

        val compactIds = targetCompactIds?.takeIf { it.isNotEmpty() }
            ?: manager.getAppWidgetIds(ComponentName(context, ShiftCompactWidgetProvider::class.java))
        compactIds.forEach { id ->
            runCatching {
                val views = RemoteViews(context.packageName, R.layout.shift_widget_compact)
                views.setInt(R.id.widget_compact_root, "setBackgroundResource", bgRes)
                views.setTextViewText(R.id.widget_compact_status, state.status)
                views.setTextViewText(R.id.widget_compact_details, state.details)
                views.setTextViewText(R.id.widget_compact_earnings, state.earnings)

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    id + 100_000,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_compact_root, pendingIntent)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
