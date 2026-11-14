package net.bible.android.view.activity.page

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.cancel
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.settings.DOUBLE_TAP_TO_FULLSCREEN_KEY
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * #4083: both taps of a double tap also reach the page as clicks, so a double tap in a margin turned the page while it
 * toggled full screen. The page can undo those turns only if native tells it that it acted on a double tap: the
 * gesture listener sends `double_tap` whenever it toggles full screen, and only then.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleViewDoubleTapEventTest {
    private lateinit var repository: WindowRepository
    private lateinit var controller: ActivityController<NavHostComposeActivity>
    private lateinit var activity: NavHostComposeActivity
    private lateinit var factory: BibleViewFactory
    private lateinit var parent: FrameLayout
    private var doubleTapSettingBefore: Boolean = true
    private val fullScreenBefore = SharedActivityState.instance.isFullScreen

    @Before fun setup() {
        repository = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
        controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        )
        activity = controller.get()
        activity.readingAppBootstrap.windowRepository = repository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(activity)
        activity.composeReadingViewHost = net.bible.test.fixtureReadingHost(activity)
        factory = activity.readingCommands.bibleViewFactory
        parent = FrameLayout(activity)
        doubleTapSettingBefore = CommonUtils.settings.getBoolean(DOUBLE_TAP_TO_FULLSCREEN_KEY, true)
    }

    @After fun cleanup() {
        CommonUtils.settings.setBoolean(DOUBLE_TAP_TO_FULLSCREEN_KEY, doubleTapSettingBefore)
        if (SharedActivityState.instance.isFullScreen != fullScreenBefore) SharedActivityState.instance.toggleFullScreen()
        if (::parent.isInitialized) parent.removeAllViews()
        if (::factory.isInitialized) factory.clear()
        if (::activity.isInitialized) {
            activity.lifecycleScope.cancel()
            CurrentActivityHolder.deactivate(activity)
        }
        if (::controller.isInitialized) controller.close()
        if (::repository.isInitialized) DatabaseResetter.resetDatabase(repository.scope)
    }

    private fun event(downTime: Long, eventTime: Long, action: Int) =
        MotionEvent.obtain(downTime, eventTime, action, 20f, 500f, 0)

    /** Two taps in the left margin, as close together as a double tap; returns every script the view ran meanwhile. */
    private fun doubleTap(bibleView: BibleView): List<String> {
        val listener = shadowOf(bibleView).onTouchListener
        val scripts = mutableListOf<String>()
        fun send(e: MotionEvent) {
            listener.onTouch(bibleView, e)
            // The view is not attached to a window here, so the channel queues; flush runs what was sent, in order.
            bibleView.jsChannel.flush()
            shadowOf(bibleView).lastEvaluatedJavascript?.let { if (scripts.lastOrNull() !== it) scripts += it }
        }
        val first = SystemClock.uptimeMillis()
        send(event(first, first, MotionEvent.ACTION_DOWN))
        send(event(first, first + 50, MotionEvent.ACTION_UP))
        val second = first + 50 + ViewConfiguration.getDoubleTapTimeout() / 2
        send(event(second, second, MotionEvent.ACTION_DOWN))
        send(event(second, second + 50, MotionEvent.ACTION_UP))
        return scripts
    }

    private fun readyView(): BibleView = factory.getOrCreateBibleView(repository.activeWindow).also {
        parent.addView(it)
        it.setClientReady()
        it.jsChannel.flush()
    }

    private fun doubleTapEvents(scripts: List<String>) = scripts.count { it.contains("bibleView.emit(\"double_tap\")") }

    @Test fun enteringFullScreenByDoubleTapTellsThePage() {
        CommonUtils.settings.setBoolean(DOUBLE_TAP_TO_FULLSCREEN_KEY, true)
        if (SharedActivityState.instance.isFullScreen) SharedActivityState.instance.toggleFullScreen()
        val scripts = doubleTap(readyView())
        assertEquals("full screen toggled", true, SharedActivityState.instance.isFullScreen)
        assertEquals("double_tap sent once: $scripts", 1, doubleTapEvents(scripts))
    }

    @Test fun leavingFullScreenByDoubleTapTellsThePageEvenWithTheSettingOff() {
        CommonUtils.settings.setBoolean(DOUBLE_TAP_TO_FULLSCREEN_KEY, false)
        if (!SharedActivityState.instance.isFullScreen) SharedActivityState.instance.toggleFullScreen()
        val scripts = doubleTap(readyView())
        assertEquals("full screen left", false, SharedActivityState.instance.isFullScreen)
        assertEquals("double_tap sent once: $scripts", 1, doubleTapEvents(scripts))
    }

    @Test fun aDoubleTapThatDoesNothingDoesNotTellThePage() {
        CommonUtils.settings.setBoolean(DOUBLE_TAP_TO_FULLSCREEN_KEY, false)
        if (SharedActivityState.instance.isFullScreen) SharedActivityState.instance.toggleFullScreen()
        val scripts = doubleTap(readyView())
        assertEquals(false, SharedActivityState.instance.isFullScreen)
        assertEquals("no double_tap: the two taps stay two page turns: $scripts", 0, doubleTapEvents(scripts))
    }
}
