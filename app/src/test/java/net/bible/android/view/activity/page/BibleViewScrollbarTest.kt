package net.bible.android.view.activity.page

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.webkit.WebView
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.service.common.CommonUtils
import net.bible.service.common.DisplayColorMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [23, 28, 29, 32, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BibleViewScrollbarTest {
    private fun view(): BibleView {
        val host = mock(ReadingHostActivity::class.java)
        `when`(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
        val callbacks = BibleViewHostCallbacks(
            hostActivity = mock(net.bible.android.view.activity.base.ActivityBase::class.java), onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { CommonUtils.realSharedPreferences.getBoolean("night_mode_pref", false) },
            imeHeight = { 0 }, topOffset2 = { 0 }, bottomOffsetForWebView = { 0 }, insetsChanges = { error("Unused") })
        val koin = org.koin.core.context.GlobalContext.get()
        val repo = net.bible.android.control.page.window.WindowRepository(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main))
        val manager = koin.get<net.bible.android.control.page.CurrentPageManager>()
        val window = Window(net.bible.android.database.WorkspaceEntities.Window(
            workspaceId = net.bible.android.database.IdType(), isSynchronized = false, isPinMode = false,
            windowLayout = net.bible.android.database.WorkspaceEntities.WindowLayout("VISIBLE")),
            manager, repo)
        return BibleView(host, callbacks, WeakReference(window),
            koin.get(), koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get())
    }

    // Inspect the actual native ScrollBarDrawable, not a parallel palette resolver.
    private fun part(view: View, name: String): Drawable? {
        val cache = View::class.java.getDeclaredField("mScrollCache").apply { isAccessible = true }.get(view)
        val bar = cache.javaClass.getDeclaredField("scrollBar").apply { isAccessible = true }.get(cache)
        return bar.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(bar) as Drawable?
    }

    private inline fun withView(block: (BibleView) -> Unit) {
        val settings = CommonUtils.settings
        val mode = settings.getString("display_color_mode", null)
        val prefs = CommonUtils.realSharedPreferences
        val night = prefs.getBoolean("night_mode_pref", false)
        var view: BibleView? = null
        try {
            settings.setString("display_color_mode", DisplayColorMode.NORMAL.value)
            prefs.edit().putBoolean("night_mode_pref", false).commit()
            view = view()
            block(view)
        } finally {
            try {
                view?.window?.destroy(destroyView = false)
            } finally {
                settings.setString("display_color_mode", mode)
                prefs.edit().putBoolean("night_mode_pref", night).commit()
            }
        }
    }

    @Test fun monoHidesBothBarsAndRestoresOriginalStateInEveryOtherMode() = withView { view ->
        val names = listOf("mVerticalThumb", "mVerticalTrack", "mHorizontalThumb", "mHorizontalTrack")
        val originals = names.associateWith { part(view, it) }
        for (vertical in listOf(false, true)) for (horizontal in listOf(false, true)) {
            for (fading in listOf(false, true)) for (dark in listOf(false, true)) {
                CommonUtils.realSharedPreferences.edit().putBoolean("night_mode_pref", dark).commit()
                view.isVerticalScrollBarEnabled = vertical
                view.isHorizontalScrollBarEnabled = horizontal
                view.isScrollbarFadingEnabled = fading
                val geometry = listOf(view.scrollBarSize, view.scrollBarStyle,
                    view.scrollBarFadeDuration, view.scrollBarDefaultDelayBeforeFade)
                for (mode in listOf(DisplayColorMode.NORMAL, DisplayColorMode.BW, DisplayColorMode.COLOR_EINK)) {
                    CommonUtils.settings.setString("display_color_mode", DisplayColorMode.MONOCHROME.value)
                    view.updateBackgroundColor()
                    // Repeated refreshes must not snapshot the already-hidden flags.
                    view.updateBackgroundColor()
                    assertFalse("MONO vertical on API ${android.os.Build.VERSION.SDK_INT}", view.isVerticalScrollBarEnabled)
                    assertFalse("MONO horizontal on API ${android.os.Build.VERSION.SDK_INT}", view.isHorizontalScrollBarEnabled)
                    assertEquals("MONO does not force persistent painting", fading, view.isScrollbarFadingEnabled)
                    for (name in names) assertSame("Drawable untouched in MONO: $name", originals[name], part(view, name))
                    CommonUtils.settings.setString("display_color_mode", mode.value)
                    view.updateBackgroundColor()
                    assertEquals(vertical, view.isVerticalScrollBarEnabled)
                    assertEquals(horizontal, view.isHorizontalScrollBarEnabled)
                    assertEquals(fading, view.isScrollbarFadingEnabled)
                    assertEquals(geometry, listOf(view.scrollBarSize, view.scrollBarStyle,
                        view.scrollBarFadeDuration, view.scrollBarDefaultDelayBeforeFade))
                    for (name in names) assertSame("Original object in $mode: $name", originals[name], part(view, name))
                }
            }
        }
    }

    @Test fun constructorInMonoHidesBothBarsThroughNightChanges() = withView { normal ->
        CommonUtils.settings.setString("display_color_mode", DisplayColorMode.MONOCHROME.value)
        val mono = view()
        try {
            for (dark in listOf(false, true)) {
                CommonUtils.realSharedPreferences.edit().putBoolean("night_mode_pref", dark).commit()
                mono.updateBackgroundColor()
                assertFalse(mono.isVerticalScrollBarEnabled)
                assertFalse(mono.isHorizontalScrollBarEnabled)
            }
            CommonUtils.settings.setString("display_color_mode", DisplayColorMode.NORMAL.value)
            mono.updateBackgroundColor()
            assertEquals(normal.isVerticalScrollBarEnabled, mono.isVerticalScrollBarEnabled)
            assertEquals(normal.isHorizontalScrollBarEnabled, mono.isHorizontalScrollBarEnabled)
            assertEquals(normal.isScrollbarFadingEnabled, mono.isScrollbarFadingEnabled)
        } finally { mono.window.destroy(destroyView = false) }
    }
    @Test @Config(sdk = [29, 32, 35])
    fun queuedNativeFadeCannotPaintHiddenMonoScrollbar() = withView { view ->
        var activity: android.app.Activity? = null
        var providerField: java.lang.reflect.Field? = null
        var provider: Any? = null
        try {
            activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
            view.setBibleJavascriptInterface(BibleJavascriptInterface(view))
            BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
                isAccessible = true
                set(view, net.bible.android.view.activity.page.screen.PageTiltScroller(view, PageTiltScrollControl()))
            }
            activity.setContentView(view)
            view.layout(0, 0, 100, 100)
            // Robolectric's WebView provider has no document layout. Supply ONLY scroll metrics;
            // View's real cache callback, scrollbar geometry and native paint remain untouched.
            providerField = WebView::class.java.getDeclaredField("mProvider").apply { isAccessible = true }
            provider = providerField.get(view)
            val providerType = Class.forName("android.webkit.WebViewProvider")
            val scrollType = Class.forName("android.webkit.WebViewProvider\$ScrollDelegate")
            val originalScroll = providerType.getMethod("getScrollDelegate").invoke(provider)
            val scroll = java.lang.reflect.Proxy.newProxyInstance(scrollType.classLoader, arrayOf(scrollType)) { _, method, args ->
                when (method.name) {
                    "computeVerticalScrollRange" -> 1000
                    "computeVerticalScrollExtent" -> 100
                    "computeVerticalScrollOffset" -> 200
                    "computeHorizontalScrollRange" -> 1000
                    "computeHorizontalScrollExtent" -> 100
                    "computeHorizontalScrollOffset" -> 200
                    else -> method.invoke(originalScroll, *(args ?: emptyArray()))
                }
            }
            val viewType = Class.forName("android.webkit.WebViewProvider\$ViewDelegate")
            val originalView = providerType.getMethod("getViewDelegate").invoke(provider)
            val viewDelegate = java.lang.reflect.Proxy.newProxyInstance(viewType.classLoader, arrayOf(viewType)) { _, method, args ->
                if (method.name == "onDrawVerticalScrollBar" || method.name == "onDrawHorizontalScrollBar") {
                    // The fake provider normally swallows this callback. Real WebView providers
                    // forward to View via PrivateAccess; make that identical super call here.
                    org.robolectric.shadow.api.Shadow.directlyOn<Any?, View>(view, View::class.java, method.name,
                        org.robolectric.util.ReflectionHelpers.ClassParameter.from(Canvas::class.java, args!![0]),
                        org.robolectric.util.ReflectionHelpers.ClassParameter.from(Drawable::class.java, args[1]),
                        *args.drop(2).map { org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, it) }.toTypedArray())
                } else method.invoke(originalView, *(args ?: emptyArray()))
            }
            providerField.set(view, java.lang.reflect.Proxy.newProxyInstance(providerType.classLoader, arrayOf(providerType)) { _, method, args ->
                when (method.name) {
                    "getScrollDelegate" -> scroll
                    "getViewDelegate" -> viewDelegate
                    else -> method.invoke(provider, *(args ?: emptyArray()))
                }
            })
            val cache = View::class.java.getDeclaredField("mScrollCache").apply { isAccessible = true }.get(view)
            val state = cache.javaClass.getDeclaredField("state").apply { isAccessible = true }
            val awaken = View::class.java.getDeclaredMethod("awakenScrollBars", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            view.isVerticalScrollBarEnabled = true
            view.isHorizontalScrollBarEnabled = true
            view.scrollBarDefaultDelayBeforeFade = 20
            view.scrollBarFadeDuration = 40
            for (alreadyFading in listOf(false, true)) {
                CommonUtils.settings.setString("display_color_mode", DisplayColorMode.NORMAL.value)
                view.updateBackgroundColor()
                assertTrue(awaken.invoke(view, 20, true) as Boolean)
                assertEquals(1, state.getInt(cache)) // ON, with an attached cache runnable queued.
                if (alreadyFading) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(751))
                    assertEquals(2, state.getInt(cache)) // actual queued callback entered FADING
                }
                CommonUtils.settings.setString("display_color_mode", DisplayColorMode.MONOCHROME.value)
                view.updateBackgroundColor()
                for (elapsed in listOf(751L, 20L, 60L)) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(elapsed))
                    org.robolectric.shadow.api.Shadow.directlyOn<Any?, View>(view, View::class.java, "setFrame",
                        *arrayOf(0, 0, 100, 100).map { org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, it) }.toTypedArray())
                    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                    org.robolectric.shadow.api.Shadow.directlyOn<Any?, View>(view, View::class.java, "draw",
                        org.robolectric.util.ReflectionHelpers.ClassParameter.from(Canvas::class.java, Canvas(bitmap)))
                    try {
                        assertFalse(view.isVerticalScrollBarEnabled)
                        assertFalse(view.isHorizontalScrollBarEnabled)
                        // A provider with both scroll ranges overflowing still cannot paint bars.
                        // Read actual native View foreground, including a previously queued fade.
                        for (y in 0 until 100) for (x in 96 until 100) {
                            assertEquals("No vertical native paint ($x,$y)", 0, bitmap.getPixel(x, y))
                        }
                        for (y in 96 until 100) for (x in 0 until 100) {
                            assertEquals("No horizontal native paint ($x,$y)", 0, bitmap.getPixel(x, y))
                        }
                    } finally { bitmap.recycle() }
                }
            }
        } finally {
            try {
                if (providerField != null && provider != null) providerField.set(view, provider)
            } finally {
                try {
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                } finally {
                    activity?.finish()
                }
            }
        }
    }

}
