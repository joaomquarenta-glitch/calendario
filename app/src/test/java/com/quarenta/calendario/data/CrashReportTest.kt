package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CrashReportTest {
    private val header = CrashReport.header("0.3.99", "Marca Modelo", 34, LocalDateTime.of(2026, 3, 9, 14, 5))

    @Test fun headerHasOnlyTechnicalFacts() {
        assertEquals("FortyCalendar 0.3.99\nTelemóvel: Marca Modelo\nAndroid: API 34\nQuando: 2026-03-09 14:05", header)
    }

    @Test fun crashNamesTheErrorAndItsCause() {
        val error = IllegalStateException("outer", IllegalArgumentException("inner"))
        val text = CrashReport.crash(header, "main", error)
        assertTrue(text.startsWith(header))
        assertTrue(text.contains("Fio: main"))
        assertTrue(text.contains("java.lang.IllegalStateException: outer"))
        assertTrue(text.contains("Caused by: java.lang.IllegalArgumentException: inner"))
    }

    @Test fun longTracesAreCut() {
        fun deep(n: Int): Nothing = if (n == 0) throw RuntimeException("fundo") else deep(n - 1)
        val error = runCatching { deep(200) }.exceptionOrNull()!!
        val lines = CrashReport.stack(error, maxLines = 20).lines()
        assertEquals(21, lines.size)
        assertTrue(lines.last().startsWith("… (+"))
    }

    @Test fun shortTracesStayWhole() {
        val text = CrashReport.stack(RuntimeException("x"), maxLines = 10_000)
        assertFalse(text.contains("… (+"))
    }

    @Test fun manualReportAttachesLastCrashOnlyWhenThereIsOne() {
        assertFalse(CrashReport.manual(header, null).contains("Último erro"))
        assertTrue(CrashReport.manual(header, "Erro: x").contains("--- Último erro guardado ---\nErro: x"))
    }
}
