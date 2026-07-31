package net.bible.android.view.compose

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.sharedui.applySystemBarColor
import net.bible.sharedui.findActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SystemBarSyncTest {

    private fun activity(): AppCompatActivity =
        Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

    @Test
    fun setsStatusBarColorToTheContainer() {
        val a = activity()
        val green = Color(0xFF1B5E20)
        applySystemBarColor(a, green, fillWindowBackground = false)
        @Suppress("DEPRECATION")
        assertEquals(green.toArgb(), a.window.statusBarColor)
    }

    @Test
    fun aDarkContainerAsksForLightStatusBarIcons() {
        val a = activity()
        applySystemBarColor(a, Color(0xFF1B5E20), fillWindowBackground = false)
        val controller = WindowInsetsControllerCompat(a.window, a.window.decorView)
        assertEquals(false, controller.isAppearanceLightStatusBars)
    }

    @Test
    fun aLightContainerAsksForDarkStatusBarIcons() {
        val a = activity()
        applySystemBarColor(a, Color(0xFFFFFBFE), fillWindowBackground = false)
        val controller = WindowInsetsControllerCompat(a.window, a.window.decorView)
        assertEquals(true, controller.isAppearanceLightStatusBars)
    }

    @Test
    fun fillWindowBackgroundPaintsTheContentRoot() {
        val a = activity()
        val green = Color(0xFF1B5E20)
        applySystemBarColor(a, green, fillWindowBackground = true)
        val root = a.findViewById<ViewGroup>(android.R.id.content)
        val bg = root.background
        assertTrue("expected a ColorDrawable, got $bg", bg is ColorDrawable)
        assertEquals(green.toArgb(), (bg as ColorDrawable).color)
    }

    @Test
    fun withoutFillWindowBackgroundTheContentRootIsLeftAlone() {
        val a = activity()
        val before = a.findViewById<ViewGroup>(android.R.id.content).background
        applySystemBarColor(a, Color(0xFF1B5E20), fillWindowBackground = false)
        assertEquals(before, a.findViewById<ViewGroup>(android.R.id.content).background)
    }

    @Test
    fun reapplyingTheSameColourDoesNotReplaceTheContentRootDrawable() {
        val a = activity()
        val green = Color(0xFF1B5E20)
        applySystemBarColor(a, green, fillWindowBackground = true)
        val root = a.findViewById<ViewGroup>(android.R.id.content)
        val firstDrawable = root.background
        applySystemBarColor(a, green, fillWindowBackground = true)
        // Identity, not equality: proves the equality guard skipped the write rather than merely
        // that a second, equal ColorDrawable was installed.
        assertSame(firstDrawable, root.background)
    }

    @Test
    fun findActivityReturnsNullForANonActivityContext() {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        assertNull(appContext.findActivity())
    }
}
