package net.bible.android.view.activity.page

import android.app.Activity
import android.os.Looper
import android.view.ViewGroup
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
import net.bible.service.common.DisplayColorMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [23, 28, 29, 32, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BibleViewJsChannelWiringTest {
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
            view.setBibleJavascriptInterface(BibleJavascriptInterface(view))
            BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
                isAccessible = true
                set(view, PageTiltScroller(view, PageTiltScrollControl()))
            }
        }
    }

    private fun withView(block: (BibleView, Activity) -> Unit) {
        val mode = CommonUtils.settings.getString("display_color_mode", null)
        val prefs = CommonUtils.realSharedPreferences
        val night = prefs.getBoolean("night_mode_pref", false)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        var view: BibleView? = null
        try {
            CommonUtils.settings.setString("display_color_mode", DisplayColorMode.NORMAL.value)
            prefs.edit().putBoolean("night_mode_pref", false).commit()
            view = newBibleView()
            controller.get().setContentView(view)
            block(view, controller.get())
        } finally {
            try {
                view?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                    it.window.destroy(destroyView = false)
                    it.destroy()
                }
                controller.pause().stop().destroy()
            } finally {
                CommonUtils.settings.setString("display_color_mode", mode)
                prefs.edit().putBoolean("night_mode_pref", night).commit()
            }
        }
    }

    @Test fun loadingGateFollowsSetClientReady() = withView { view, _ ->
        assertTrue(view.isAttachedToWindow)
        assertTrue(view.jsChannel.loading)
        assertFalse(view.executeJavascriptOnUiThread("1"))
        assertNull(shadowOf(view).lastEvaluatedJavascript)
        view.setClientReady()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(view.jsChannel.loading)
        assertTrue(view.executeJavascriptOnUiThread("1"))
        assertEquals("1;", shadowOf(view).lastEvaluatedJavascript)
    }

    @Test fun detachedReadinessFlushesWhenReattached() = withView { view, activity ->
        (view.parent as ViewGroup).removeView(view)
        view.setClientReady()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(view.jsChannel.loading)
        assertFalse(view.executeJavascriptOnUiThread("dropped"))
        activity.setContentView(view)
        assertFalse(view.jsChannel.loading)
        assertTrue(view.executeJavascriptOnUiThread("reattached"))
        assertEquals("reattached;", shadowOf(view).lastEvaluatedJavascript)
    }

    @Test fun backgroundCallPostsToTheAttachedWebView() = withView { view, _ ->
        view.setClientReady()
        val before = shadowOf(view).lastEvaluatedJavascript
        var accepted = false
        Thread { accepted = view.executeJavascriptOnUiThread("background") }.apply { start(); join() }
        assertTrue(accepted)
        assertEquals(before, shadowOf(view).lastEvaluatedJavascript)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("background;", shadowOf(view).lastEvaluatedJavascript)
    }
}
