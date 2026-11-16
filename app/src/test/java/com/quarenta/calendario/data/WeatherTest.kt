package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeatherTest {
    private val sample = """
        {"latitude":38.72,"longitude":-9.14,"daily_units":{"time":"iso8601","weather_code":"wmo code"},
         "daily":{"time":["2026-11-15","2026-11-16","2026-11-17"],
                  "weather_code":[0,61,95],
                  "temperature_2m_max":[18.4,15.0,13.6],
                  "temperature_2m_min":[11.2,10.1,9.0]}}
    """.trimIndent()

    @Test
    fun readsEachDay() {
        val days = Weather.parse(sample)
        assertEquals(3, days.size)
        assertEquals(DayWeather(0, 18.4, 11.2), days[LocalDate.of(2026, 11, 15)])
        assertEquals("☀️ 18°", days[LocalDate.of(2026, 11, 15)]!!.short)
        assertEquals("Chuva", days[LocalDate.of(2026, 11, 16)]!!.label)
        assertEquals("14° / 9°", days[LocalDate.of(2026, 11, 17)]!!.range)
    }

    @Test
    fun aBrokenAnswerGivesNothingRatherThanNonsense() {
        assertTrue(Weather.parse("{}").isEmpty())
        assertTrue(Weather.parse("not json").isEmpty())
        val partial = """{"daily":{"time":["2026-11-15","2026-11-16"],"weather_code":[1],"temperature_2m_max":[20,21],"temperature_2m_min":[10,11]}}"""
        assertEquals(setOf(LocalDate.of(2026, 11, 15)), Weather.parse(partial).keys)
    }

    @Test
    fun rainSnowAndStormsAreWet() {
        assertTrue(Weather.isWet(61))
        assertTrue(Weather.isWet(95))
        assertTrue(Weather.isWet(73))
        assertFalse(Weather.isWet(0))
        assertFalse(Weather.isWet(3))
    }

    @Test
    fun theAddressAlwaysUsesADot() {
        val u = Weather.url(38.7223, -9.1393)
        assertTrue(u.contains("latitude=38.722"))
        assertTrue(u.contains("longitude=-9.139"))
    }
}
