package net.bible.android.view.compose

import android.os.Looper
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.nav.WindowMode
import net.bible.sharedcore.nav.NavRoutes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * F68. On API 30–34 the reading destination ran ADJUST_NOTHING while the window kept
 * decorFitsSystemWindows = true, so the decor consumed the IME inset and nothing lifted the editor.
 * Reading now runs edge-to-edge (the configuration API 35 already proved on a device), every other
 * destination keeps decor-fits + adjustResize. `setDecorFitsSystemWindows` is not observable under
 * Robolectric (HostInsetOwnershipTest's kdoc), so the host's recorded mode is asserted; the platform
 * half is an emulator re-check (spec §4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class ReadingWindowModeHostTest {
    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    private fun host(route: String): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
        ).also { hostControllers += it }.setup().get()
    }

    @After fun tearDown() { hostControllers.forEach { it.close() }; hostControllers.clear() }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun adjust(a: NavHostComposeActivity) =
        a.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

    private val edgeToEdgeReading = WindowMode(false, WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    private val decorFitsOther = WindowMode(true, WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    @Config(sdk = [30])
    @Test fun readingIsEdgeToEdgeAndARoundTripRestoresIt() {
        val a = host(NavRoutes.READING)
        assertEquals("reading start", edgeToEdgeReading, a.lastAppliedWindowMode)

        a.navigateInGraph(NavRoutes.AI_TOOL_INFO); idle()
        assertEquals("leaving reading restores decor-fits", decorFitsOther, a.lastAppliedWindowMode)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, adjust(a))

        a.onBackPressedDispatcher.onBackPressed(); idle()
        assertEquals("returning to reading re-applies edge-to-edge", edgeToEdgeReading, a.lastAppliedWindowMode)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, adjust(a))
    }

    // Review Focus 3.
    @Config(sdk = [30])
    @Test fun aNonReadingStartKeepsDecorFits() {
        val a = host(NavRoutes.AI_TOOL_INFO)
        assertEquals(decorFitsOther, a.lastAppliedWindowMode)
    }

    @Config(sdk = [28])
    @Test fun belowApi30NothingIsApplied() {
        val a = host(NavRoutes.READING)
        assertEquals(null, a.lastAppliedWindowMode)
    }
}
