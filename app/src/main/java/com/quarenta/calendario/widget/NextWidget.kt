package com.quarenta.calendario.widget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.quarenta.calendario.MainActivity
import com.quarenta.calendario.R
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.data.CalendarRepository
import com.quarenta.calendario.data.ContactsRepository
import com.quarenta.calendario.data.Dedup
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.reminders.PersonMatch
import com.quarenta.calendario.ui.agenda.Highlights
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Home-screen widget: the next event as one big card, with the person's photo filling it
 * (as in the app's Início). Tapping it opens the event inside FortyCalendar.
 */
class NextWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try { render(context.applicationContext, manager, ids) } finally { pending.finish() }
        }.start()
    }

    companion object {
        private const val PHOTO_MAX_PX = 900

        /** Redraws every "Próximo" widget on the home screen. Safe to call from any thread. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, NextWidget::class.java))
            if (ids.isEmpty()) return
            Thread { runCatching { render(app, manager, ids) } }.start()
        }

        private fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val now = System.currentTimeMillis()
            val next: CalendarEvent? = if (granted(context, Manifest.permission.READ_CALENDAR)) {
                runCatching {
                    val events = Dedup.merge(CalendarRepository(context).eventsBetween(today, today.plusDays(60)))
                    Highlights.upcoming(events, now, endMs = { it.end.toEpochMilli() }, startMs = { it.start.toEpochMilli() }, max = 1).firstOrNull()
                }.getOrNull()
            } else null

            var person: Person? = null
            var photo: Bitmap? = null
            var birthYear: Int? = null
            val birthday = next != null && Highlights.isBirthday(next.title, next.sources)
            if (next != null && granted(context, Manifest.permission.READ_CONTACTS)) {
                runCatching {
                    val repo = ContactsRepository(context)
                    val people = repo.all()
                    person = PersonMatch.inTitle(next.title, people.map { it.id to it.name })?.let { id -> people.firstOrNull { it.id == id } }
                    person?.let { p ->
                        photo = repo.photo(p)?.let(::fitted)
                        if (birthday) birthYear = runCatching { repo.details(p).birthYear }.getOrNull()
                    }
                }
            }

            val views = RemoteViews(context.packageName, R.layout.widget_next)
            if (next == null) {
                views.setImageViewBitmap(R.id.widget_photo, gradient(Color.rgb(10, 132, 255)))
                views.setTextViewText(R.id.widget_headline, context.getString(R.string.widget_next_empty))
                views.setTextViewText(R.id.widget_subline, "")
                views.setViewVisibility(R.id.widget_subline, View.GONE)
            } else {
                val start = next.start.atZone(zone)
                val lines = NextWidgetText.lines(
                    title = next.title, birthday = birthday, birthYear = birthYear,
                    day = start.toLocalDate(), today = today,
                    time = if (next.allDay) null else start.format(DateTimeFormatter.ofPattern("HH:mm")),
                    person = person, hasPhoto = photo != null,
                )
                views.setImageViewBitmap(R.id.widget_photo, photo ?: gradient(next.color))
                views.setTextViewText(R.id.widget_headline, lines.headline)
                views.setTextViewText(R.id.widget_subline, lines.subline)
                views.setViewVisibility(R.id.widget_subline, if (lines.subline.isEmpty()) View.GONE else View.VISIBLE)
            }
            views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, next))
            ids.forEach { manager.updateAppWidget(it, views) }
            scheduleNext(context, next, now)
        }

        /** Opens the event in the app; with nothing coming up, opens the app. */
        private fun openIntent(context: Context, event: CalendarEvent?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (event != null) {
                intent.action = Intent.ACTION_VIEW
                intent.data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
            }
            return PendingIntent.getActivity(context, 7001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** Wakes the widget again when the current event ends, so it moves on to the next one. */
        private fun scheduleNext(context: Context, event: CalendarEvent?, now: Long) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, NextWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NextWidget::class.java)),
                )
            val pi = PendingIntent.getBroadcast(context, 7002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val at = listOfNotNull(event?.end?.toEpochMilli(), midnight).filter { it > now }.minOrNull() ?: midnight
            // Not exact on purpose: a minute late is fine and saves battery.
            alarms.set(AlarmManager.RTC, at + 30_000, pi)
        }

        /** Keeps the photo sharp but within what a home-screen widget may carry. */
        private fun fitted(bitmap: Bitmap): Bitmap {
            val longest = max(bitmap.width, bitmap.height)
            if (longest <= PHOTO_MAX_PX) return bitmap
            val scale = PHOTO_MAX_PX.toFloat() / longest
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
        }

        /** No photo: a rich gradient in the event's colour, like the cards in the app. */
        private fun gradient(color: Int): Bitmap {
            val size = 400
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(ColorUtils.blendARGB(color, Color.WHITE, 0.25f), color, ColorUtils.blendARGB(color, Color.BLACK, 0.45f)),
                    null, Shader.TileMode.CLAMP,
                )
            }
            Canvas(bmp).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            return bmp
        }
    }
}
