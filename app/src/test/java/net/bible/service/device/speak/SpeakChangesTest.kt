package net.bible.service.device.speak

import android.os.Looper
import org.robolectric.Shadows.shadowOf
import org.koin.core.context.GlobalContext
import net.bible.android.control.speak.SpeakTransportServiceImpl
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakPlaybackState
import net.bible.android.control.speak.SpeakSettingsChange
import net.bible.android.control.speak.SpeakSettingsChanges
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.sharedcore.event.Subscription
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.VerseRange
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.crosswire.jsword.passage.RangedPassage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec section 2.1-2.2: events emitted through the real (mocked-TTS) SpeakControl. */
@RunWith(RobolectricTestRunner::class)
class SpeakChangesTest : SpeakIntegrationTestBase() {
    private val seen = mutableListOf<SpeakChange>()
    private val settingsSeen = mutableListOf<SpeakSettingsChange>()
    private val subs = mutableListOf<Subscription>()

    private fun watch() {
        subs += SpeakChanges.changes.subscribe { seen += it }
        subs += SpeakSettingsChanges.changes.subscribe { settingsSeen += it }
    }

    @After fun cancel() { subs.forEach { it.cancel() } }

    private fun verse(ref: String) = (book.getKey(ref) as RangedPassage).getVerseAt(0)
    private fun states() = seen.filterIsInstance<SpeakChange.State>().map { it.speakState }

    @Test
    fun speakPauseContinueStopEmitTheStatesInOrder() {
        watch()
        speakControl.speakBible(book, verse("Rom.1.1"))
        speakControl.pause()
        speakControl.continueAfterPause()
        speakControl.stop()
        assertEquals(
            listOf(SpeakPlaybackState.SPEAKING, SpeakPlaybackState.PAUSED, SpeakPlaybackState.SPEAKING, SpeakPlaybackState.SILENT),
            states(),
        )
    }

    private fun nextText(provider: SpeakTextProvider, prefix: String): Pair<String, TextCommand> {
        repeat(20) { index ->
            val id = "$prefix-$index"
            val command = provider.getNextSpeakCommand(id)
            if (command is TextCommand) return id to command
        }
        error("No text command in the first 20 commands")
    }

    @Test
    fun bibleStartUtteranceEmitsTitleAndTextWithTheirVerseRanges() {
        val provider = BibleSpeakTextProvider(BibleTraverser(DocumentBibleBooksFactory()), bookmarkControl, book)
        provider.settings = SpeakSettings(playbackSettings = PlaybackSettings(speakTitles = true))
        provider.setupReading(book, verse("Rom.1.1"))
        watch()
        for (type in listOf(TextCommand.TextType.TITLE, TextCommand.TextType.NORMAL)) {
            val (id, command) = nextText(provider, type.name)
            assertEquals(type, command.type)
            assertTrue(command.text.isNotBlank())
            seen.clear()
            provider.startUtterance(id)
            val progress = seen.filterIsInstance<SpeakChange.Progress>().single()
            assertSame(book, progress.book)
            assertSame(command, progress.speakCommand)
            assertTrue(progress.key is VerseRange)
            assertEquals("Rom.1.1-Rom.1.3", progress.key.osisRef)
            assertFalse(progress.forceFollow)
        }
    }

    @Test
    fun generalStartUtteranceEmitsTextKeyAndForceFollowForASelectedRange() {
        // The general provider also reads dictionaries; use real OSIS/ordinal extraction.
        val dictionary = requireNotNull(Books.installed().getBook("StrongsGreek"))
        val key = dictionary.getKey("00001")
        val ordinal = SwordContentFacade.ordinalRangeFor(dictionary, key).first
        val provider = GeneralSpeakTextProvider(bookmarkControl, dictionary)
        provider.setupReading(BookAndKey(key, dictionary, OrdinalRange(ordinal, ordinal)))
        val (id, command) = nextText(provider, "general")
        assertEquals(TextCommand.TextType.NORMAL, command.type)
        assertTrue("Real dictionary text must be queued", command.text.isNotBlank())
        watch()
        provider.startUtterance(id)
        val progress = seen.filterIsInstance<SpeakChange.Progress>().single()
        assertSame(dictionary, progress.book)
        assertSame(command, progress.speakCommand)
        val emittedKey = progress.key as BookAndKey
        assertSame(dictionary, emittedKey.document)
        assertEquals(key.osisRef, emittedKey.key.osisRef)
        assertEquals(ordinal, emittedKey.ordinal!!.start)
        assertEquals(ordinal, emittedKey.ordinal.end)
        assertTrue(progress.forceFollow)
    }

    @Test
    fun legacyStartUtteranceEmitsCurrentTextThenBookTitleForTheSelectedKey() {
        val key = book.getKey("Rom.1.1")
        val provider = LegacySpeakTextProvider()
        provider.setupReading(book, listOf(key))
        val (id, command) = nextText(provider, "legacy")
        watch()
        provider.startUtterance(id)
        val progress = seen.filterIsInstance<SpeakChange.Progress>()
        assertEquals(2, progress.size)
        progress.forEach {
            assertSame(book, it.book)
            assertSame(key, it.key)
            assertFalse(it.forceFollow)
        }
        val normal = progress[0].speakCommand as TextCommand
        assertEquals(TextCommand.TextType.NORMAL, normal.type)
        assertEquals(command.text, normal.text)
        val title = progress[1].speakCommand as TextCommand
        assertEquals(TextCommand.TextType.TITLE, title.type)
        assertEquals(book.name, title.text)
    }

    @Test
    fun shutdownWithContinueEmitsTemporaryStop() {
        speakControl.speakBible(book, verse("Rom.1.1"))
        watch()
        speakControl.stop(willContinueAfter = true)
        assertEquals(listOf(SpeakPlaybackState.TEMPORARY_STOP), states())
    }

    @Test
    fun saveEmitsTheSettingsChangeWithItsFlags() {
        watch()
        val s = SpeakSettings.load()
        s.sleepTimer = s.sleepTimer + 5
        s.save()
        val change = settingsSeen.single()
        assertEquals(s.sleepTimer, change.speakSettings.sleepTimer)
        assertEquals(true, change.sleepTimerChanged)
        assertEquals(false, change.updateBookmark)
    }

    @Test
    fun saveWithUnchangedSettingsEmitsNothing() {
        SpeakSettings.load().save() // make currentSettings equal to what we save next
        watch()
        SpeakSettings.load().save()
        assertEquals(emptyList<SpeakSettingsChange>(), settingsSeen)
    }
    @Test
    fun settingsChangeWhileSpeakingPausesAndContinuesInsideSave() {
        speakControl.speakBible(book, verse("Rom.1.1"))
        watch()
        val s = SpeakSettings.load()
        s.playbackSettings = s.playbackSettings.copy(speed = s.playbackSettings.speed + 10)
        s.save()
        // No looper idling: SpeakControl's reaction must have run synchronously inside save().
        assertEquals(listOf(SpeakPlaybackState.PAUSED, SpeakPlaybackState.SPEAKING), states())
    }

    @Test
    fun settingsChangeFromWorkerPausesAndContinuesBeforeSaveReturns() {
        speakControl.speakBible(book, verse("Rom.1.1"))
        watch()
        val s = SpeakSettings.load()
        s.playbackSettings = s.playbackSettings.copy(speed = s.playbackSettings.speed + 10)
        runWorkerAndAwait { s.save() }
        // Main is not idled: a main-dispatched settings handler cannot satisfy this contract.
        assertEquals(listOf(SpeakPlaybackState.PAUSED, SpeakPlaybackState.SPEAKING), states())
    }

    @Test
    fun workerTimeoutInterruptsAndBoundsCleanupEvenWhenWorkIgnoresInterruption() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val workerRef = AtomicReference<Thread>()
        try {
            try {
                runWorkerAndAwait(timeoutMillis = 100, cleanupMillis = 100) {
                    workerRef.set(Thread.currentThread())
                    started.countDown()
                    try {
                        while (release.count > 0) {
                            try { release.await() } catch (_: InterruptedException) { interrupted.countDown() }
                        }
                    } finally { finished.countDown() }
                }
                fail("Expected timeout")
            } catch (expected: AssertionError) {
                assertEquals("worker did not return within 100 ms", expected.message)
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            assertTrue(interrupted.await(1, TimeUnit.SECONDS))
            // Interruption cannot cancel arbitrary work: daemon status contains JVM exit risk.
            assertTrue(workerRef.get().isDaemon)
            assertTrue(workerRef.get().isAlive)
        } finally {
            release.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))
            workerRef.get()?.join(1000)
        }
    }

    @Test
    fun workerFailuresReachTheCallingTest() {
        val failure = IllegalStateException("worker failure")
        try {
            runWorkerAndAwait { throw failure }
            fail("Expected worker failure")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
    }

    @Test
    fun stopWhenStoppedHidesTheBarAndARefreshKeepsItHidden() {
        val transport = GlobalContext.get().get<SpeakTransportServiceImpl>()
        transport.setTransportVisible(true)
        assertEquals(true, transport.state.value.visible)
        transport.stop() // speech is stopped: hides
        assertEquals(false, transport.state.value.visible)
        SpeakChanges.notifyState(SpeakPlaybackState.SILENT) // a refresh keeps visible as is
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, transport.state.value.visible)
    }
}

/** Bounded test wait; interruption is best-effort, not a guarantee of cancellation. */
private fun runWorkerAndAwait(timeoutMillis: Long = 5000, cleanupMillis: Long = 250, block: () -> Unit) {
    val failure = AtomicReference<Throwable?>()
    val worker = Thread {
        try { block() } catch (e: Throwable) { failure.set(e) }
    }.apply { isDaemon = true }
    worker.start()
    try {
        worker.join(timeoutMillis)
        if (worker.isAlive) throw AssertionError("worker did not return within $timeoutMillis ms")
        failure.get()?.let { throw it }
    } finally {
        if (worker.isAlive) {
            worker.interrupt()
            worker.join(cleanupMillis)
        }
    }
}
