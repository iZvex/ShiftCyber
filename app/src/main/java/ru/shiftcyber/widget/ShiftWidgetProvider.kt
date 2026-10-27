package ru.shiftcyber.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import ru.shiftcyber.R

class ShiftWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        val prefs = context.getSharedPreferences(
            "shift_widget",
            Context.MODE_PRIVATE
        )

        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.shift_widget)

            views.setTextViewText(
                R.id.widget_status,
                prefs.getString("status", "Твой график под контролем")
            )
            views.setTextViewText(
                R.id.widget_details,
                prefs.getString("details", "Открой приложение, чтобы добавить смены")
            )
            views.setTextViewText(
                R.id.widget_hours,
                prefs.getString("hours", "—")
            )
            views.setTextViewText(
                R.id.widget_earnings,
                prefs.getString("earnings", "— ₽")
            )
            views.setTextViewText(
                R.id.widget_next,
                prefs.getString("next", "Следующая смена: —")
            )

            manager.updateAppWidget(id, views)
        }
    }
}
