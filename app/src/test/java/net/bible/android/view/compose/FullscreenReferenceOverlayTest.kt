package net.bible.android.view.compose

import android.view.MotionEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.BibleGestureListener
import net.bible.android.view.activity.page.BibleView
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.awaitReadingGraph
import net.bible.test.closeReadingHostsBeforeReset
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Catches the overlay sharing the tap-driven window-button timer instead of observing scrolling. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class FullscreenReferenceOverlayTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After fun tearDown() {
        controllers.forEach { it.get().fullScreen = false }
        closeReadingHostsBeforeReset(controllers)
    }

    private fun host(): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().start().resume().visible().get().also {
            awaitReadingGraph(it) { compose.waitForIdle() }
            compose.runOnIdle { it.fullScreen = true }
            compose.mainClock.advanceTimeBy(3000)
            compose.waitForIdle()
        }
    }

    @Test fun tappingInFullscreenDoesNotShowTheReferenceOverlay() {
        val activity = host()
        val text = activity.readingCommands.bibleOverlayText
        compose.onNodeWithText(text).assertDoesNotExist()
        val listener = BibleGestureListener(activity, (requireNotNull(CommonUtils.windowControl.activeWindow.bibleView) as BibleView), {}, {})
        val event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 50f, 50f, 0)
        try {
            compose.runOnIdle { listener.onSingleTapUp(event) }
            compose.mainClock.advanceTimeBy(300)
            compose.onNodeWithText(text).assertDoesNotExist()
        } finally {
            event.recycle()
            listener.destroy()
        }
    }

    @Test fun aTapDoesNotExtendTheReferenceOverlayAfterScrollingStops() {
        val activity = host()
        val text = activity.readingCommands.bibleOverlayText
        val view = requireNotNull(CommonUtils.windowControl.activeWindow.bibleView) as BibleView
        val listener = BibleGestureListener(activity, view, {}, {})
        val event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 50f, 50f, 0)
        try {
            compose.runOnIdle { view.scrollTo(0, 100) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1500)
            compose.onNodeWithText(text).assertIsDisplayed()
            compose.runOnIdle { listener.onSingleTapUp(event) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.onNodeWithText(text).assertDoesNotExist()
        } finally {
            event.recycle()
            listener.destroy()
        }
    }

    @Test fun scrollingShowsTheReferenceOverlayAndRestartsItsOwnIdleTimer() {
        val activity = host()
        val text = activity.readingCommands.bibleOverlayText
        val view = (requireNotNull(CommonUtils.windowControl.activeWindow.bibleView) as BibleView)
        compose.onNodeWithText(text).assertDoesNotExist()
        compose.runOnIdle { view.scrollTo(view.scrollX, view.scrollY) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText(text).assertDoesNotExist()
        compose.runOnIdle { view.scrollTo(0, 100) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { view.scrollTo(0, 120) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText(text).assertDoesNotExist()
    }
}
