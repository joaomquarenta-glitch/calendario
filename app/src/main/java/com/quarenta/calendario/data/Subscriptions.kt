package com.quarenta.calendario.data

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One subscribed link, shown as its own read-only calendar on the phone. */
data class Subscription(
    val url: String,
    val name: String,
    val color: Int,
    val calendarId: Long,
    val lastSync: Long = 0,
    val lastError: String? = null,
    val eventCount: Int = 0,
)

/**
 * Subscribed iCalendar links (webcal://, https://…ics, iCloud public calendars).
 * Each one lives in a local calendar that this app owns, so its events show everywhere:
 * agenda, search, widget, reminders, and other calendar apps on the phone too.
 */
object Subscriptions {
    private const val PREFS = "subscriptions"
    private const val KEY = "list"
    const val ACCOUNT = "FortyCalendar"
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val _list = MutableStateFlow<List<Subscription>?>(null)

    fun observe(context: Context): StateFlow<List<Subscription>?> { all(context); return _list.asStateFlow() }

    fun all(context: Context): List<Subscription> = _list.value ?: load(context).also { _list.value = it }

    /** webcal://x -> https://x; adds https:// when the scheme is missing. */
    fun normalizeUrl(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        val withScheme = when {
            t.startsWith("webcal://", true) -> "https://" + t.substring(9)
            t.startsWith("webcals://", true) -> "https://" + t.substring(10)
            t.startsWith("http://", true) || t.startsWith("https://", true) -> t
            t.contains("://") -> return null
            else -> "https://$t"
        }
        val host = Uri.parse(withScheme).host
        return if (host.isNullOrBlank() || !host.contains('.')) null else withScheme
    }

    /** Downloads, creates its calendar and fills it. Returns an error message, or null when it worked. */
    fun add(context: Context, rawUrl: String, chosenName: String, color: Int): String? {
        val url = normalizeUrl(rawUrl) ?: return "O link não parece válido."
        if (all(context).any { it.url.equals(url, true) }) return "Já assinaste este link."
        val text = runCatching { download(url) }.getOrElse { return "Não foi possível abrir o link. Confirma que está certo e que tens internet." }
        if (!text.contains("BEGIN:VCALENDAR")) return "O link não é um calendário (.ics)."
        val name = chosenName.trim().ifBlank { IcsFeed.calendarName(text) ?: Uri.parse(url).host.orEmpty() }
        val calendarId = createCalendar(context, name, color) ?: return "O Android não deixou criar o calendário."
        val count = runCatching { fill(context, calendarId, text) }.getOrElse {
            deleteCalendar(context, calendarId)
            return "O calendário tem um formato que a app não conseguiu ler."
        }
        save(context, all(context) + Subscription(url, name, color, calendarId, System.currentTimeMillis(), null, count))
        return null
    }

    fun remove(context: Context, sub: Subscription) {
        deleteCalendar(context, sub.calendarId)
        save(context, all(context).filterNot { it.calendarId == sub.calendarId })
    }

    /** Refreshes every link that is older than [maxAgeMs]. Safe to call often. */
    fun syncDue(context: Context, maxAgeMs: Long = 6 * 3600_000L) {
        val now = System.currentTimeMillis()
        all(context).filter { now - it.lastSync >= maxAgeMs }.forEach { sync(context, it) }
    }

    fun sync(context: Context, sub: Subscription) {
        val updated = runCatching {
            val text = download(sub.url)
            require(text.contains("BEGIN:VCALENDAR"))
            val count = fill(context, sub.calendarId, text)
            sub.copy(lastSync = System.currentTimeMillis(), lastError = null, eventCount = count)
        }.getOrElse { sub.copy(lastSync = System.currentTimeMillis(), lastError = "Não foi possível atualizar") }
        save(context, all(context).map { if (it.calendarId == sub.calendarId) updated else it })
    }

    // ---- The calendar store -----------------------------------------------------------

    private fun asSync(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun createCalendar(context: Context, name: String, color: Int): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, name)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, color)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_READ)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, java.util.TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(asSync(CalendarContract.Calendars.CONTENT_URI), values) ?: return null
        return uri.lastPathSegment?.toLongOrNull()
    }

    private fun deleteCalendar(context: Context, calendarId: Long) {
        runCatching {
            context.contentResolver.delete(
                asSync(CalendarContract.Calendars.CONTENT_URI),
                "${CalendarContract.Calendars._ID} = ?", arrayOf(calendarId.toString()),
            )
        }
    }

    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** Replaces every event in the calendar with the feed's. Returns how many events it holds. */
    private fun fill(context: Context, calendarId: Long, text: String): Int {
        val events = IcsFeed.parse(text)
        val resolver = context.contentResolver
        val eventsUri = asSync(CalendarContract.Events.CONTENT_URI)
        resolver.delete(eventsUri, "${CalendarContract.Events.CALENDAR_ID} = ?", arrayOf(calendarId.toString()))
        events.chunked(200).forEach { chunk ->
            val ops = ArrayList<ContentProviderOperation>(chunk.size)
            chunk.forEach { e -> ops += ContentProviderOperation.newInsert(eventsUri).withValues(values(calendarId, e)).build() }
            resolver.applyBatch(CalendarContract.AUTHORITY, ops)
        }
        return events.size
    }

    private fun values(calendarId: Long, e: IcsFeed.FeedEvent) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, e.title)
        put(CalendarContract.Events.EVENT_LOCATION, e.location)
        put(CalendarContract.Events.DESCRIPTION, e.description)
        put(CalendarContract.Events.DTSTART, e.start.toEpochMilli())
        put(CalendarContract.Events.ALL_DAY, if (e.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (e.allDay) "UTC" else java.util.TimeZone.getDefault().id)
        put(CalendarContract.Events.UID_2445, e.uid)
        put(CalendarContract.Events.HAS_ALARM, 0)
        if (e.rrule != null) {
            // Android wants a duration, not an end, for repeating events.
            val seconds = maxOf(0L, (e.end.toEpochMilli() - e.start.toEpochMilli()) / 1000)
            put(CalendarContract.Events.DURATION, if (e.allDay) "P${maxOf(1L, seconds / 86400)}D" else "P${seconds}S")
            put(CalendarContract.Events.RRULE, e.rrule)
            if (e.exdates.isNotEmpty()) put(CalendarContract.Events.EXDATE, e.exdates.joinToString(",") { utc.format(it) })
        } else {
            put(CalendarContract.Events.DTEND, e.end.toEpochMilli())
        }
    }

    // ---- Network and storage ------------------------------------------------------------

    private fun download(url: String): String {
        var target = URL(url)
        repeat(5) {
            val conn = (target.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "text/calendar, */*")
                setRequestProperty("User-Agent", "FortyCalendar")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    target = URL(target, conn.getHeaderField("Location") ?: error("redirect"))
                    return@repeat
                }
                check(code == 200) { "HTTP $code" }
                val bytes = conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        check(out.size() <= MAX_BYTES) { "too big" }
                    }
                    out.toByteArray()
                }
                return bytes.toString(Charsets.UTF_8)
            } finally {
                conn.disconnect()
            }
        }
        error("too many redirects")
    }

    private fun save(context: Context, list: List<Subscription>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("url", it.url).put("name", it.name).put("color", it.color).put("calendarId", it.calendarId)
                .put("lastSync", it.lastSync).put("lastError", it.lastError ?: JSONObject.NULL).put("eventCount", it.eventCount))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
        _list.value = list
    }

    private fun load(context: Context): List<Subscription> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Subscription(
                url = o.getString("url"), name = o.getString("name"), color = o.getInt("color"),
                calendarId = o.getLong("calendarId"), lastSync = o.optLong("lastSync"),
                lastError = if (o.isNull("lastError")) null else o.optString("lastError"),
                eventCount = o.optInt("eventCount"),
            )
        }
    }.getOrDefault(emptyList())
}
