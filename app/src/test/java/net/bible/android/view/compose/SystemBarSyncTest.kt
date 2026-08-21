package net.bible.android.view.compose

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
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

    /** Stand-in for `HistoryComposeActivity`'s window theme (`Theme.AbComposeDialog`, parent
     *  `Theme.AppCompat...Dialog.Alert` — floating), for Minor 7's coverage. Same idiom
     *  [ComposeHostActionBarTest] uses for its themed probes: `setTheme` runs BEFORE
     *  `super.onCreate`, so AppCompat's delegate reads it exactly as it would read a manifest
     *  `android:theme` — unlike calling `setTheme` after the Activity already exists, which is too
     *  late for `PhoneWindow.generateLayout` (where `windowIsFloating` is read once and cached). */
    private class FloatingDialogProbeActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog_Alert)
            super.onCreate(savedInstanceState)
        }
    }

    private fun floatingActivity(): AppCompatActivity =
        Robolectric.buildActivity(FloatingDialogProbeActivity::class.java).create().get()

    @Test
    fun setsStatusBarColorToTheContainer() {
        val a = activity()
        val green = Color(0xFF1B5E20)
        applySystemBarColor(a, green, fillWindowBackground = false)
        @Suppress("DEPRECATION")
        assertEquals(green.toArgb(), a.window.statusBarColor)
    }

    // A/B batch 3 review fix (Minor 1): names below state what `isAppearanceLightStatusBars` means
    // (a light status-bar BACKGROUND, hence DARK icons) rather than reading like the F1 bug ("light
    // container wants light icons"). Assertions/expected values are unchanged.
    @Test
    fun aDarkContainerAsksForALightStatusBarBackgroundFalse() {
        val a = activity()
        applySystemBarColor(a, Color(0xFF1B5E20), fillWindowBackground = false)
        val controller = WindowInsetsControllerCompat(a.window, a.window.decorView)
        assertEquals(false, controller.isAppearanceLightStatusBars)
    }

    @Test
    fun aLightContainerAsksForALightStatusBarBackgroundTrue() {
        val a = activity()
        applySystemBarColor(a, Color(0xFFFFFBFE), fillWindowBackground = false)
        val controller = WindowInsetsControllerCompat(a.window, a.window.decorView)
        assertEquals(true, controller.isAppearanceLightStatusBars)
    }

    // Round 12b fix round 1 (Finding 1): same idiom, for the NAVIGATION-bar write's
    // `fillWindowBackground` gate. Named/structured to mirror the three status-bar tests above.
    @Test
    fun aDarkContainerWithFillWindowBackgroundAsksForALightNavigationBarBackgroundFalse() {
        val a = activity()
        applySystemBarColor(a, Color(0xFF1B5E20), fillWindowBackground = true)
        val controller = WindowInsetsControllerCompat(a.window, a.window.decorView)
        assertEquals(false, controller.isAppearanceLightNavigationBars)
    }

    @Test
    fun aLightContainerWithFillWindowBackgroundAsksForALightNavigationBarBackgroundTrue() {
        val a = activity()
        applySystemBarColor(a, Color(0xFFFFFBFE), fillWindowBackground = true)
        val controller = WindowInsetsControllerCompat(a.window, a.window.decorView)
        assertEquals(true, controller.isAppearanceLightNavigationBars)
    }

    @Test
    fun withoutFillWindowBackgroundTheNavigationBarIconAppearanceIsNotWritten() {
        val a = activity()
        val controller = WindowInsetsControllerCompat(a.window, a.window.decorView)
        // Establish a KNOWN state first (fillWindowBackground = true, dark container -> false), so
        // the "unchanged" assertion below is not merely a coincidental match with whatever
        // Robolectric's untouched default happens to be.
        applySystemBarColor(a, Color(0xFF1B5E20), fillWindowBackground = true)
        assertEquals(false, controller.isAppearanceLightNavigationBars)
        // Now call again with fillWindowBackground = false and a LIGHT container — a wrongly-ungated
        // write would flip this to `true`. NOTE: this proves "left at its pre-call value", not "the
        // property was never touched" — a Robolectric shadow boolean cannot distinguish "never
        // written" from "written back to the same value", so this is the strongest assertion this
        // harness can express for "not written".
        applySystemBarColor(a, Color(0xFFFFFBFE), fillWindowBackground = false)
        assertEquals(false, controller.isAppearanceLightNavigationBars)
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

    // A/B batch 3 review fix (Minor 7): a floating window (HistoryComposeActivity's dialog theme)
    // does not own the real status bar. `statusBarColor` is already ignored by the platform there,
    // but `isAppearanceLightStatusBars` is NOT — it would otherwise leak icon-contrast changes onto
    // whichever Activity is really showing the status bar, with nothing restoring it on dismiss.
    // These four use `TestBibleApplication` (method-level, overriding the class default) because
    // `FloatingDialogProbeActivity` extends the real `ActivityBase`, whose `onCreate` reaches into
    // Koin (`historyTraversalFactory`) unconditionally, even with `doNotInitializeApp = true`.

    @Test
    @Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
    fun aFloatingWindowIsDetectedAsFloating() {
        val a = floatingActivity()
        assertTrue(a.window.isFloating)
    }

    @Test
    @Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
    fun aFloatingWindowDoesNotWriteStatusBarColor() {
        val a = floatingActivity()
        @Suppress("DEPRECATION")
        val before = a.window.statusBarColor
        applySystemBarColor(a, Color(0xFF1B5E20), fillWindowBackground = false)
        @Suppress("DEPRECATION")
        assertEquals(before, a.window.statusBarColor)
    }

    @Test
    @Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
    fun aFloatingWindowDoesNotWriteStatusBarIconAppearance() {
        val a = floatingActivity()
        val controller = WindowInsetsControllerCompat(a.window, a.window.decorView)
        val before = controller.isAppearanceLightStatusBars
        // A/B batch 3 review fix (re-review): a LIGHT container is required to discriminate — it
        // maps to `true` (see aLightContainerAsksForALightStatusBarBackgroundTrue), which differs
        // from Robolectric's default `false`. A dark container maps to `false` too, so it would leave
        // the assertion passing whether or not the floating guard actually ran, proving nothing.
        applySystemBarColor(a, Color(0xFFFFFBFE), fillWindowBackground = false)
        assertEquals(before, controller.isAppearanceLightStatusBars)
    }

    // Round 12b fix round 1 (Finding 1): one-liner extension of the test above — the
    // navigation-bar write sits inside the SAME outer `if (!floating)` guard as the status-bar
    // write, so `fillWindowBackground = true` (which WOULD write for a non-floating window) must
    // still be skipped entirely here.
    @Test
    @Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
    fun aFloatingWindowDoesNotWriteNavigationBarIconAppearance() {
        val a = floatingActivity()
        val controller = WindowInsetsControllerCompat(a.window, a.window.decorView)
        val before = controller.isAppearanceLightNavigationBars
        applySystemBarColor(a, Color(0xFFFFFBFE), fillWindowBackground = true)
        assertEquals(before, controller.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
    fun aFloatingWindowStillPaintsItsOwnContentRootWhenAsked() {
        val a = floatingActivity()
        val green = Color(0xFF1B5E20)
        applySystemBarColor(a, green, fillWindowBackground = true)
        val root = a.findViewById<ViewGroup>(android.R.id.content)
        val bg = root.background
        assertTrue("expected a ColorDrawable, got $bg", bg is ColorDrawable)
        assertEquals(green.toArgb(), (bg as ColorDrawable).color)
    }
}
