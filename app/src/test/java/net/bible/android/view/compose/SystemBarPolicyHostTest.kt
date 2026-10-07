package net.bible.android.view.compose

import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.nav.SystemBarSettingChanges
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** F77 + the fullscreen leak. sdk 29: WindowInsetsControllerCompat writes observable decor flags. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [29])
class SystemBarPolicyHostTest {
    @get:Rule val composeDispatcherReset = net.bible.android.ComposeUiDispatcherResetRule()

    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    private fun host(route: String): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
        ).also { hostControllers += it }.setup().get()
    }

    @After fun tearDown() {
        hostControllers.forEach { it.close() }; hostControllers.clear()
        CommonUtils.settings.setBoolean("hide_status_bar", false)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun flags(a: NavHostComposeActivity) = a.window.decorView.systemUiVisibility
    private fun statusHidden(a: NavHostComposeActivity) = flags(a) and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
    private fun navHidden(a: NavHostComposeActivity) = flags(a) and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0

    // F77: a cold start straight into a non-reading destination honours the setting.
    @Test fun aNonReadingStartHidesTheStatusBarWhenTheSettingIsOn() {
        CommonUtils.settings.setBoolean("hide_status_bar", true)
        val a = host(NavRoutes.AI_TOOL_INFO)
        assertTrue("status bar must be hidden on a non-reading destination (F77)", statusHidden(a))
    }

    // Fullscreen must not leak a hidden NAV bar into other destinations; Review Focus 4: it comes back on return.
    @Test fun fullScreenIsReadingOnlyAndSurvivesARoundTrip() {
        val a = host(NavRoutes.READING)
        a.fullScreen = true
        a.applyIdleSystemUi()
        assertTrue("fullscreen reading hides the nav bar", navHidden(a))

        a.navigateInGraph(NavRoutes.AI_TOOL_INFO); idle()
        assertEquals("the nav bar must show on a non-reading destination", false, navHidden(a))

        // navigateInGraph rather than a back press: the real back press is the sdk-30 test below. (An
        // earlier version of this comment blamed sdk 29; the actual cause of a back press not popping
        // was stale AndroidUiDispatcher flags, which ComposeUiDispatcherResetRule now resets.)
        a.navigateInGraph(NavRoutes.READING); idle()
        assertTrue("returned to reading, fullscreen hides it again", navHidden(a))
    }

    // Review Focus 4, the real return leg: system back from a non-reading destination, fullscreen ON.
    @Config(sdk = [30])
    @Test fun systemBackToFullScreenReadingHidesTheNavBarAgain() {
        val a = host(NavRoutes.READING)
        a.fullScreen = true
        a.applyIdleSystemUi()
        a.navigateInGraph(NavRoutes.AI_TOOL_INFO); idle()
        assertEquals(true, a.lastAppliedSystemBars?.navVisible)
        a.onBackPressedDispatcher.onBackPressed(); idle()
        assertEquals(NavRoutes.READING, a.currentRouteForTest()?.substringBefore('?'))
        assertEquals("back on reading, fullscreen hides the nav bar again", false, a.lastAppliedSystemBars?.navVisible)
    }

    // Review Focus 5: switching the setting OFF while elsewhere shows the bar at once.
    @Test fun turningTheSettingOffAppliesImmediatelyOffReading() {
        CommonUtils.settings.setBoolean("hide_status_bar", true)
        val a = host(NavRoutes.AI_TOOL_INFO)
        assertTrue("precondition: the status bar starts hidden", statusHidden(a))
        CommonUtils.settings.setBoolean("hide_status_bar", false)
        SystemBarSettingChanges.notifyChanged(); idle()
        assertEquals(false, statusHidden(a))
    }
}
