package net.bible.service.device.speak

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.speak.SPEAK_KEY
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.RangedPassage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Guards the locking of the speak providers and the TTS manager. Their monitors are re-entrant
 * (`setupReading` -> `reset`, `stop` -> `reset`) and a revision check separates a speech cursor
 * calculated outside the lock from a concurrent reset. Any replacement lock must keep that.
 */
@RunWith(RobolectricTestRunner::class)
class SpeakLockingTest : SpeakIntegrationTestBase() {
    private fun verse(ref: String) = (book.getKey(ref) as RangedPassage).getVerseAt(0)

    private fun bibleProvider() = BibleSpeakTextProvider(
        BibleTraverser(DocumentBibleBooksFactory()), bookmarkControl, book, fakePlatform,
        net.bible.test.testAppSettings(), GlobalContext.get().get(), GlobalContext.get().get(),
        swordContentFacade = net.bible.test.testSwordContentFacade(),
    )

    private val dictionary get() = requireNotNull(Books.installed().getBook("StrongsGreek"))

    private fun generalProvider() = GeneralSpeakTextProvider(
        bookmarkControl, dictionary, fakePlatform, net.bible.test.testAppSettings(),
        GlobalContext.get().get(), GlobalContext.get().get(), GlobalContext.get().get(),
        swordContentFacade = net.bible.test.testSwordContentFacade(),
        strings = net.bible.test.testCoreStrings(), traverser = BibleTraverser(DocumentBibleBooksFactory()),
    )

    private fun generalKey(): BookAndKey {
        val key = dictionary.getKey("00001")
        val ordinal = runBlocking { SwordContentFacade.ordinalRangeFor(dictionary, key, strings = net.bible.test.testCoreStrings()) }.first
        return BookAndKey(key, dictionary, OrdinalRange(ordinal, ordinal))
    }

    /** Runs [block] on a thread and fails if it does not finish in [millis] (a re-entrancy deadlock).
     * The bound is generous because the first setupReading loads the Sword book. */
    private fun assertCompletes(millis: Long = 30_000, block: () -> Unit) {
        val error = AtomicReference<Throwable?>()
        val t = thread(isDaemon = true) { try { block() } catch (e: Throwable) { error.set(e) } }
        t.join(millis)
        assertFalse("Deadlock: block did not finish in $millis ms", t.isAlive)
        error.get()?.let { throw it }
    }

    /** Runs [a] and [b] on two threads for [millis]; returns every exception that was not a revision cancellation. */
    private fun race(millis: Long, a: () -> Unit, b: () -> Unit): List<Throwable> {
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val deadline = System.nanoTime() + millis * 1_000_000
        fun loop(body: () -> Unit) = thread(isDaemon = true) {
            while (System.nanoTime() < deadline) {
                try { body() } catch (e: CancellationException) { /* revision check */ } catch (e: Throwable) { failures += e; return@thread }
            }
        }
        val threads = listOf(loop(a), loop(b))
        threads.forEach { it.join(millis + 10_000) }
        assertTrue("Race threads did not finish", threads.none { it.isAlive })
        return failures
    }

    @Test
    fun bibleProviderLockIsReentrant() {
        val provider = bibleProvider()
        assertCompletes {
            provider.setupReading(book, verse("Rom.1.1")) // @Synchronized -> reset() @Synchronized
            provider.stop() // @Synchronized -> reset()
            provider.pause() // @Synchronized -> reset()
            provider.prepareForStartSpeaking()
        }
    }

    @Test
    fun generalProviderLockIsReentrant() {
        val provider = generalProvider()
        assertCompletes {
            provider.setupReading(generalKey())
            provider.stop()
            provider.pause()
            provider.prepareForStartSpeaking()
        }
    }

    @Test
    fun bibleRevisionCheckStillExcludesConcurrentReset() {
        val provider = bibleProvider()
        provider.setupReading(book, verse("Rom.1.1"))
        var n = 0
        val failures = race(
            2000,
            { runBlocking { provider.getNextSpeakCommand("race-${n++}") } },
            { provider.setupReading(book, verse("Rom.1.1")); provider.reset() },
        )
        assertTrue("Unexpected: $failures", failures.isEmpty())
        // State must be consistent: after a final single-threaded setup, the provider replays like a fresh one.
        provider.setupReading(book, verse("Rom.1.1"))
        val fresh = bibleProvider().also { it.setupReading(book, verse("Rom.1.1")) }
        val replayed = (0 until 5).map { (runBlocking { provider.getNextSpeakCommand("replay-$it") }).toString().substringBefore("@") }
        val expected = (0 until 5).map { (runBlocking { fresh.getNextSpeakCommand("replay-$it") }).toString().substringBefore("@") }
        assertEquals(expected, replayed)
        assertEquals(fresh.getTotalChars(), provider.getTotalChars())
    }

    @Test
    fun generalRevisionCheckStillExcludesConcurrentReset() {
        val provider = generalProvider()
        provider.setupReading(generalKey())
        var n = 0
        val failures = race(
            2000,
            { runBlocking { provider.getNextSpeakCommand("race-${n++}") } },
            { provider.setupReading(generalKey()); provider.reset() },
        )
        assertTrue("Unexpected: $failures", failures.isEmpty())
        provider.setupReading(generalKey())
        val fresh = generalProvider().also { it.setupReading(generalKey()) }
        val replayed = (0 until 3).map { (runBlocking { provider.getNextSpeakCommand("replay-$it") }).toString().substringBefore("@") }
        val expected = (0 until 3).map { (runBlocking { fresh.getNextSpeakCommand("replay-$it") }).toString().substringBefore("@") }
        assertEquals(expected, replayed)
    }

    @Test
    fun ttsManagerStateEventsSerialised() {
        speakControl.speakBible(book, verse("Rom.1.1"))
        runBlocking { kotlinx.coroutines.withTimeout(4000) { GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(SPEAK_KEY) } }
        assertTrue(speakControl.isSpeaking)
        val listener = fakePlatform.listener!!
        var n = 0
        val failures = race(
            1500,
            { listener.onUtteranceStart("AND-BIBLE-${n % 5}"); listener.onUtteranceDone("AND-BIBLE-${n++ % 5}") },
            { speakControl.stop(); speakControl.isSpeaking },
        )
        assertTrue("Unexpected: $failures", failures.isEmpty())
        speakControl.stop(force = true)
        assertFalse(speakControl.isSpeaking)
        assertTrue(fakePlatform.focusAbandons > 0)
    }
}
