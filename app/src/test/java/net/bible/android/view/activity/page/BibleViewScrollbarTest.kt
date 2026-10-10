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
