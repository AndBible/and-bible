package net.bible.android.view.activity.page

import android.app.Activity
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.ref.WeakReference

/** A window that moves from the bottom to the middle must reset its offsets in JS, not keep a stale bottomOffset. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BibleViewOffsetsTest {
    @Test fun windowMovedFromBottomToMiddleEmitsZeroOffsets() {
        val koin = GlobalContext.get()
        val windowControl = koin.get<WindowControl>()
        val repo = windowControl.windowRepository
        val window = repo.activeWindow
        val host = mock(ReadingHostActivity::class.java)
        `when`(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
        val callbacks = BibleViewHostCallbacks(
            hostActivity = mock(ActivityBase::class.java), onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { false },
            imeHeight = { 0 }, topOffset2 = { 0 }, bottomOffsetForWebView = { 300 }, insetsChanges = { error("Unused") })
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val view = BibleView(host, callbacks, WeakReference(window),
            koin.get(), koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get())
        view.setBibleJavascriptInterface(BibleJavascriptInterface(view))
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, PageTiltScroller(view, PageTiltScrollControl()))
        }
        try {
            activity.get().setContentView(view)
            view.setClientReady()
            view.contentVisible = true
            // Portrait: windows stack vertically, so a visible window that is neither first nor last is "middle".
            assertEquals(true, CommonUtils.isPortrait)
            repo.addNewWindow(window)
            repo.addNewWindow(window)
            // Order is [window, a, b]; make `window` the last visible one.
            windowControl.moveWindow(window, 2)
            assertEquals(window, repo.lastVisibleWindow)

            view.updateOffsets()
            val atBottom = shadowOf(view).lastEvaluatedJavascript
            assertNotEquals("bottom window must send a non-zero bottom offset: $atBottom", true, atBottom?.contains("\"set_offsets\", 0.0, 0.0,"))

            windowControl.moveWindow(window, 1)
            assertEquals(false, repo.firstVisibleWindow == window || repo.lastVisibleWindow == window)
            view.updateOffsets()
            val inMiddle = shadowOf(view).lastEvaluatedJavascript
            assertEquals(inMiddle, true, inMiddle?.startsWith("bibleView.emit(\"set_offsets\", 0.0, 0.0,"))
        } finally {
            view.destroy()
            activity.pause().stop().destroy()
        }
    }
}
