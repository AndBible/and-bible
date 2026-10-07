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
import net.bible.android.database.bookmarks.SpeakSettings.RewindAmount
import net.bible.sharedcore.event.Subscription
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

    @Test
    fun speakingEmitsProgressForTheSpokenBook() {
        watch()
        speakControl.speakBible(book, verse("Rom.1.1"))
        speakControl.forward(RewindAmount.ONE_VERSE)
        val progress = seen.filterIsInstance<SpeakChange.Progress>()
        assertTrue("no Progress emitted", progress.isNotEmpty())
        assertTrue(progress.all { it.book == book })
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
        var failure: Throwable? = null
        val worker = Thread {
            try {
                s.save()
            } catch (e: Throwable) {
                failure = e
            }
        }
        worker.start()
        worker.join(5000)
        assertEquals("save did not return", false, worker.isAlive)
        failure?.let { throw it }
        // Main is not idled: a main-dispatched settings handler cannot satisfy this contract.
        assertEquals(listOf(SpeakPlaybackState.PAUSED, SpeakPlaybackState.SPEAKING), states())
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
