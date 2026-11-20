package net.bible.android.view.widget

import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.bookmarks.SpeakSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widget buttons call the speak commands; the sleep toggle keeps the earlier widget's semantics. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WidgetActionsTest {
    private val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
    private val calls = mutableListOf<String>()
    private val recording = object : WidgetSpeakCommands {
        override fun togglePlayPause() { calls += "play" }
        override fun prevVerse() { calls += "prev" }
        override fun nextVerse() { calls += "next" }
        override fun rewind() { calls += "rewind" }
        override fun forward() { calls += "forward" }
        override fun stop() { calls += "stop" }
        override fun toggleSleepTimer() { calls += "sleep" }
        override fun speakFromBookmark(id: String) { calls += "bookmark:$id" }
    }

    @After fun restore() { WidgetCommands.override = null }

    private fun run(action: androidx.glance.appwidget.action.ActionCallback, params: ActionParameters = actionParametersOf()) {
        WidgetCommands.override = recording
        runBlocking { action.onAction(app, object : GlanceId {}, params) }
    }

    @Test fun everyButtonCallsItsCommand() {
        run(PlayPauseAction()); run(PrevVerseAction()); run(NextVerseAction()); run(RewindAction())
        run(ForwardAction()); run(StopAction()); run(ToggleSleepTimerAction())
        assertEquals(listOf("play", "prev", "next", "rewind", "forward", "stop", "sleep"), calls)
    }

    @Test fun rowTapSpeaksFromThatBookmark() {
        run(SpeakFromBookmarkAction(), actionParametersOf(SpeakFromBookmarkAction.BOOKMARK_ID to "abc"))
        assertEquals(listOf("bookmark:abc"), calls)
    }

    @Test fun sleepToggleRestoresLastSleepTimer() {
        val control = ControlWidgetSpeakCommands()
        SpeakSettings.load().apply { sleepTimer = 0; lastSleepTimer = 25 }.save()
        control.toggleSleepTimer()
        assertEquals(25, SpeakSettings.load().sleepTimer)
        control.toggleSleepTimer()
        assertEquals(0, SpeakSettings.load().sleepTimer)
    }
}
