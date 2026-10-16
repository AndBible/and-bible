package net.bible.shareddata

import net.bible.android.database.DbDate
import net.bible.android.database.currentTimeMillis
import net.bible.android.database.secureRandomBytes
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformSeamsTest {
    @Test
    fun dbDateKeepsEpochMillisAndHasValueSemantics() {
        val date = DbDate(1_700_000_000_123L)
        assertEquals(1_700_000_000_123L, date.getTime())
        assertEquals(DbDate(1_700_000_000_123L), date)
        assertEquals(DbDate(1_700_000_000_123L).hashCode(), date.hashCode())
        assertNotEquals(DbDate(1L), date)
        assertTrue(DbDate(1L) < date)
        assertEquals(-5L, DbDate(-5L).getTime())
    }

    @Test
    fun dbDateDefaultIsNow() {
        val before = currentTimeMillis()
        val now = DbDate().getTime()
        val after = currentTimeMillis()
        assertTrue(now in before..after)
    }

    @Test
    fun secureRandomBytesHasRequestedSizeAndVaries() {
        assertEquals(0, secureRandomBytes(0).size)
        assertEquals(32, secureRandomBytes(32).size)
        assertNotEquals(secureRandomBytes(32).toList(), secureRandomBytes(32).toList())
    }

    @Test
    fun jswordTypesResolve() {
        assertEquals("KJV", assertNotNull(Versifications.instance().getVersification("KJV")).name)
    }
}
