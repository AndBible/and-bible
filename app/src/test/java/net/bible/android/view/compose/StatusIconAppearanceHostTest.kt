package net.bible.android.view.compose

import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.applySystemBarColor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * F71's verifiable half. sdk 29, where `decorView.systemUiVisibility` is the field both writers
 * target:
 *  1. The race: `showSystemUI` assigned the whole field and CLEARED the LIGHT_STATUS_BAR bit the
 *     toolbar's `applySystemBarColor` had just set, so a light toolbar got white icons.
 *  2. Fullscreen: the toolbar leaves composition, the page is under the transient bar, and the
 *     icons must follow the page, not the last toolbar colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [29])
class StatusIconAppearanceHostTest {
    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    private fun host(route: String): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
        ).also { hostControllers += it }.setup().get()
    }

    @After fun tearDown() { hostControllers.forEach { it.close() }; hostControllers.clear() }

    private fun lightStatus(a: NavHostComposeActivity) =
        a.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0

    @Test fun aLightToolbarKeepsDarkIconsAfterTheIdleSystemUiPass() {
        val a = host(NavRoutes.READING)
        applySystemBarColor(a, Color.White, fillWindowBackground = false)
        a.applyIdleSystemUi()
        assertTrue("the idle pass must not clear the light-status request (API 23-29 race)", lightStatus(a))
        // Discriminating: the flag bit alone also holds when the toolbar writes it directly (5a already
        // removed the race). Only the 5b seam makes the HOST's policy decide the icons.
        assertEquals("the host policy decided the icons from the reported toolbar colour", true, a.lastAppliedSystemBars?.lightStatusIcons)
    }

    @Test fun inFullScreenTheIconsFollowTheLightPageNotTheDarkToolbar() {
        val a = host(NavRoutes.READING)
        applySystemBarColor(a, Color.Black, fillWindowBackground = false) // dark toolbar
        a.fullScreen = true
        a.applyIdleSystemUi()
        assertTrue(
            "fullscreen: the default day page background is light, so the transient bar needs dark icons",
            lightStatus(a),
        )
    }
}
