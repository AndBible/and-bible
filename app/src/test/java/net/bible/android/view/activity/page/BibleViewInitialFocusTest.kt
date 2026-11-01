package net.bible.android.view.activity.page

import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.fakes.RoboWebSettings
import org.robolectric.shadows.ShadowWebView
import java.lang.ref.WeakReference

/**
 * osTicket 3496: opening search in one window scrolled a StudyPad in ANOTHER window back to its top.
 *
 * Out of touch mode (a hardware keyboard or a mouse, e.g. Waydroid on a desktop), Android hands the
 * view focus to the first focusable view whenever the focused one lets go of it (the search field's
 * release, the search toolbar going away, a pane's WebView clearing its focus). That is often the
 * first window's WebView. `WebView.requestFocus(direction, rect)` then, with
 * `WebSettings.needInitialFocus` at its default `true`, makes Chromium focus the document's first
 * focusable node and scroll it into view: a StudyPad (links and editors near its top) jumps to the
 * top; a Bible chapter, with no focusable node, seemed unaffected. The reading view never wants
 * that: its pages manage their own scroll position, and gaining view focus must not move them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, shadows = [BibleViewInitialFocusTest.ShadowWebViewWithPlatformDefaults::class])
class BibleViewInitialFocusTest {
    /**
     * Robolectric's [RoboWebSettings] starts `needInitialFocus` at `false`, but the platform WebView
     * starts it at `true` (see `WebSettings.setNeedInitialFocus`). Without the platform default the
     * assertion below would pass whether or not BibleView turns the setting off.
     */
    @Implements(WebView::class)
    class ShadowWebViewWithPlatformDefaults : ShadowWebView() {
        private val platformDefaults = RoboWebSettings().apply { setNeedInitialFocus(true) }

        @Implementation
        override fun getSettings(): WebSettings = platformDefaults
    }

    private fun newBibleView(): BibleView {
        val host = mock(ReadingHostActivity::class.java)
        `when`(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
        val callbacks = BibleViewHostCallbacks(
            hostActivity = mock(ActivityBase::class.java), onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { CommonUtils.realSharedPreferences.getBoolean("night_mode_pref", false) },
            imeHeight = { 0 }, topOffset2 = { 0 }, bottomOffsetForWebView = { 0 }, insetsChanges = { error("Unused") })
        val koin = GlobalContext.get()
        val repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        val window = Window(WorkspaceEntities.Window(
            workspaceId = IdType(), isSynchronized = false, isPinMode = false,
            windowLayout = WorkspaceEntities.WindowLayout("VISIBLE")), koin.get<CurrentPageManager>(), repo)
        return BibleView(host, callbacks, WeakReference(window),
            koin.get(), koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get()).also { view ->
            // `initialise()` builds it; destroy() needs it.
            BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
                isAccessible = true
                set(view, PageTiltScroller(view, PageTiltScrollControl()))
            }
        }
    }

    @Test
    fun gainingViewFocusDoesNotFocusAndScrollToTheFirstNodeOfThePage() {
        val view = newBibleView()
        try {
            assertFalse(
                "BibleView must turn off WebSettings.needInitialFocus, or a focus hand-over out of " +
                    "touch mode scrolls the page to its first focusable node (osTicket 3496)",
                (view.settings as RoboWebSettings).needInitialFocus,
            )
        } finally {
            view.window.destroy(destroyView = false)
            view.destroy()
        }
    }
}
