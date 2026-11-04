package com.quarenta.calendario.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One occurrence of an event, read from any calendar account on the phone. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    val color: Int,
    val calendarId: Long,
    val calendarName: String,
    val accountName: String,
    val canEdit: Boolean,
    /** Calendars this event was found in, after duplicates are merged. */
    val sources: List<String> = listOf(calendarName),
    /** Ids of the other copies merged into this one. */
    val duplicateIds: List<Long> = emptyList(),
)

data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int, val isPrimary: Boolean)

/** What the person fills in (or what an "Add to calendar" button sends) for a new or edited event. */
data class EventDraft(
    val title: String = "",
    val start: Instant = Instant.now(),
    val end: Instant = Instant.now().plusSeconds(3600),
    val allDay: Boolean = false,
    val location: String = "",
    val description: String = "",
    val calendarId: Long? = null,
    val reminderMinutes: Int? = 30,
)

/**
 * Reads and writes events in Android's calendar store. This covers every account the
 * person already has on the phone (Google, Outlook/Exchange, Samsung, CalDAV/iCloud apps),
 * so the agenda shows all of them and new events sync back to those accounts.
 */
class CalendarRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun eventsBetween(from: LocalDate, toExclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> {
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.OWNER_ACCOUNT,
            CalendarContract.Instances.CALENDAR_ACCESS_LEVEL,
        )

        val events = mutableListOf<CalendarEvent>()
        resolver.query(
            uri, projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(4) == 1
                events += CalendarEvent(
                    id = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(Sem título)",
                    start = toLocal(c.getLong(2), allDay, zone),
                    end = toLocal(c.getLong(3), allDay, zone),
                    allDay = allDay,
                    location = c.getString(5)?.takeIf { it.isNotBlank() },
                    description = c.getString(6)?.takeIf { it.isNotBlank() },
                    color = c.getInt(7),
                    calendarId = c.getLong(8),
                    calendarName = c.getString(9) ?: "",
                    accountName = c.getString(10) ?: "",
                    canEdit = c.getInt(11) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }
        return events.sortedBy { it.start }
    }

    /** One event by id (used when another app asks to show an event). */
    fun eventById(id: Long, zone: ZoneId = ZoneId.systemDefault()): CalendarEvent? {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.OWNER_ACCOUNT,
            CalendarContract.Events.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Events.DURATION,
        )
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return resolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val allDay = c.getInt(4) == 1
            val startMs = c.getLong(2)
            val endMs = if (c.isNull(3)) startMs + 3_600_000 else c.getLong(3)
            CalendarEvent(
                id = c.getLong(0),
                title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(Sem título)",
                start = toLocal(startMs, allDay, zone),
                end = toLocal(endMs, allDay, zone),
                allDay = allDay,
                location = c.getString(5)?.takeIf { it.isNotBlank() },
                description = c.getString(6)?.takeIf { it.isNotBlank() },
                color = c.getInt(7),
                calendarId = c.getLong(8),
                calendarName = c.getString(9) ?: "",
                accountName = c.getString(10) ?: "",
                canEdit = c.getInt(11) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
            )
        }
    }

    /** Calendars the person can add events to, primary first. */
    fun writableCalendars(): List<CalendarInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val out = mutableListOf<CalendarInfo>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "",
                    account = c.getString(2) ?: "",
                    color = c.getInt(3),
                    isPrimary = c.getInt(4) == 1,
                )
            }
        }
        return out.sortedWith(compareByDescending<CalendarInfo> { it.isPrimary }.thenBy { it.name })
    }

    /** Saves a new event; returns its id, or null if it could not be saved. */
    fun insert(draft: EventDraft, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val calendarId = draft.calendarId ?: writableCalendars().firstOrNull()?.id ?: return null
        val values = draft.toValues(zone).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        draft.reminderMinutes?.let { minutes ->
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, id)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            })
        }
        return id
    }

    enum class UpdateResult { Saved, ReadOnly, Failed }

    /**
     * Saves changes to an existing event.
     *  - Repeating events (yearly anniversaries, weekly meetings...) are stored with a length
     *    (DURATION) instead of an end, and the calendar store refuses them with an end; the
     *    whole series moves by as much as this occurrence was moved ([originalStart] -> new start).
     *  - Moving to another calendar is a copy into the new one and a delete from the old one,
     *    which every calendar account accepts.
     */
    fun update(eventId: Long, draft: EventDraft, originalStart: Instant? = null, zone: ZoneId = ZoneId.systemDefault()): UpdateResult {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val current = resolver.query(
            uri,
            arrayOf(
                CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.RRULE, CalendarContract.Events.DTSTART,
                CalendarContract.Events.CALENDAR_ACCESS_LEVEL,
            ),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) Current(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3)) else null }
            ?: return UpdateResult.Failed
        if (current.accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) return UpdateResult.ReadOnly

        val values = draft.toValues(zone)
        if (!current.rrule.isNullOrBlank()) {
            val start = values.getAsLong(CalendarContract.Events.DTSTART)
            val end = values.getAsLong(CalendarContract.Events.DTEND)
            values.remove(CalendarContract.Events.DTEND)
            values.putNull(CalendarContract.Events.DTEND)
            values.put(CalendarContract.Events.DURATION, EventRules.duration(end - start, draft.allDay))
            val shift = originalStart?.let { draft.start.toEpochMilli() - it.toEpochMilli() } ?: 0L
            values.put(CalendarContract.Events.DTSTART, EventRules.seriesStart(current.dtStart, shift, draft.allDay))
            values.put(CalendarContract.Events.RRULE, current.rrule)
        }

        val target = draft.calendarId
        if (target != null && target != current.calendarId) {
            values.put(CalendarContract.Events.CALENDAR_ID, target)
            val copy = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return UpdateResult.Failed
            copyReminders(eventId, ContentUris.parseId(copy))
            resolver.delete(uri, null, null)
            return UpdateResult.Saved
        }
        return if (resolver.update(uri, values, null, null) > 0) UpdateResult.Saved else UpdateResult.Failed
    }

    private data class Current(val calendarId: Long, val rrule: String?, val dtStart: Long, val accessLevel: Int)

    private fun copyReminders(from: Long, to: Long) {
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
            "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(from.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, to)
                    put(CalendarContract.Reminders.MINUTES, c.getInt(0))
                    put(CalendarContract.Reminders.METHOD, c.getInt(1))
                })
            }
        }
    }

    fun delete(eventId: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return resolver.delete(uri, null, null) > 0
    }

    private fun EventDraft.toValues(zone: ZoneId) = ContentValues().apply {
        put(CalendarContract.Events.TITLE, title.ifBlank { "(Sem título)" })
        put(CalendarContract.Events.EVENT_LOCATION, location)
        put(CalendarContract.Events.DESCRIPTION, description)
        if (allDay) {
            // The calendar store wants all-day events at UTC midnight.
            val startDay = start.atZone(zone).toLocalDate()
            val endDay = maxOf(end.atZone(zone).toLocalDate(), startDay.plusDays(1))
            put(CalendarContract.Events.DTSTART, startDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, endDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        } else {
            put(CalendarContract.Events.DTSTART, start.toEpochMilli())
            put(CalendarContract.Events.DTEND, maxOf(end, start.plusSeconds(60)).toEpochMilli())
            put(CalendarContract.Events.ALL_DAY, 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
    }

    /** All-day events are stored at UTC midnight; shift them to local days. */
    private fun toLocal(ms: Long, allDay: Boolean, zone: ZoneId): Instant =
        if (!allDay) Instant.ofEpochMilli(ms)
        else Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant()
}
