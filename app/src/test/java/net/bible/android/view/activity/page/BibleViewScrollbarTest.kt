package net.bible.android.view.activity.page

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
@Config(application = TestBibleApplication::class, sdk = [23, 28, 29, 35])
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

    private fun pixels(drawable: Drawable?): IntArray {
        val bitmap = Bitmap.createBitmap(16, 64, Bitmap.Config.ARGB_8888)
        drawable?.setBounds(0, 0, 16, 64)
        drawable?.draw(Canvas(bitmap))
        return IntArray(16 * 64).also {
            bitmap.getPixels(it, 0, 16, 0, 0, 16, 64)
            bitmap.recycle()
        }
    }

    private fun pixel(drawable: Drawable): Int = pixels(drawable)[32 * 16 + 8]

    private inline fun withView(block: (BibleView) -> Unit) {
        val settings = CommonUtils.settings
        val mode = settings.getString("display_color_mode", null)
        val prefs = CommonUtils.realSharedPreferences
        val night = prefs.getBoolean("night_mode_pref", false)
        settings.setString("display_color_mode", DisplayColorMode.NORMAL.value)
        prefs.edit().putBoolean("night_mode_pref", false).commit()
        val view = view()
        try { block(view) }
        finally {
            view.window.destroy(destroyView = false)
            settings.setString("display_color_mode", mode)
            prefs.edit().putBoolean("night_mode_pref", night).commit()
        }
    }

    @Test fun constructorMonoAndWithinMonoNightChangePreserveCustomFlagsOnExit() = withView { normal ->
        normal.isVerticalScrollBarEnabled = false
        normal.isHorizontalScrollBarEnabled = true
        normal.isScrollbarFadingEnabled = false
        CommonUtils.settings.setString("display_color_mode", DisplayColorMode.MONOCHROME.value)
        val constructed = view()
        try {
            for (dark in listOf(false, true)) {
                CommonUtils.realSharedPreferences.edit().putBoolean("night_mode_pref", dark).commit()
                normal.updateBackgroundColor()
                constructed.updateBackgroundColor()
                for (candidate in listOf(normal, constructed)) {
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        assertEquals(if (dark) Color.WHITE else Color.BLACK, pixel(part(candidate, "mVerticalThumb")!!))
                        assertEquals(if (dark) Color.BLACK else Color.WHITE, pixel(part(candidate, "mVerticalTrack")!!))
                    } else {
                        assertFalse(candidate.isVerticalScrollBarEnabled)
                        assertFalse(candidate.isHorizontalScrollBarEnabled)
                    }
                }
            }
            CommonUtils.settings.setString("display_color_mode", DisplayColorMode.NORMAL.value)
            normal.updateBackgroundColor()
            assertFalse(normal.isVerticalScrollBarEnabled)
            assertTrue(normal.isHorizontalScrollBarEnabled)
            assertFalse(normal.isScrollbarFadingEnabled)
        } finally { constructed.window.destroy(destroyView = false) }
    }

    @Test @Config(sdk = [29, 35])
    fun nullTracksKeepEffectiveThumbDimensionsInMono() = withView { view ->
        fun sized(width: Int, height: Int) = object : android.graphics.drawable.ColorDrawable(Color.RED) {
            override fun getIntrinsicWidth() = width
            override fun getIntrinsicHeight() = height
        }
        view.scrollBarSize = 4
        view.verticalScrollbarThumbDrawable = sized(13, 17)
        view.horizontalScrollbarThumbDrawable = sized(19, 11)
        view.verticalScrollbarTrackDrawable = null
        view.horizontalScrollbarTrackDrawable = null
        val height = View::class.java.getDeclaredMethod("getHorizontalScrollbarHeight").apply { isAccessible = true }
        assertEquals(13, view.verticalScrollbarWidth)
        assertEquals(11, height.invoke(view))
        CommonUtils.settings.setString("display_color_mode", DisplayColorMode.MONOCHROME.value)
        view.updateBackgroundColor()
        assertEquals(13, view.verticalScrollbarWidth)
        assertEquals(11, height.invoke(view))
        CommonUtils.settings.setString("display_color_mode", DisplayColorMode.NORMAL.value)
        view.updateBackgroundColor()
        assertEquals(13, view.verticalScrollbarWidth)
        assertEquals(11, height.invoke(view))
        assertNull(view.verticalScrollbarTrackDrawable)
        assertNull(view.horizontalScrollbarTrackDrawable)
        // A PRESENT track with non-positive dimensions selects the configured fallback,
        // not the thumb's positive dimensions.
        view.verticalScrollbarTrackDrawable = sized(-1, -1)
        view.horizontalScrollbarTrackDrawable = sized(-1, -1)
        assertEquals(4, view.verticalScrollbarWidth)
        assertEquals(4, height.invoke(view))
        CommonUtils.settings.setString("display_color_mode", DisplayColorMode.MONOCHROME.value)
        view.updateBackgroundColor()
        assertEquals(4, view.verticalScrollbarWidth)
        assertEquals(4, height.invoke(view))
    }

    @Test @Config(sdk = [29, 35])
    fun queuedNativeFadeCannotGrayOrHideMonoScrollbar() = withView { view ->
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        view.setBibleJavascriptInterface(BibleJavascriptInterface(view))
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, net.bible.android.view.activity.page.screen.PageTiltScroller(view, PageTiltScrollControl()))
        }
        activity.setContentView(view)
        view.layout(0, 0, 100, 100)
        // Robolectric's WebView provider has no document layout. Supply ONLY scroll metrics;
        // View's real cache callback, scrollbar geometry and native paint remain untouched.
        val providerField = WebView::class.java.getDeclaredField("mProvider").apply { isAccessible = true }
        val provider = providerField.get(view)
        val providerType = Class.forName("android.webkit.WebViewProvider")
        val scrollType = Class.forName("android.webkit.WebViewProvider\$ScrollDelegate")
        val originalScroll = providerType.getMethod("getScrollDelegate").invoke(provider)
        val scroll = java.lang.reflect.Proxy.newProxyInstance(scrollType.classLoader, arrayOf(scrollType)) { _, method, args ->
            when (method.name) {
                "computeVerticalScrollRange" -> 1000
                "computeVerticalScrollExtent" -> 100
                "computeVerticalScrollOffset" -> 200
                else -> method.invoke(originalScroll, *(args ?: emptyArray()))
            }
        }
        val viewType = Class.forName("android.webkit.WebViewProvider\$ViewDelegate")
        val originalView = providerType.getMethod("getViewDelegate").invoke(provider)
        val viewDelegate = java.lang.reflect.Proxy.newProxyInstance(viewType.classLoader, arrayOf(viewType)) { _, method, args ->
            if (method.name == "onDrawVerticalScrollBar") {
                // The fake provider normally swallows this callback. Real WebView providers
                // forward to View via PrivateAccess; make that identical super call here.
                org.robolectric.shadow.api.Shadow.directlyOn<Any?, View>(view, View::class.java, "onDrawVerticalScrollBar",
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
        view.scrollBarDefaultDelayBeforeFade = 20
        view.scrollBarFadeDuration = 40
        try {
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
                        assertEquals("Native thumb remains ink after queued fade", Color.BLACK, bitmap.getPixel(98, 24))
                        assertEquals("Native track remains paper", Color.WHITE, bitmap.getPixel(98, 80))
                        assertEquals("Native cache visible after draw", 1, state.getInt(cache))
                    } finally { bitmap.recycle() }
                }
                for (name in listOf("mVerticalThumb", "mVerticalTrack", "mHorizontalThumb", "mHorizontalTrack")) {
                    val drawable = part(view, name)!!
                    drawable.alpha = 75
                    assertEquals("Mono part rejects fractional alpha", 255, drawable.alpha)
                }
            }
        } finally {
            providerField.set(view, provider)
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            activity.finish()
        }
    }

    @Test fun nativeThumbAndTrackUseInkAndPaperAndRestoreOriginals() {
        val prefs = CommonUtils.realSharedPreferences
        val savedMode = CommonUtils.settings.getString("display_color_mode", null)
        val savedNight = prefs.getBoolean("night_mode_pref", false)
        try {
            CommonUtils.settings.setString("display_color_mode", DisplayColorMode.NORMAL.value)
            val view = view()
            // Restore window event subscriptions even if a palette assertion fails.
            try {
                val names = listOf("mVerticalThumb", "mVerticalTrack", "mHorizontalThumb", "mHorizontalTrack")
                val legacy = WebView(RuntimeEnvironment.getApplication())
                val originals = names.associateWith { part(legacy, it) }
                val actualOriginals = names.associateWith { part(view, it) }
                for (name in names) assertArrayEquals("Initial legacy $name", pixels(originals[name]), pixels(actualOriginals[name]))
                assertEquals(legacy.verticalScrollbarWidth, view.verticalScrollbarWidth)
                val geometry = listOf(view.scrollBarSize, view.scrollBarStyle, view.scrollBarFadeDuration,
                    view.scrollBarDefaultDelayBeforeFade, view.verticalScrollbarPosition)
                for (dark in listOf(false, true)) {
                    prefs.edit().putBoolean("night_mode_pref", dark).commit()
                    CommonUtils.settings.setString("display_color_mode", DisplayColorMode.MONOCHROME.value)
                    view.updateBackgroundColor()
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        assertFalse(view.isScrollbarFadingEnabled)
                        for (name in names) {
                            val expected = if (name.endsWith("Thumb") != dark) Color.BLACK else Color.WHITE
                            assertEquals("$name dark=$dark", expected, pixel(part(view, name)!!))
                        }
                    } else {
                        assertFalse(view.isVerticalScrollBarEnabled)
                        assertFalse(view.isHorizontalScrollBarEnabled)
                    }
                    assertEquals(geometry, listOf(view.scrollBarSize, view.scrollBarStyle, view.scrollBarFadeDuration,
                        view.scrollBarDefaultDelayBeforeFade, view.verticalScrollbarPosition))
                    for (mode in listOf(DisplayColorMode.NORMAL, DisplayColorMode.BW, DisplayColorMode.COLOR_EINK)) {
                        // Re-enter MONO before EACH legacy mode, not just the first one.
                        CommonUtils.settings.setString("display_color_mode", DisplayColorMode.MONOCHROME.value)
                        view.updateBackgroundColor()
                        CommonUtils.settings.setString("display_color_mode", mode.value)
                        view.updateBackgroundColor()
                        assertEquals(legacy.isScrollbarFadingEnabled, view.isScrollbarFadingEnabled)
                        assertEquals(legacy.isVerticalScrollBarEnabled, view.isVerticalScrollBarEnabled)
                        assertEquals(legacy.isHorizontalScrollBarEnabled, view.isHorizontalScrollBarEnabled)
                        for (name in names) {
                            val baseline = originals[name]
                            val actual = part(view, name)
                            assertSame("Original object restored $name", actualOriginals[name], actual)
                            if (actual == null) assertNull(baseline)
                            else {
                                assertArrayEquals("Restored $name in $mode", pixels(baseline), pixels(actual))
                                assertEquals(baseline?.intrinsicWidth ?: -1, actual.intrinsicWidth)
                                assertEquals(baseline?.intrinsicHeight ?: -1, actual.intrinsicHeight)
                            }
                        }
                    }
                }
            } finally {
                view.window.destroy(destroyView = false)
            }
        } finally {
            CommonUtils.settings.setString("display_color_mode", savedMode)
            prefs.edit().putBoolean("night_mode_pref", savedNight).commit()
        }
    }
}
