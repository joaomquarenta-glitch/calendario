package com.quarenta.calendario.data

import java.time.LocalDate
import kotlin.math.roundToInt

/** One day's forecast: the WMO weather code and the day's highest and lowest temperature. */
data class DayWeather(val code: Int, val max: Double, val min: Double) {
    val symbol: String get() = Weather.symbol(code)
    val label: String get() = Weather.label(code)

    /** "☀️ 18°", small enough to sit beside a day's name. */
    val short: String get() = "$symbol ${max.roundToInt()}°"

    /** "18° / 11°". */
    val range: String get() = "${max.roundToInt()}° / ${min.roundToInt()}°"
}

/** Reads the free Open-Meteo forecast and names the weather in Portuguese. */
object Weather {
    const val DAYS = 16

    fun url(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.3f".format(java.util.Locale.ROOT, lat)}" +
            "&longitude=${"%.3f".format(java.util.Locale.ROOT, lon)}" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=$DAYS"

    /** The "daily" part of the answer, day by day. Anything unreadable is left out rather than guessed. */
    fun parse(json: String): Map<LocalDate, DayWeather> {
        val daily = json.substringAfter("\"daily\":", "").ifEmpty { return emptyMap() }
        val days = array(daily, "time").map { it.trim('"') }
        val codes = array(daily, "weather_code")
        val max = array(daily, "temperature_2m_max")
        val min = array(daily, "temperature_2m_min")
        val out = LinkedHashMap<LocalDate, DayWeather>()
        days.forEachIndexed { i, d ->
            val date = runCatching { LocalDate.parse(d) }.getOrNull() ?: return@forEachIndexed
            val code = codes.getOrNull(i)?.toDoubleOrNull()?.toInt() ?: return@forEachIndexed
            val hi = max.getOrNull(i)?.toDoubleOrNull() ?: return@forEachIndexed
            val lo = min.getOrNull(i)?.toDoubleOrNull() ?: return@forEachIndexed
            out[date] = DayWeather(code, hi, lo)
        }
        return out
    }

    private fun array(json: String, key: String): List<String> {
        val m = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]").find(json) ?: return emptyList()
        return m.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun symbol(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun label(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1 -> "Pouco nublado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Nevoeiro"
        in 51..57 -> "Chuvisco"
        in 61..65 -> "Chuva"
        66, 67 -> "Chuva gelada"
        in 80..82 -> "Aguaceiros"
        in 71..77, 85, 86 -> "Neve"
        in 95..99 -> "Trovoada"
        else -> "Tempo"
    }

    /** True when rain, snow or storm is on the way: worth a word on an outdoor plan. */
    fun isWet(code: Int): Boolean = code in 51..67 || code in 71..86 || code in 95..99
}
