package com.quarenta.calendario.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reminders for every calendar on the phone, shown by this app.
 * It reads the reminders the person already set on each event (Google, Outlook, Samsung...),
 * and keeps one alarm set for the next one. After it rings, the following one is set.
 */
object ReminderScheduler {

    private const val PREFS = "forty"
    private const val KEY_ENABLED = "reminders_on"
    private const val KEY_LAST = "reminders_last_handled"
    private const val KEY_COLOR = "reminder_color"
    private const val LOOKAHEAD_MS = 3L * 24 * 3600_000

    const val ACTION_ALARM = "com.quarenta.calendario.REMINDER_ALARM"
    const val ACTION_SNOOZED = "com.quarenta.calendario.REMINDER_SNOOZED"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        reschedule(context)
    }

    /** The colour the person chose for the reminder screen (ARGB). */
    fun color(context: Context): Int = prefs(context).getInt(KEY_COLOR, ReminderColors.default)

    fun setColor(context: Context, argb: Int) = prefs(context).edit().putInt(KEY_COLOR, argb).apply()

    /** Shows what is due now and sets the alarm for the next reminder. Safe to call any time. */
    fun reschedule(context: Context) {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        if (!isEnabled(context) || !canReadCalendar(context)) {
            alarms.cancel(pending)
            return
        }
        // First run: start from now, so old reminders are not shown all at once.
        val last = p.getLong(KEY_LAST, 0L).takeIf { it > 0 } ?: now.also { p.edit().putLong(KEY_LAST, it).apply() }
        val alerts = runCatching { alertsBetween(context, last - ReminderPlan.GRACE_MS, now + LOOKAHEAD_MS) }.getOrDefault(emptyList())

        val due = ReminderPlan.due(alerts, last, now)
        if (due.isNotEmpty()) {
            due.forEach { ReminderNotifier.show(context, it) }
        }
        val handled = maxOf(last, now)
        p.edit().putLong(KEY_LAST, handled).apply()

        val next = ReminderPlan.next(alerts, handled)
        if (next == null) alarms.cancel(pending) else setExact(context, next, pending)
    }

    /** "Adiar": shows the same reminder again in [minutes]. */
    fun snooze(context: Context, alert: ReminderAlert, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZED)
        ReminderIntents.put(intent, alert.copy(alertAtMs = at))
        val pending = PendingIntent.getBroadcast(
            context, ReminderNotifier.notificationId(alert), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExact(context, at, pending)
    }

    /** For the "Experimentar" button: shows [alert] in [seconds], so the person can lock the phone first. */
    fun test(context: Context, alert: ReminderAlert, seconds: Int) {
        val at = System.currentTimeMillis() + seconds * 1000L
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZED)
        ReminderIntents.put(intent, alert.copy(alertAtMs = at))
        val pending = PendingIntent.getBroadcast(
            context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExact(context, at, pending)
    }

    fun canUseExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun setExact(context: Context, at: Long, pending: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (canUseExactAlarms(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            // Without the exact-alarm permission it may ring a few minutes late, but it still rings.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canReadCalendar(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Every alert time between [fromMs] and [toMs], for events whose occurrence is still to come or ongoing. */
    fun alertsBetween(context: Context, fromMs: Long, toMs: Long): List<ReminderAlert> {
        val resolver = context.contentResolver
        val zone = ZoneId.systemDefault()
        // An alert can be up to a few weeks before its event; look at events up to 4 weeks after the window.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMs)
            ContentUris.appendId(it, toMs + 28L * 24 * 3600_000)
        }.build()
        data class Occ(val eventId: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val location: String?, val color: Int)
        val occurrences = mutableListOf<Occ>()
        resolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.HAS_ALARM,
            ),
            "${CalendarContract.Instances.VISIBLE} = 1", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(7) != 1) continue
                val allDay = c.getInt(4) == 1
                occurrences += Occ(
                    eventId = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(Sem título)",
                    begin = localMs(c.getLong(2), allDay, zone),
                    end = localMs(c.getLong(3), allDay, zone),
                    allDay = allDay,
                    location = c.getString(5)?.takeIf { it.isNotBlank() },
                    color = c.getInt(6),
                )
            }
        }
        if (occurrences.isEmpty()) return emptyList()

        val minutesByEvent = mutableMapOf<Long, MutableSet<Int>>()
        occurrences.map { it.eventId }.distinct().chunked(400).forEach { ids ->
            resolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
                "${CalendarContract.Reminders.EVENT_ID} IN (${ids.joinToString(",")})", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val method = c.getInt(2)
                    if (method != CalendarContract.Reminders.METHOD_ALERT && method != CalendarContract.Reminders.METHOD_DEFAULT) continue
                    // -1 means "the calendar's default"; use 10 minutes like most calendars do.
                    val minutes = c.getInt(1).let { if (it < 0) 10 else it }
                    minutesByEvent.getOrPut(c.getLong(0)) { mutableSetOf() } += minutes
                }
            }
        }
        return occurrences.flatMap { o ->
            minutesByEvent[o.eventId].orEmpty().map { m ->
                ReminderAlert(o.eventId, o.title, o.begin, o.end, o.allDay, o.location, o.color, o.begin - m * 60_000L)
            }
        }.filter { it.alertAtMs in fromMs..toMs }
    }

    /** All-day events are stored at UTC midnight; their reminders count from local midnight. */
    private fun localMs(ms: Long, allDay: Boolean, zone: ZoneId): Long =
        if (!allDay) ms
        else Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Twelve colours for the reminder screen: strong enough to wake you, pretty enough to look at. */
object ReminderColors {
    val all: List<Pair<String, Int>> = listOf(
        "Coral" to 0xFFFF6B6B.toInt(),
        "Laranja" to 0xFFFF9F43.toInt(),
        "Sol" to 0xFFF7B731.toInt(),
        "Lima" to 0xFF7BC950.toInt(),
        "Menta" to 0xFF26C6A0.toInt(),
        "Água" to 0xFF22B8CF.toInt(),
        "Azul" to 0xFF3B82F6.toInt(),
        "Índigo" to 0xFF5C6AE0.toInt(),
        "Violeta" to 0xFF9B5DE5.toInt(),
        "Rosa" to 0xFFF15BB5.toInt(),
        "Ameixa" to 0xFF8E3B5F.toInt(),
        "Grafite" to 0xFF2F3542.toInt(),
    )
    val default: Int = all[6].second
}

/** Packs a reminder into an Intent and back, for alarms, notification buttons and the reminder screen. */
object ReminderIntents {
    fun put(intent: Intent, a: ReminderAlert): Intent = intent
        .putExtra("r_event", a.eventId).putExtra("r_title", a.title)
        .putExtra("r_begin", a.beginMs).putExtra("r_end", a.endMs)
        .putExtra("r_allday", a.allDay).putExtra("r_location", a.location)
        .putExtra("r_color", a.color).putExtra("r_at", a.alertAtMs)

    fun get(intent: Intent?): ReminderAlert? {
        if (intent == null || !intent.hasExtra("r_event")) return null
        return ReminderAlert(
            eventId = intent.getLongExtra("r_event", 0),
            title = intent.getStringExtra("r_title") ?: "",
            beginMs = intent.getLongExtra("r_begin", 0),
            endMs = intent.getLongExtra("r_end", 0),
            allDay = intent.getBooleanExtra("r_allday", false),
            location = intent.getStringExtra("r_location"),
            color = intent.getIntExtra("r_color", 0),
            alertAtMs = intent.getLongExtra("r_at", 0),
        )
    }
}
