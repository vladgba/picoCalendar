package x.vladgba.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.io.File
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Month widget built from plain RemoteViews (TextViews in LinearLayouts), no collection adapters.
 * - tap a day: open the app on that date
 * - ‹ ›: flip the month shown in this widget (offset stored per widget id)
 * - title: back to the current month, or open the app if already there
 * Refreshes itself at local midnight, on time/zone/locale change and when notes are edited.
 */
class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, manager, id)
        scheduleMidnight(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHIFT -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val delta = intent.getIntExtra(EXTRA_DELTA, 0)
                val p = prefs(context)
                val off = if (delta == 0) 0 else p.getInt("off_$id", 0) + delta
                p.edit().putInt("off_$id", off).apply()
                render(context, AppWidgetManager.getInstance(context), id)
            }
            ACTION_MIDNIGHT,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> updateAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val e = prefs(context).edit()
        for (id in ids) e.remove("off_$id")
        e.apply()
    }

    override fun onDisabled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(midnightIntent(context))
    }

    companion object {
        private const val ACTION_SHIFT = "x.vladgba.calendar.WIDGET_SHIFT"
        private const val ACTION_MIDNIGHT = "x.vladgba.calendar.WIDGET_MIDNIGHT"
        private const val EXTRA_DELTA = "delta"

        private val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

        /** Re-render every placed widget (call after notes change). */
        fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context) ?: return
            val ids = m.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) render(context, m, id)
            scheduleMidnight(context)
        }

        private fun prefs(c: Context) = c.getSharedPreferences("widget", Context.MODE_PRIVATE)

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val pkg = context.packageName
            val store = EventStore(File(context.filesDir, MainActivity.NOTES_FILE))
            val offset = prefs(context).getInt("off_$id", 0)

            val now = Calendar.getInstance()
            val todayKey = EventStore.key(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
            val c = Calendar.getInstance().apply {
                clear()
                set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), 1)
                add(Calendar.MONTH, offset)
            }
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)

            val cText = context.color(R.color.text)
            val cDim = context.color(R.color.text_dim)
            val cWeekend = context.color(R.color.weekend)
            val cOnAccent = context.color(R.color.on_accent)

            val views = RemoteViews(pkg, R.layout.widget_month)
            views.setTextViewText(R.id.w_title, SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(c.time)
                .replaceFirstChar { it.titlecase(Locale.getDefault()) })
            views.setOnClickPendingIntent(R.id.w_prev, shiftIntent(context, id, -1))
            views.setOnClickPendingIntent(R.id.w_next, shiftIntent(context, id, 1))
            views.setOnClickPendingIntent(R.id.w_title,
                if (offset != 0) shiftIntent(context, id, 0) else openIntent(context, todayKey))

            views.removeAllViews(R.id.w_grid)

            // Weekday header
            val firstDow = Calendar.getInstance().firstDayOfWeek
            val names = DateFormatSymbols.getInstance().shortWeekdays
            val head = RemoteViews(pkg, R.layout.widget_head_row)
            for (col in 0 until 7) {
                val dow = (firstDow - 1 + col) % 7 + 1
                val cell = RemoteViews(pkg, R.layout.widget_head_cell)
                cell.setTextViewText(R.id.w_cell, names[dow].uppercase())
                cell.setTextColor(R.id.w_cell, if (isWeekend(dow)) cWeekend else cDim)
                head.addView(R.id.w_row, cell)
            }
            views.addView(R.id.w_grid, head)

            // Grid: always 6 rows so the widget doesn't jump in height between months.
            val offsetDays = (c.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
            c.add(Calendar.DAY_OF_MONTH, -offsetDays)
            for (row in 0 until 6) {
                val rv = RemoteViews(pkg, R.layout.widget_row)
                for (col in 0 until 7) {
                    val y = c.get(Calendar.YEAR)
                    val m = c.get(Calendar.MONTH)
                    val d = c.get(Calendar.DAY_OF_MONTH)
                    val key = EventStore.key(y, m, d)
                    val today = key == todayKey
                    val notes = store.has(key)

                    val cell = RemoteViews(pkg, R.layout.widget_cell)
                    cell.setTextViewText(R.id.w_cell, d.toString())
                    cell.setTextColor(R.id.w_cell, when {
                        today -> cOnAccent
                        m != month -> cDim
                        isWeekend(c.get(Calendar.DAY_OF_WEEK)) -> cWeekend
                        else -> cText
                    })
                    val bg = when {
                        today && notes -> R.drawable.cell_today_note
                        today -> R.drawable.cell_today
                        notes -> R.drawable.cell_note
                        else -> 0
                    }
                    if (bg != 0) cell.setInt(R.id.w_cell, "setBackgroundResource", bg)
                    cell.setOnClickPendingIntent(R.id.w_cell, openIntent(context, key))
                    rv.addView(R.id.w_row, cell)
                    c.add(Calendar.DAY_OF_MONTH, 1)
                }
                views.addView(R.id.w_grid, rv)
            }

            manager.updateAppWidget(id, views)
        }

        private fun isWeekend(dow: Int) = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY

        private fun shiftIntent(context: Context, id: Int, delta: Int): PendingIntent {
            val i = Intent(context, CalendarWidget::class.java)
                .setAction(ACTION_SHIFT)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_DELTA, delta)
            // request code must differ per (widget, delta) or the extras get merged
            return PendingIntent.getBroadcast(context, id * 4 + delta + 1, i, PI_FLAGS)
        }

        private fun openIntent(context: Context, key: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DATE, key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, key, i, PI_FLAGS)
        }

        private fun midnightIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(context, 0,
                Intent(context, CalendarWidget::class.java).setAction(ACTION_MIDNIGHT), PI_FLAGS)

        /** Inexact alarm a few seconds after the next local midnight; no exact-alarm permission needed. */
        private fun scheduleMidnight(context: Context) {
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
            }
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC, next.timeInMillis, midnightIntent(context))
        }
    }
}
