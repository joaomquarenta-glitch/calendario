package com.quarenta.calendario.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.CalendarContract
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.quarenta.calendario.MainActivity
import com.quarenta.calendario.R
import com.quarenta.calendario.data.CalendarRepository
import com.quarenta.calendario.data.ContactsRepository
import com.quarenta.calendario.data.Dedup
import com.quarenta.calendario.reminders.PersonMatch
import com.quarenta.calendario.ui.agenda.Highlights
import com.quarenta.calendario.ui.agenda.withoutName
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Home-screen widget: the next appointment that is with somebody, their photo filling the card
 * and the plan underneath ("Com Ana · Jantar · Sáb 20:00"). Tapping it opens that appointment.
 */
class PersonWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread { try { render(context.applicationContext, manager, ids) } finally { pending.finish() } }.start()
    }

    companion object {
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, PersonWidget::class.java))
            if (ids.isEmpty()) return
            Thread { runCatching { render(app, manager, ids) } }.start()
        }

        private fun granted(context: Context, p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val now = System.currentTimeMillis()
            val found = if (granted(context, Manifest.permission.READ_CALENDAR) && granted(context, Manifest.permission.READ_CONTACTS)) {
                runCatching {
                    val events = Dedup.merge(CalendarRepository(context).eventsBetween(today, today.plusDays(90)))
                    val upcoming = Highlights.upcoming(events, now, endMs = { it.end.toEpochMilli() }, startMs = { it.start.toEpochMilli() }, max = 40)
                    val repo = ContactsRepository(context)
                    val people = repo.all()
                    val pairs = people.map { it.id to it.name }
                    WidgetPicks.firstWithPerson(upcoming) { e ->
                        PersonMatch.inTitle(e.title, pairs)?.let { id -> people.firstOrNull { it.id == id } }
                    }?.let { (event, person) -> Triple(event, person, repo.photo(person, maxPx = 1800)?.let(::fitted)) }
                }.getOrNull()
            } else null

            fun build(layout: Int): RemoteViews {
                val views = RemoteViews(context.packageName, layout)
                if (found == null) {
                    views.setImageViewBitmap(R.id.widget_photo, NextWidgetArt.gradient(0xFFD4AF37.toInt()))
                    views.setTextViewText(R.id.widget_headline, "Sem planos com ninguém")
                    views.setViewVisibility(R.id.widget_subline, View.GONE)
                } else {
                    val (event, person, photo) = found
                    val start = event.start.atZone(zone)
                    val time = if (event.allDay) "" else " · " + start.format(DateTimeFormatter.ofPattern("HH:mm"))
                    views.setImageViewBitmap(R.id.widget_photo, photo ?: NextWidgetArt.gradient(event.color))
                    views.setTextViewText(R.id.widget_headline, withoutName(event.title, person).ifBlank { event.title })
                    views.setTextViewText(
                        R.id.widget_subline,
                        (Highlights.shortDay(start.toLocalDate(), today) + time).replaceFirstChar { it.uppercase() },
                    )
                    views.setViewVisibility(R.id.widget_subline, View.VISIBLE)
                }
                if (layout == R.layout.widget_next) {
                    views.setTextViewText(R.id.widget_label, found?.second?.name?.let { "COM ${it.uppercase()}" } ?: "COM QUEM")
                }
                val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                found?.first?.let {
                    intent.action = Intent.ACTION_VIEW
                    intent.data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it.id)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, 8300, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
                return views
            }

            val views = if (Build.VERSION.SDK_INT >= 31) {
                RemoteViews(mapOf(SizeF(100f, 100f) to build(R.layout.widget_next_small), SizeF(200f, 100f) to build(R.layout.widget_next)))
            } else build(R.layout.widget_next)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun fitted(bitmap: Bitmap): Bitmap {
            val longest = max(bitmap.width, bitmap.height)
            if (longest <= 900) return bitmap
            val scale = 900f / longest
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
        }
    }
}
