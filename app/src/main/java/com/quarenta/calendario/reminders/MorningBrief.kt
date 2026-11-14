package com.quarenta.calendario.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quarenta.calendario.MainActivity
import com.quarenta.calendario.R
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.data.CalendarRepository
import com.quarenta.calendario.data.ContactsRepository
import com.quarenta.calendario.data.Dedup
import com.quarenta.calendario.ui.agenda.Highlights
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Every morning at the hour the person chose, one notification with the day ahead:
 * how many appointments, the first one, and today's and tomorrow's birthdays.
 */
object MorningBrief {

    private const val PREFS = "forty"
    private const val KEY_ON = "morning_on"
    private const val KEY_HOUR = "morning_hour"
    private const val CHANNEL = "resumo"
    private const val NOTIFICATION_ID = 8080
    const val ACTION = "com.quarenta.calendario.MORNING_BRIEF"
    val hours = listOf(6, 7, 8, 9, 10)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ON, true)
    fun hour(context: Context) = prefs(context).getInt(KEY_HOUR, 8)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        schedule(context)
    }

    fun setHour(context: Context, hour: Int) {
        prefs(context).edit().putInt(KEY_HOUR, hour.coerceIn(0, 23)).apply()
        schedule(context)
    }

    /** Sets the alarm for the next morning (today, if the hour has not passed yet). Safe to call any time. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        if (!isEnabled(context)) {
            alarms.cancel(pending)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        var at = now.toLocalDate().atTime(LocalTime.of(hour(context), 0)).atZone(zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val ms = at.toInstant().toEpochMilli()
        if (ReminderScheduler.canUseExactAlarms(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pending)
        }
    }

    /** Builds and posts the summary now. Also used by the "Experimentar" button. */
    fun post(context: Context) {
        if (!canPost(context)) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val events = if (canRead(context)) {
            runCatching { Dedup.merge(CalendarRepository(context).eventsBetween(today, today.plusDays(2), zone), zone) }
                .getOrDefault(emptyList())
        } else emptyList()
        val dayStart = today.atStartOfDay(zone).toInstant()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant()
        val todays = events.filter { it.start < tomorrowStart && it.end > dayStart }
        val tomorrows = events.filter { it.start >= tomorrowStart }
        fun CalendarEvent.isBirthday() = Highlights.isBirthday(title, sources)

        val appointments = todays.filterNot { it.isBirthday() }
        val text = MorningText.build(
            today = appointments.map {
                MorningText.Item(
                    it.title, it.allDay,
                    if (it.allDay || it.start < dayStart) null else it.start.atZone(zone).toLocalTime(),
                )
            },
            birthdaysToday = todays.filter { it.isBirthday() }.map { it.title },
            birthdaysTomorrow = tomorrows.filter { it.isBirthday() }.map { it.title },
        )

        // The photo of whoever the first appointment (or today's birthday) is with.
        val first = appointments.filter { !it.allDay }.minByOrNull { it.start }
            ?: todays.firstOrNull { it.isBirthday() }
        val photo = first?.let { e -> runCatching { personPhoto(context, e.title) }.getOrNull() }

        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (photo != null) {
            builder.setLargeIcon(photo)
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(photo).bigLargeIcon(null as android.graphics.Bitmap?).setSummaryText(text.body))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
        }
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    private fun personPhoto(context: Context, title: String): android.graphics.Bitmap? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val repo = ContactsRepository(context)
        val people = repo.all()
        val id = PersonMatch.inTitle(title, people.map { it.id to it.name }) ?: return null
        val person = people.firstOrNull { it.id == id } ?: return null
        return repo.photo(person, maxPx = 1024)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Resumo da manhã", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "O dia que tens pela frente, todas as manhãs"
            },
        )
    }

    private fun canPost(context: Context) = ReminderNotifier.canPost(context)

    private fun canRead(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 80, Intent(context, MorningReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Our own morning alarm: posts the summary and sets tomorrow's. */
class MorningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MorningBrief.ACTION) return
        val pending = goAsync()
        Thread {
            try {
                val app = context.applicationContext
                runCatching { MorningBrief.post(app) }
                runCatching { MorningBrief.schedule(app) }
            } finally { pending.finish() }
        }.start()
    }
}
