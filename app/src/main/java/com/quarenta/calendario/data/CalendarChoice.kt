package com.quarenta.calendario.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which of the phone's calendars FortyCalendar leaves out (holidays, a work account...).
 * Kept only in this app: the calendars stay exactly as they are for every other app.
 */
object CalendarChoice {
    private const val PREFS = "calendar_choice"
    private const val HIDDEN = "hidden"

    private val _hidden = MutableStateFlow<Set<Long>?>(null)

    fun hidden(context: Context): Set<Long> = _hidden.value ?: load(context).also { _hidden.value = it }

    fun observe(context: Context): StateFlow<Set<Long>?> { hidden(context); return _hidden.asStateFlow() }

    fun setShown(context: Context, calendarId: Long, shown: Boolean) {
        val next = toggle(hidden(context), calendarId, shown)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(HIDDEN, next.map { it.toString() }.toSet()).apply()
        _hidden.value = next
    }

    private fun load(context: Context): Set<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(HIDDEN, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toSet()

    fun toggle(hidden: Set<Long>, calendarId: Long, shown: Boolean): Set<Long> =
        if (shown) hidden - calendarId else hidden + calendarId

    /** Calendars grouped by account, the accounts in alphabetical order, each account's calendars by name. */
    fun byAccount(calendars: List<CalendarInfo>): List<Pair<String, List<CalendarInfo>>> =
        calendars.groupBy { it.account.ifBlank { "Neste telemóvel" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (account, list) -> account to list.sortedWith(compareByDescending<CalendarInfo> { it.isPrimary }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
}
