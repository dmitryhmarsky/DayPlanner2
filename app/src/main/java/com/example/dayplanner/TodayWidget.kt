package com.example.dayplanner

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val RU: Locale = Locale.forLanguageTag("ru-RU")

private fun String.cap(): String = replaceFirstChar { it.titlecase(RU) }

/** Виджет: число и день недели, заметка на сегодня и ближайшие события дня. */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, manager, ids)
    }

    // при изменении размера выбираем подходящую раскладку (обычная или компактная 2x2)
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        refresh(context, manager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(refreshIntent(context))
    }

    companion object {
        private const val ACTION_REFRESH = "com.example.dayplanner.WIDGET_REFRESH"
        private const val MAX_EVENTS = 3

        // порог (dp), ниже которого включается компактная раскладка: примерно меньше 3 клеток
        private const val COMPACT_W = 180
        private const val COMPACT_H = 150

        /** Вызывается из приложения после любого изменения данных. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            refresh(context, manager, ids)
        }

        private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return

            val today = LocalDate.now()
            val key = today.toString()
            val db = Db.get(context)
            val note = db.noteFor(key).trim()
            val events = db.eventsFor(key)

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val noteText = if (note.isEmpty()) context.getString(R.string.widget_no_note) else note
            val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, RU).cap()
            val monthName = today.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).cap()

            for (id in ids) {
                val opts = manager.getAppWidgetOptions(id)
                val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                // 0 — размер ещё неизвестен: считаем виджет обычным
                val compact = (w in 1 until COMPACT_W) || (h in 1 until COMPACT_H)

                val views = RemoteViews(
                    context.packageName,
                    if (compact) R.layout.widget_today_small else R.layout.widget_today
                )
                views.setTextViewText(R.id.wDay, today.dayOfMonth.toString())
                views.setTextViewText(R.id.wWeekday, weekday)
                views.setTextViewText(
                    R.id.wMonth,
                    if (compact) monthName else "$monthName ${today.year}"
                )
                views.setTextViewText(R.id.wNote, noteText)

                val eventsText = when {
                    events.isEmpty() -> context.getString(R.string.widget_no_events)
                    compact -> {
                        // в 2x2 места мало: ближайшее событие и сколько ещё
                        val first = events.first()
                        val head = (if (first.time.isEmpty()) "" else first.time + " ") + first.title
                        if (events.size > 1) head + "  +" + (events.size - 1) else head
                    }
                    else -> {
                        val lines = events.take(MAX_EVENTS).map {
                            (if (it.time.isEmpty()) "•" else it.time) + "  " + it.title
                        }.toMutableList()
                        if (events.size > MAX_EVENTS) {
                            lines.add(context.getString(R.string.widget_more, events.size - MAX_EVENTS))
                        }
                        lines.joinToString("\n")
                    }
                }
                views.setTextViewText(R.id.wEvents, eventsText)
                views.setOnClickPendingIntent(R.id.widgetRoot, open)

                manager.updateAppWidget(id, views)
            }
            scheduleMidnight(context)
        }

        /** Обновление сразу после полуночи, чтобы виджет перешёл на новый день. */
        private fun scheduleMidnight(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1000
            // неточный будильник: точное время не нужно, разрешения тоже
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, refreshIntent(context))
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, TodayWidget::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
