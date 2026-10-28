package net.bible.android.view.activity.page

import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.cancel
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * A horizontal swipe that the native gesture listener handles (chapter/page swipe) must still
 * deliver the end of the touch to the WebView. Otherwise the page never sees pointerup/touchend:
 * a drag in progress in BibleView-JS (the memorize Order tiles, Sortable) never finishes and keeps
 * cancelling every later touchmove, so nothing in the page scrolls any more (#3736).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleViewSwipeTouchEndTest {
    private lateinit var repository: WindowRepository
    private lateinit var activity: NavHostComposeActivity
    private lateinit var factory: BibleViewFactory
    private lateinit var parent: FrameLayout
    private var swipeModeBefore: String? = null

    @Before fun setup() {
        repository = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = repository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(activity)
        activity.composeReadingViewHost = net.bible.test.fixtureReadingHost(activity)
        factory = activity.readingCommands.bibleViewFactory
        parent = FrameLayout(activity)
        // The fling is still recognised and consumed with swipe mode NONE, without navigating.
        swipeModeBefore = CommonUtils.settings.getString("bible_view_swipe_mode", null)
        CommonUtils.settings.setString("bible_view_swipe_mode", "NONE")
    }

    @After fun cleanup() {
        CommonUtils.settings.setString("bible_view_swipe_mode", swipeModeBefore)
        if (::parent.isInitialized) parent.removeAllViews()
        if (::factory.isInitialized) factory.clear()
        if (::activity.isInitialized) {
            activity.lifecycleScope.cancel()
            CurrentActivityHolder.deactivate(activity)
        }
        if (::repository.isInitialized) DatabaseResetter.resetDatabase(repository.scope)
    }

    private fun event(downTime: Long, offset: Long, action: Int, x: Float) =
        MotionEvent.obtain(downTime, downTime + offset, action, x, 500f, 0)

    @Test fun webViewReceivesTheTouchEndOfAHandledHorizontalSwipe() {
        val bibleView = factory.getOrCreateBibleView(repository.activeWindow)
        parent.addView(bibleView)
        val listener = shadowOf(bibleView).onTouchListener
        val down = SystemClock.uptimeMillis()

        val consumed = mutableListOf<Boolean>()
        consumed += listener.onTouch(bibleView, event(down, 0, MotionEvent.ACTION_DOWN, 800f))
        for (i in 1..8) {
            consumed += listener.onTouch(bibleView, event(down, i * 10L, MotionEvent.ACTION_MOVE, 800f - i * 60f))
        }
        val up = event(down, 90, MotionEvent.ACTION_UP, 260f)
        val upConsumed = listener.onTouch(bibleView, up)

        assertTrue("precondition: the swipe must be handled as a fling",
            ShadowLog.getLogsForTag("BibleGestureListener").any { it.msg.startsWith("onFling") && "horizontal:540.0" in it.msg })
        assertFalse("DOWN/MOVE reach the WebView", consumed.any { it })
        assertFalse("the WebView must receive ACTION_UP of a handled swipe", upConsumed)
    }
}
