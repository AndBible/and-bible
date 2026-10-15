package net.bible.sharedcore.platform.files

import okio.Buffer
import okio.IOException
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupFileSnifferTest {
    private fun sniff(text: String) = BackupFileSniffer.sniff { Buffer().writeUtf8(text) }

    @Test fun sqliteHeaderWithExtraBytes() {
        assertEquals(BackupFileType.SQLITE3, sniff("SQLite format 3\u0000extra"))
    }

    @Test fun sqliteRequiresTerminatingNul() {
        assertEquals(BackupFileType.UNKNOWN, sniff("SQLite format 3"))
    }

    @Test fun shortZipWithExtraBytes() {
        assertEquals(BackupFileType.ZIP, sniff("PK\u0003\u0004ab"))
    }

    @Test fun exactZipSignature() {
        assertEquals(BackupFileType.ZIP, sniff("PK\u0003\u0004"))
    }

    @Test fun incompleteZipSignature() {
        assertEquals(BackupFileType.UNKNOWN, sniff("PK\u0003"))
    }

    @Test fun emptyFile() {
        assertEquals(BackupFileType.UNKNOWN, sniff(""))
    }

    @Test fun unrelatedHeader() {
        assertEquals(BackupFileType.UNKNOWN, sniff("not a backup file"))
    }

    @Test fun openingFailure() {
        assertEquals(BackupFileType.UNKNOWN, BackupFileSniffer.sniff { throw IOException("cannot open") })
    }

    @Test fun secondReadFailureIsUnknownAndClosesSource() {
        var reads = 0
        var closed = false
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reads++
                if (reads == 2) throw IOException("second read failed")
                sink.writeUtf8("PK\u0003\u0004")
                return 4
            }
            override fun timeout() = Timeout.NONE
            override fun close() { closed = true }
        }
        assertEquals(BackupFileType.UNKNOWN, BackupFileSniffer.sniff { source })
        assertEquals(2, reads)
        assertTrue(closed)
    }

    @Test fun fragmentedSqliteHeaderIsReadAndClosed() {
        val data = Buffer().writeUtf8("SQLite format 3\u0000")
        var closed = false
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long) = data.read(sink, minOf(1L, byteCount))
            override fun timeout() = Timeout.NONE
            override fun close() { closed = true }
        }
        assertEquals(BackupFileType.SQLITE3, BackupFileSniffer.sniff { source })
        assertTrue(closed)
    }

    @Test fun closeFailureIsUnknown() {
        val source = object : Source by Buffer().writeUtf8("SQLite format 3\u0000") {
            override fun close() { throw IOException("close failed") }
        }
        assertEquals(BackupFileType.UNKNOWN, BackupFileSniffer.sniff { source })
    }
}
