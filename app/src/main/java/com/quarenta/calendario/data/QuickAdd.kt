package com.quarenta.calendario.data

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Understands a compromisso written the way people say it in Portuguese:
 * "Jantar com Ana sexta 20h", "Dentista amanhã às 9h30", "Reunião dia 15 das 10h às 11h30",
 * "Aniversário da Rita 3 de maio". What is not a date or a time becomes the title.
 */
object QuickAdd {

    data class Result(
        val title: String,
        val date: LocalDate,
        val start: LocalTime?,
        val end: LocalTime?,
        val allDay: Boolean,
        /** True when the text said something about when; otherwise only the title was understood. */
        val foundWhen: Boolean,
    )

    private val weekdays = mapOf(
        "segunda" to DayOfWeek.MONDAY, "terca" to DayOfWeek.TUESDAY, "quarta" to DayOfWeek.WEDNESDAY,
        "quinta" to DayOfWeek.THURSDAY, "sexta" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY,
    )
    private val months = listOf(
        "janeiro", "fevereiro", "marco", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )
    private val shortMonths = months.map { it.take(3) }

    /** Little words that only link a date or time to the sentence ("na sexta", "às 9h", "dia 15"). */
    private val linkers = setOf("as", "a", "ao", "na", "no", "em", "de", "do", "da", "dia", "pelas", "pelos", "proxima", "proximo", "das", "desde")

    private val timeRegex = Regex("^(\\d{1,2})(?:[h:.](\\d{2})?)h?(?:m|min)?$")
    private val bareHour = Regex("^\\d{1,2}$")
    private val slashDate = Regex("^(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?$")

    fun parse(text: String, now: LocalDateTime): Result {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val keys = words.map(::key)
        val used = BooleanArray(words.size)
        val today = now.toLocalDate()

        var date: LocalDate? = null
        var weekday: DayOfWeek? = null
        var start: LocalTime? = null
        var end: LocalTime? = null
        var allDay = false

        fun use(vararg idx: Int) { idx.forEach { if (it in words.indices) used[it] = true } }
        fun k(i: Int) = keys.getOrNull(i)
        fun monthAt(i: Int): Int? = k(i)?.let { w -> months.indexOf(w).takeIf { it >= 0 } ?: shortMonths.indexOf(w).takeIf { it >= 0 && w.length == 3 } }?.plus(1)

        var i = 0
        while (i < words.size) {
            val w = keys[i]
            when {
                w == "depois" && k(i + 1) == "de" && (k(i + 2) == "amanha") -> { date = today.plusDays(2); use(i, i + 1, i + 2); i += 2 }
                w == "hoje" -> { date = today; use(i) }
                w == "amanha" -> { date = today.plusDays(1); use(i) }
                w.removeSuffix("-feira") in weekdays -> {
                    weekday = weekdays.getValue(w.removeSuffix("-feira")); use(i)
                    if (k(i + 1) == "feira") { use(i + 1); i++ }
                }
                w == "todo" && (k(i + 1) == "dia" || (k(i + 1) == "o" && k(i + 2) == "dia")) -> {
                    // "todo o dia" / "todo dia"
                    val last = if (k(i + 1) == "dia") i + 1 else i + 2
                    allDay = true
                    for (j in i..last) use(j)
                    i = last
                }
                w == "meio-dia" || (w == "meio" && k(i + 1) == "dia") -> {
                    val t = LocalTime.NOON
                    if (start == null) start = t else if (end == null) end = t
                    use(i); if (w == "meio") { use(i + 1); i++ }
                }
                slashDate.matches(w) -> {
                    val m = slashDate.find(w)!!.groupValues
                    val d = m[1].toInt(); val mo = m[2].toInt()
                    val y = m[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
                    dateOf(d, mo, y, today)?.let { date = it; use(i) }
                }
                bareHour.matches(w) && monthAt(i + 1) != null || (bareHour.matches(w) && k(i + 1) == "de" && monthAt(i + 2) != null) -> {
                    val mIdx = if (monthAt(i + 1) != null) i + 1 else i + 2
                    dateOf(w.toInt(), monthAt(mIdx)!!, null, today)?.let { date = it; use(i, mIdx); if (mIdx == i + 2) use(i + 1); i = mIdx }
                }
                w == "dia" && k(i + 1)?.let(bareHour::matches) == true -> {
                    val d = k(i + 1)!!.toInt()
                    val mIdx = when { monthAt(i + 2) != null -> i + 2; k(i + 2) == "de" && monthAt(i + 3) != null -> i + 3; else -> -1 }
                    val found = if (mIdx > 0) dateOf(d, monthAt(mIdx)!!, null, today) else dayOfMonthFrom(d, today)
                    if (found != null) {
                        date = found; use(i, i + 1)
                        if (mIdx > 0) { use(mIdx); if (mIdx == i + 3) use(i + 2); i = mIdx } else i++
                    }
                }
                bareHour.matches(w) && isTimeLinker(k(i - 1)) && w.toInt() in 0..23 -> {
                    val t = LocalTime.of(w.toInt(), 0)
                    if (start == null) start = t else if (end == null) end = t
                    use(i)
                }
                timeRegex.matches(w) && (w.any { it == 'h' || it == ':' || it == '.' } || isTimeLinker(k(i - 1))) -> {
                    val t = timeOf(w)
                    if (t != null) {
                        if (start == null) start = t else if (end == null) end = t
                        use(i)
                    }
                }
            }
            i++
        }

        // A time range written with a dash: "20h-22h" or "10:00-11:30".
        if (start == null) {
            words.indices.forEach { idx ->
                val parts = keys[idx].split('-')
                if (!used[idx] && parts.size == 2) {
                    val a = timeOf(parts[0]); val b = timeOf(parts[1])
                    if (a != null && b != null && (parts[0].any { it == 'h' || it == ':' } || parts[1].any { it == 'h' || it == ':' })) {
                        start = a; end = b; used[idx] = true
                    }
                }
            }
        }

        // Linking words right before something that was understood go too ("na sexta", "às 9h").
        for (idx in words.indices.reversed()) {
            if (!used[idx] && keys[idx] in linkers && idx + 1 < words.size && used[idx + 1]) used[idx] = true
        }
        // "das 10h às 11h": "as" between the two times.
        for (idx in 1 until words.size - 1) {
            if (!used[idx] && keys[idx] in linkers && used[idx - 1] && used[idx + 1]) used[idx] = true
        }

        val startTime = start
        if (weekday != null && date == null) {
            val wd = weekday!!
            var d = today.with(TemporalAdjusters.nextOrSame(wd))
            if (d == today && startTime != null && !startTime.isAfter(now.toLocalTime())) d = d.plusWeeks(1)
            date = d
        }
        val foundWhen = date != null || startTime != null || allDay
        val finalDate = date ?: when {
            startTime != null && !startTime.isAfter(now.toLocalTime()) -> today.plusDays(1)
            else -> today
        }
        val title = words.filterIndexed { idx, _ -> !used[idx] }.joinToString(" ")
            .trim().trimEnd(',', '-', '·').trim()
            .replaceFirstChar { it.uppercase() }
        return Result(
            title = title,
            date = finalDate,
            start = if (allDay) null else startTime,
            end = if (allDay || startTime == null) null else end?.takeIf { it.isAfter(startTime) },
            allDay = allDay || (startTime == null && date != null),
            foundWhen = foundWhen,
        )
    }

    private fun isTimeLinker(prev: String?) = prev == "as" || prev == "a" || prev == "pelas" || prev == "das"

    private fun timeOf(raw: String): LocalTime? {
        val m = timeRegex.find(raw) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }

    /** "dia 15": the 15th of this month, or of next month when it has passed. */
    private fun dayOfMonthFrom(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        var month = today.withDayOfMonth(1)
        repeat(3) {
            if (day <= month.lengthOfMonth()) {
                val d = month.withDayOfMonth(day)
                if (!d.isBefore(today)) return d
            }
            month = month.plusMonths(1)
        }
        return null
    }

    /** A day and month (and maybe year); without a year, the next time that date comes round. */
    private fun dateOf(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? = runCatching {
        if (year != null) return@runCatching LocalDate.of(year, month, day)
        val thisYear = LocalDate.of(today.year, month, day)
        if (thisYear.isBefore(today)) LocalDate.of(today.year + 1, month, day) else thisYear
    }.getOrNull()

    private fun key(word: String): String =
        Normalizer.normalize(word.lowercase().trim(',', '.', ';', '!', '?'), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
