package net.bible.android.view.activity.page

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.cancel
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWebView
import org.robolectric.util.ReflectionHelpers
import android.webkit.WebView
import android.os.Looper

/**
 * Follow-up of osTicket 3496: out of touch mode (mouse, hardware keyboard, Waydroid), when the focused
 * view lets go of focus (the reading-view search field or the search-index sheet leaving the
 * composition), Android hands focus to the first focusable view of the window
 * (`View.clearFocus` -> `rootViewRequestFocus`). That is usually window 1's BibleView, and
 * `BibleView.onFocusChanged` used to make ANY focus gain the active window: a search made in
 * window 3 silently made window 1 active. Seen on the emulator (2026-11-01) with this stack:
 * `FocusOwnerImpl.clearOwnerFocus` -> `View.clearFocus` -> `rootViewRequestFocus` ->
 * `WebView.requestFocus(FOCUS_DOWN, null)` -> `BibleView.onFocusChanged`.
 *
 * Focus that the user moves from a focused view (keyboard/D-pad navigation between windows) must
 * still change the active window, and so must any focus gain in touch mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK], shadows = [BibleViewFocusHandOverActiveWindowTest.ShadowFocusableWebView::class])
class BibleViewFocusHandOverActiveWindowTest {
    /**
     * The device's WebView (WebViewChromium) takes view focus through `super_requestFocus`, i.e.
     * `ViewGroup.requestFocus`, and its `findFocus` answers `super.findFocus()`. Robolectric's
     * stand-in provider answers `false` and `null`, so no WebView could gain focus, nor be found
     * focused, in a test. Do what the device does.
     */
    @Implements(WebView::class)
    class ShadowFocusableWebView : ShadowWebView() {
        @RealObject private lateinit var realWebView: WebView

        @Implementation
        fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean =
            Shadow.directlyOn(realWebView, ViewGroup::class.java, "requestFocus",
                ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, direction),
                ReflectionHelpers.ClassParameter.from(Rect::class.java, previouslyFocusedRect))

        @Implementation
        fun findFocus(): View? = Shadow.directlyOn(realWebView, ViewGroup::class.java, "findFocus")
    }

    private lateinit var repository: WindowRepository
    private lateinit var navActivity: NavHostComposeActivity
    private lateinit var factory: BibleViewFactory
    private lateinit var container: ActivityController<Activity>
    private lateinit var window1: Window
    private lateinit var window2: Window
    private lateinit var view1: BibleView
    private lateinit var view2: BibleView
    /** Stands in for the search field: a focusable view that takes focus and then lets go of it. */
    private lateinit var field: View
    private val canFocusZeroSized = View::class.java.getDeclaredField("sCanFocusZeroSized").apply { isAccessible = true }
    private var canFocusZeroSizedBefore: Boolean? = null

    @Before fun setup() {
        repository = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
        navActivity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        navActivity.readingAppBootstrap.windowRepository = repository
        navActivity.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(navActivity)
        navActivity.composeReadingViewHost = net.bible.test.fixtureReadingHost(navActivity)
        factory = navActivity.readingCommands.bibleViewFactory

        window1 = repository.activeWindow
        window2 = repository.addNewWindow()
        view1 = factory.getOrCreateBibleView(window1)
        view2 = factory.getOrCreateBibleView(window2)
        // The platform WebView makes itself focusable (also in touch mode) when it initialises its
        // provider; Robolectric's ShadowWebView does not, so restore what the device has.
        listOf(view1, view2).forEach { it.isFocusable = true; it.isFocusableInTouchMode = true }
        field = View(navActivity).apply { isFocusable = true; isFocusableInTouchMode = true }

        // The panes come first in focus order, the search field after them: exactly the order in
        // which `rootViewRequestFocus` finds window 1's BibleView.
        // Robolectric's WebView keeps a 0x0 frame whatever it is laid out to, and since API 28 a
        // zero-sized view cannot take focus. Let it, as the device's laid-out panes can.
        canFocusZeroSizedBefore = canFocusZeroSized.getBoolean(null)
        canFocusZeroSized.setBoolean(null, true)
        container = Robolectric.buildActivity(Activity::class.java).setup()
        container.get().setContentView(LinearLayout(container.get()).apply {
            orientation = LinearLayout.VERTICAL
            addView(view1, LinearLayout.LayoutParams(400, 300))
            addView(view2, LinearLayout.LayoutParams(400, 300))
            addView(field, LinearLayout.LayoutParams(400, 50))
        })
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After fun cleanup() {
        if (::container.isInitialized) {
            (container.get().findViewById<View>(android.R.id.content) as? ViewGroup)?.removeAllViews()
            container.pause().stop().destroy()
        }
        if (::factory.isInitialized) factory.clear()
        if (::navActivity.isInitialized) {
            navActivity.lifecycleScope.cancel()
            CurrentActivityHolder.deactivate(navActivity)
        }
        if (::repository.isInitialized) DatabaseResetter.resetDatabase(repository.scope)
        canFocusZeroSizedBefore?.let { canFocusZeroSized.setBoolean(null, it) }
    }

    private fun setTouchMode(inTouchMode: Boolean) {
        // Robolectric's window manager does not report touch mode back to the view root, so set
        // the flag every view of this window reads (View.isInTouchMode -> AttachInfo.mInTouchMode).
        val attachInfo = View::class.java.getDeclaredField("mAttachInfo").apply { isAccessible = true }.get(view1)
        attachInfo.javaClass.getDeclaredField("mInTouchMode").apply { isAccessible = true }.setBoolean(attachInfo, inTouchMode)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("precondition: touch mode", inTouchMode, view1.isInTouchMode)
    }

    private fun makeActive(window: Window) {
        repository.activeWindow = window
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("precondition: active window", window.id, repository.activeWindow.id)
    }

    @Test fun releasingFocusOutOfTouchModeKeepsTheActiveWindowAndItsFocus() {
        setTouchMode(false)
        makeActive(window2)
        assertTrue("precondition: the search field takes focus", field.requestFocus())

        field.clearFocus()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a focus hand-over must not change the active window",
            window2.id, repository.activeWindow.id)
        assertTrue("focus goes back to the active pane, not to window 1", view2.hasFocus())
        assertFalse(view1.hasFocus())
    }

    @Test fun keyboardNavigationOutOfTouchModeStillChangesTheActiveWindow() {
        setTouchMode(false)
        makeActive(window2)
        view2.requestFocus()
        assertTrue("precondition: the active pane holds focus", view2.isFocused)

        // What ViewRootImpl's focus navigation does for D-pad up / Shift+Tab: the next view gets
        // the direction and the previously focused view's rect.
        val rect = Rect().also { view2.getFocusedRect(it) }
        assertTrue(view1.requestFocus(View.FOCUS_UP, rect))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(window1.id, repository.activeWindow.id)
        assertTrue(view1.isFocused)
    }

    @Test fun focusGainInTouchModeStillChangesTheActiveWindow() {
        setTouchMode(true)
        makeActive(window2)
        field.requestFocus()
        field.clearFocus() // in touch mode Android does not refocus: nothing is focused now
        shadowOf(Looper.getMainLooper()).idle()

        // e.g. an accessibility action focusing window 1's WebView
        assertTrue(view1.requestFocus())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(window1.id, repository.activeWindow.id)
    }
}
