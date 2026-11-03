package com.quarenta.calendario.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    val calendarName: String,
    val accountName: String,
)

/**
 * Reads events from Android's calendar store. This covers every account the
 * person already has on the phone (Google, Outlook/Exchange, Samsung, and any
 * CalDAV/iCloud sync app), so the app shows all of them in one agenda.
 */
class CalendarRepository(private val context: Context) {

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
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.OWNER_ACCOUNT,
        )

        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            uri, projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(4) == 1
                var startMs = c.getLong(2)
                var endMs = c.getLong(3)
                if (allDay) {
                    // All-day events are stored in UTC midnight; shift them to local days.
                    val utc = ZoneId.of("UTC")
                    startMs = Instant.ofEpochMilli(startMs).atZone(utc).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                    endMs = Instant.ofEpochMilli(endMs).atZone(utc).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                }
                events += CalendarEvent(
                    id = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(Sem título)",
                    start = Instant.ofEpochMilli(startMs),
                    end = Instant.ofEpochMilli(endMs),
                    allDay = allDay,
                    location = c.getString(5)?.takeIf { it.isNotBlank() },
                    description = c.getString(6)?.takeIf { it.isNotBlank() },
                    color = c.getInt(7),
                    calendarName = c.getString(8) ?: "",
                    accountName = c.getString(9) ?: "",
                )
            }
        }
        return events.sortedBy { it.start }
    }
}
