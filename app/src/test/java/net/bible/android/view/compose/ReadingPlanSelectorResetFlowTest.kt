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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.readingplan.nav.LoadedReadingDay
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

    private class Fixture(val controller: ActivityController<NavHostComposeActivity>, val planName: String)

    private fun nav(activity: NavHostComposeActivity) = NavHostComposeActivity::class.java.getDeclaredField("navController")
        .apply { isAccessible = true }.get(activity) as NavHostController

    private fun loadedDayPlan(activity: NavHostComposeActivity): String? {
        val flow = NavHostComposeActivity::class.java.getDeclaredField("loadedReadingDay")
            .apply { isAccessible = true }.get(activity) as StateFlow<*>
        return (flow.value as LoadedReadingDay?)?.planCode
    }

    /** Daily reading on PLAN, selector on top, PLAN reset there (long-press, Yes). */
    private fun resetFromSelector(): Fixture {
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

        nav(activity).navigate(NavRoutes.READING_PLAN_SELECTOR)
        idle()
        val planName = runBlocking {
            GlobalContext.get().get<ReadingPlanControl>().readingPlanList().first { it.planCode == PLAN }.planName!!
        }
        compose.onNodeWithText(planName).performTouchInput { longClick() }
        idle()
        compose.onNodeWithText("Yes").performClick()
        idle()
        assertEquals("fixture: the reset removed the selected plan", "", readingPlanSetting())
        return Fixture(controller, planName)
    }

    @Test fun selectorResetOfTheLoadedPlanDoesNotReSelectItOnBack() {
        val (controller, planName) = resetFromSelector().let { it.controller to it.planName }
        val activity = controller.get()

        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals("Back onto the daily reading must not re-select the reset plan", "", readingPlanSetting())
        assertEquals(
            "one Back leaves the selector and the plan-less day view for the reader",
            NavRoutes.READING, activity.currentRouteForTest()?.substringBefore('?'),
        )

        // choosing the reset plan again is the set's only exit: the day view must load it AND stay on it
        // reopening Reading Plan: the plan-less day view offers the selector
        nav(activity).navigate(NavRoutes.dailyReading())
        idle()
        net.bible.test.awaitHostRoute(activity, NavRoutes.READING_PLAN_SELECTOR) { compose.waitForIdle() }
        compose.onNodeWithText(planName).performClick()
        idle()
        net.bible.test.awaitHostRoute(activity, NavRoutes.dailyReading()) { compose.waitForIdle() }
        net.bible.test.awaitHostUntil("the re-chosen plan is loaded in the day view") { loadedDayPlan(activity) == PLAN }
        idle()
        idle()
        assertEquals(
            "the re-chosen plan must stay on the day view, not bounce to the selector",
            NavRoutes.dailyReading().substringBefore('?'), activity.currentRouteForTest()?.substringBefore('?'),
        )
        assertEquals(PLAN, loadedDayPlan(activity))
        assertEquals(PLAN, readingPlanSetting())
    }

    /** A recreated host (uiMode, font scale, multi-window, process death) restores the back stack but not memory. */
    @Test fun aRecreatedHostStillDoesNotReSelectTheResetPlanOnBack() {
        val fixture = resetFromSelector()
        fixture.controller.recreate()
        idle()
        val activity = fixture.controller.get()
        net.bible.test.awaitHostRoute(activity, NavRoutes.READING_PLAN_SELECTOR) { compose.waitForIdle() }

        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        idle()
        idle()
        assertEquals("a recreated host must not re-select the reset plan", "", readingPlanSetting())
    }

    private companion object {
        const val PLAN = "y1ot1nt1_OTandNT"
    }
}
