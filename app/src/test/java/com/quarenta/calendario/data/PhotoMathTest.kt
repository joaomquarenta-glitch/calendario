package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PhotoMathTest {

    @Test fun sampleSizeKeepsAtLeastTheWantedSize() {
        assertEquals(1, PhotoMath.sampleSize(4000, 3000, 4000))
        assertEquals(1, PhotoMath.sampleSize(4000, 3000, 2560))
        assertEquals(2, PhotoMath.sampleSize(8000, 6000, 2560))
        assertEquals(4, PhotoMath.sampleSize(4000, 3000, 640))
        assertEquals(1, PhotoMath.sampleSize(500, 400, 640))
        assertEquals(1, PhotoMath.sampleSize(-1, -1, 640))
    }

    @Test fun fitScalesDownButNeverUp() {
        assertEquals(2560 to 1920, PhotoMath.fit(4000, 3000, 2560))
        assertEquals(1440 to 2560, PhotoMath.fit(3024, 5376, 2560))
        assertEquals(720 to 720, PhotoMath.fit(720, 720, 2560))
    }

    @Test fun readsExifDates() {
        val ms = PhotoMath.parseExifDate("2030:05:17 18:42:03", ZoneOffset.UTC)
        assertEquals(LocalDate.of(2030, 5, 17), java.time.Instant.ofEpochMilli(ms!!).atZone(ZoneOffset.UTC).toLocalDate())
        assertNull(PhotoMath.parseExifDate(null))
        assertNull(PhotoMath.parseExifDate("    :  :     :  :  "))
    }

    @Test fun warnsOnlyAfterAYear() {
        val today = LocalDate.of(2031, 6, 1)
        assertNull(PhotoMath.ageWarning(null, today))
        assertNull(PhotoMath.ageWarning(LocalDate.of(2030, 6, 2), today))
        assertEquals("Foto de há mais de 1 ano", PhotoMath.ageWarning(LocalDate.of(2030, 6, 1), today))
        assertEquals("Foto de há 3 anos", PhotoMath.ageWarning(LocalDate.of(2028, 1, 1), today))
    }
}
