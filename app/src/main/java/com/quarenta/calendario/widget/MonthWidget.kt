package com.quarenta.calendario.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.quarenta.calendario.MainActivity
import com.quarenta.calendario.R
import com.quarenta.calendario.data.CalendarRepository
import com.quarenta.calendario.data.Dedup
import com.quarenta.calendario.ui.agenda.MonthMath
import com.quarenta.calendario.ui.theme.Themes
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Home-screen widget: this month at a glance, today ringed in gold and a dot on every busy day. */
class MonthWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread { try { render(context.applicationContext, manager, ids) } finally { pending.finish() } }.start()
    }

    companion object {
        private const val W = 1080
        private const val H = 960

        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, MonthWidget::class.java))
            if (ids.isEmpty()) return
            Thread { runCatching { render(app, manager, ids) } }.start()
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val today = LocalDate.now()
            val month = YearMonth.from(today)
            val days = MonthMath.days(month)
            val colours: Map<LocalDate, List<Int>> =
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                    runCatching {
                        val zone = ZoneId.systemDefault()
                        Dedup.merge(CalendarRepository(context).eventsBetween(days.first(), days.last().plusDays(1)))
                            .flatMap { e ->
                                val from = e.start.atZone(zone).toLocalDate()
                                val to = e.end.minusMillis(1).atZone(zone).toLocalDate().let { if (it.isBefore(from)) from else it }
                                generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { it to e.color }.toList()
                            }
                            .groupBy({ it.first }, { it.second })
                    }.getOrDefault(emptyMap())
                } else emptyMap()
            val luxe = Themes.load(context.getSharedPreferences("forty", Context.MODE_PRIVATE)) == Themes.Gold.id
            val picture = draw(month, days, today, colours, accent = if (luxe) 0xFFD4AF37.toInt() else 0xFF0A84FF.toInt())

            val views = RemoteViews(context.packageName, R.layout.widget_month)
            views.setImageViewBitmap(R.id.month_picture, picture)
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TAB, "agenda")
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 8200, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun draw(month: YearMonth, days: List<LocalDate>, today: LocalDate, colours: Map<LocalDate, List<Int>>, accent: Int): Bitmap {
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
            // Title.
            text.apply { color = accent; textSize = 64f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.LEFT }
            c.drawText(WidgetPicks.monthTitle(month), 40f, 90f, text)
            // Weekday letters.
            text.apply { textAlign = Paint.Align.CENTER; textSize = 34f; color = 0x99FFFFFF.toInt(); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
            val cellW = (W - 40f) / 7
            val top = 150f
            listOf("S", "T", "Q", "Q", "S", "S", "D").forEachIndexed { i, l -> c.drawText(l, 20f + cellW * (i + 0.5f), top, text) }
            val cellH = (H - top - 30f) / 6
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            days.forEachIndexed { i, d ->
                val cx = 20f + cellW * (i % 7 + 0.5f)
                val cy = top + 20f + cellH * (i / 7) + cellH * 0.42f
                val inMonth = YearMonth.from(d) == month
                if (d == today) c.drawCircle(cx, cy - 14f, 50f, ring)
                text.apply {
                    textSize = 46f
                    typeface = Typeface.create("sans-serif", if (d == today) Typeface.BOLD else Typeface.NORMAL)
                    color = when {
                        d == today -> Color.BLACK
                        !inMonth -> 0x40FFFFFF
                        i % 7 >= 5 -> 0xB3FFFFFF.toInt()
                        else -> Color.WHITE
                    }
                }
                c.drawText("${d.dayOfMonth}", cx, cy, text)
                colours[d].orEmpty().distinct().take(3).let { list ->
                    list.forEachIndexed { k, col ->
                        dot.color = if (inMonth) col or 0xFF000000.toInt() else (col and 0x00FFFFFF) or 0x60000000
                        c.drawCircle(cx + (k - (list.size - 1) / 2f) * 20f, cy + 46f, 7f, dot)
                    }
                }
            }
            return bmp
        }
    }
}
