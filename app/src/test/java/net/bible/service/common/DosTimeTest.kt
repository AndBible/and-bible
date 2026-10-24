package net.bible.service.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.bible.android.platform.AndroidDateTimeFormats
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class DosTimeTest {
    private fun zipOutputStreamDosTime(epochMs: Long): Long {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("e").apply { time = epochMs })
                zip.closeEntry()
            }
        }.toByteArray()
        // Local header: time at offset 10-11, date at 12-13, little endian; the DOS time is date << 16 | time.
        fun u16(o: Int) = (bytes[o].toLong() and 0xff) or ((bytes[o + 1].toLong() and 0xff) shl 8)
        return (u16(12) shl 16) or u16(10)
    }

    @Test fun equalsZipOutputStreamDosTimeForTheSameInstantAndZone() {
        val formats = AndroidDateTimeFormats(ApplicationProvider.getApplicationContext<Context>())
        val saved = TimeZone.getDefault()
        try {
            val instants = listOf(
                1_700_000_000_000L,      // 2023-11-14 22:13:20Z, an even second
                1_700_000_001_000L,      // odd second
                1_711_846_799_000L,      // 2024-03-31 00:59:59Z, around the EU DST switch
                1_710_054_000_000L,      // 2024-03-10 07:00:00Z, around the US DST switch
                1_730_613_599_000L,      // 2024-11-03 05:59:59Z, around the US DST end
                315_532_800_000L,        // 1980-01-01 00:00:00Z
            )
            for (zone in listOf("Europe/Helsinki", "America/New_York", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (t in instants) {
                    assertEquals("$zone $t", zipOutputStreamDosTime(t), dosDateTime(t, formats))
                }
            }
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test fun before1980ClampsLikeZipEntry() {
        val formats = AndroidDateTimeFormats(ApplicationProvider.getApplicationContext<Context>())
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val t = 315_532_799_000L // 1979-12-31 23:59:59Z
            assertEquals((1L shl 21) or (1L shl 16), dosDateTime(t, formats))
            assertEquals(zipOutputStreamDosTime(t), dosDateTime(t, formats))
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
