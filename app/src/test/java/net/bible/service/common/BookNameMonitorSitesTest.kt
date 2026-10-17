package net.bible.service.common

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.abbreviated
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Characterizes the BookName monitor sites: the full/short flag is restored (also through the non-local `return`),
 * and the sites still exclude against plain `synchronized(BookName::class.java)` holders elsewhere in the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookNameMonitorSitesTest {
    private val v11n = Versifications.instance().getVersification("KJV")
    private val john316: Key = Verse(v11n, BibleBook.JOHN, 3, 16)
    private val gen1: VerseRange = VerseRange(v11n, Verse(v11n, BibleBook.GEN, 1, 1), Verse(v11n, BibleBook.GEN, 1, 3))

    @Test fun shortNameRestoresFullBookNameFlag() {
        val old = BookName.isFullBookName()
        try {
            BookName.setFullBookName(true)
            assertFalse(john316.shortName.isEmpty())
            assertTrue(BookName.isFullBookName())
        } finally { BookName.setFullBookName(old) }
    }

    @Test fun abbreviatedRestoresFullBookNameFlag() {
        val old = BookName.isFullBookName()
        try {
            BookName.setFullBookName(true)
            val short = gen1.abbreviated
            assertFalse(short.isEmpty())
            assertTrue(BookName.isFullBookName())
            BookName.setFullBookName(false)
            gen1.abbreviated
            assertFalse(BookName.isFullBookName())
        } finally { BookName.setFullBookName(old) }
    }

    @Test fun tinyNameRestoresTruncateLength() {
        val old = BookName.getTruncateShortName()
        try {
            BookName.setTruncateShortName(42)
            val tiny = john316.tinyName
            assertTrue(tiny.length <= 7 || tiny.isNotEmpty())
            assertEquals(42, BookName.getTruncateShortName())
        } finally { BookName.setTruncateShortName(old) }
    }

    @Test fun convertedSiteWaitsForALegacySynchronizedHolder() {
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val a = Thread {
            synchronized(BookName::class.java) {
                holding.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }.also { it.start() }
        assertTrue(holding.await(5, TimeUnit.SECONDS))
        val done = CountDownLatch(1)
        val b = Thread { john316.shortName; done.countDown() }.also { it.start() }
        assertFalse(done.await(200, TimeUnit.MILLISECONDS))
        release.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        a.join(); b.join()
    }
}
