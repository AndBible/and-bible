package net.bible.android.view.compose

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * E6 RP-RESET: resetting the plan that is on screen from the selector (long-press, Yes) must not leave the
 * host able to re-select it. Driven through the real host and selector UI, so it goes red if the selector's
 * onReset stops forgetting the loaded plan, or if the daily-reading destination reloads the reset plan from its
 * own route arguments when Back pops onto it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingPlanSelectorResetFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After fun tearDown() {
        net.bible.test.closeReadingHostsBeforeReset(controllers)
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitForIdle()
    }

    private fun readingPlanSetting() = CommonUtils.settings.getString("reading_plan", "") ?: ""

    @Test fun selectorResetOfTheLoadedPlanDoesNotReSelectItOnBack() {
        resetComposeUiDispatcher()
        firstTime = false
        CommonUtils.settings.setString("reading_plan", PLAN)
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.setup()
        val activity = controller.get()
        idle()
        controller.newIntent(
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(), NavRoutes.dailyReading(plan = PLAN, day = 5),
            ),
        )
        net.bible.test.awaitHostRoute(activity, NavRoutes.dailyReading(plan = PLAN, day = 5))
        net.bible.test.awaitHostUntil("fixture: the plan day is loaded and selected") { readingPlanSetting() == PLAN }
        idle()

        val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController
        nav.navigate(NavRoutes.READING_PLAN_SELECTOR)
        idle()
        val planName = runBlocking {
            GlobalContext.get().get<ReadingPlanControl>().readingPlanList().first { it.planCode == PLAN }.planName!!
        }
        compose.onNodeWithText(planName).performTouchInput { longClick() }
        idle()
        compose.onNodeWithText("Yes").performClick()
        idle()
        assertEquals("fixture: the reset removed the selected plan", "", readingPlanSetting())

        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals("Back onto the daily reading must not re-select the reset plan", "", readingPlanSetting())
        assertEquals(
            "one Back leaves the selector and the plan-less day view for the reader",
            NavRoutes.READING, activity.currentRouteForTest()?.substringBefore('?'),
        )

        // choosing the reset plan again is the set's only exit: the day view must load it
        nav.navigate(NavRoutes.READING_PLAN_SELECTOR)
        idle()
        net.bible.test.awaitHostRoute(activity, NavRoutes.READING_PLAN_SELECTOR) { compose.waitForIdle() }
        compose.onNodeWithText(planName).performClick()
        idle()
        net.bible.test.awaitHostUntil("the re-chosen plan loads again") { readingPlanSetting() == PLAN }
    }

    private companion object {
        const val PLAN = "y1ot1nt1_OTandNT"
    }
}
