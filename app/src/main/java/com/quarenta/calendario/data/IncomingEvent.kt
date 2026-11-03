package com.quarenta.calendario.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What another app asked us to do: add a new event, or show an existing one. */
sealed interface IncomingRequest {
    data class Add(val draft: EventDraft) : IncomingRequest
    data class Show(val eventId: Long) : IncomingRequest
}

/**
 * Turns the different "Add to calendar" hand-offs into one request:
 *  - apps (SAMS, airlines, banks...) that call the standard Android "insert event" action,
 *  - .ics files from emails and websites,
 *  - Google Calendar "add event" links (calendar.google.com/calendar/render?action=TEMPLATE...),
 *  - apps that ask to open an existing event.
 */
object IncomingEvent {

    fun parse(context: Context, intent: Intent?): IncomingRequest? {
        if (intent == null) return null
        val data = intent.data
        return when (intent.action) {
            Intent.ACTION_INSERT, Intent.ACTION_EDIT -> {
                val id = data?.let { eventIdFrom(it) }
                if (intent.action == Intent.ACTION_EDIT && id != null) IncomingRequest.Show(id)
                else IncomingRequest.Add(fromInsertExtras(intent))
            }
            Intent.ACTION_VIEW -> when {
                data == null -> null
                data.scheme == "https" || data.scheme == "http" -> fromGoogleLink(data)?.let { IncomingRequest.Add(it) }
                eventIdFrom(data) != null -> IncomingRequest.Show(eventIdFrom(data)!!)
                else -> readIcs(context, data)?.let { IncomingRequest.Add(it) }
            }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> readIcs(context, stream)?.let { IncomingRequest.Add(it) }
                    text != null && text.contains("BEGIN:VEVENT") -> parseIcs(text)?.let { IncomingRequest.Add(it) }
                    text != null -> Regex("https?://\\S+").find(text)?.value?.let { Uri.parse(it) }
                        ?.let { fromGoogleLink(it) }?.let { IncomingRequest.Add(it) }
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun eventIdFrom(uri: Uri): Long? {
        if (uri.authority != CalendarContract.AUTHORITY) return null
        val segs = uri.pathSegments
        return if (segs.size >= 2 && segs[0] == "events") segs[1].toLongOrNull() else null
    }

    private fun fromInsertExtras(intent: Intent): EventDraft {
        val now = Instant.now()
        val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L)
        val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L)
        val start = if (begin > 0) Instant.ofEpochMilli(begin) else now
        return EventDraft(
            title = intent.getStringExtra(CalendarContract.Events.TITLE).orEmpty(),
            start = start,
            end = if (end > 0) Instant.ofEpochMilli(end) else start.plusSeconds(3600),
            allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false),
            location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION).orEmpty(),
            description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION).orEmpty(),
        )
    }

    /** calendar.google.com/calendar/render?action=TEMPLATE&text=...&dates=A/B&details=...&location=... */
    fun fromGoogleLink(uri: Uri): EventDraft? {
        val host = uri.host ?: return null
        if (!host.endsWith("google.com")) return null
        if (!uri.getQueryParameter("action").equals("TEMPLATE", ignoreCase = true)) return null
        val dates = uri.getQueryParameter("dates")?.split('/')
        val start = dates?.getOrNull(0)?.let { parseIcsDate(it, null) }
        val end = dates?.getOrNull(1)?.let { parseIcsDate(it, null) }
        val allDay = dates?.getOrNull(0)?.length == 8
        val s = start ?: Instant.now()
        return EventDraft(
            title = uri.getQueryParameter("text").orEmpty(),
            start = s,
            end = end ?: s.plusSeconds(3600),
            allDay = allDay,
            location = uri.getQueryParameter("location").orEmpty(),
            description = uri.getQueryParameter("details").orEmpty(),
        )
    }

    private fun readIcs(context: Context, uri: Uri): EventDraft? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()?.let { parseIcs(it) }

    /** Minimal iCalendar reader: the first VEVENT's summary, times, place and notes. */
    fun parseIcs(text: String): EventDraft? {
        // Unfold lines (a line starting with a space continues the previous one).
        val lines = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n')
        var inEvent = false
        val props = mutableMapOf<String, Pair<String, String?>>() // name -> (value, TZID)
        for (line in lines) {
            when {
                line.startsWith("BEGIN:VEVENT") -> inEvent = true
                line.startsWith("END:VEVENT") -> break
                inEvent -> {
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val head = line.substring(0, colon)
                    val value = line.substring(colon + 1)
                    val parts = head.split(';')
                    val name = parts[0].uppercase()
                    val tzid = parts.drop(1).firstOrNull { it.startsWith("TZID=") }?.removePrefix("TZID=")
                    if (name !in props) props[name] = value to tzid
                }
            }
        }
        if (!inEvent) return null
        val (startRaw, startTz) = props["DTSTART"] ?: return null
        val start = parseIcsDate(startRaw, startTz) ?: return null
        val end = props["DTEND"]?.let { (v, tz) -> parseIcsDate(v, tz) }
        return EventDraft(
            title = unescape(props["SUMMARY"]?.first.orEmpty()),
            start = start,
            end = end ?: start.plusSeconds(3600),
            allDay = startRaw.length == 8,
            location = unescape(props["LOCATION"]?.first.orEmpty()),
            description = unescape(props["DESCRIPTION"]?.first.orEmpty()),
        )
    }

    private fun unescape(s: String) =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateOnly = DateTimeFormatter.ofPattern("yyyyMMdd")

    private fun parseIcsDate(raw: String, tzid: String?): Instant? = runCatching {
        val zone = tzid?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        when {
            raw.length == 8 -> LocalDate.parse(raw, dateOnly).atStartOfDay(ZoneId.systemDefault()).toInstant()
            raw.endsWith("Z") -> LocalDateTime.parse(raw.dropLast(1), dateTime).toInstant(ZoneOffset.UTC)
            else -> LocalDateTime.parse(raw.take(15), dateTime).atZone(zone).toInstant()
        }
    }.getOrNull()
}
