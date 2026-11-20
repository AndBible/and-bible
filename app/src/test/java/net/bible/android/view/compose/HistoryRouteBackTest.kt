package net.bible.android.view.compose

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ErrorActivity
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.test.resetComposeUiDispatcher
import net.bible.service.common.CommonUtils
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import net.bible.service.db.DatabaseContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * Spec 2026-10-08 API 36 §3.2: on a destination that publishes a history route, BACK takes the history step
 * BEFORE the NavHost pops, as `ActivityBase.onBackPressed` did. Driven through the dispatcher, the route
 * predictive back takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class HistoryRouteBackTest {
    /**
     * Installs the Compose test environment (recomposer + frame clock) so the host's own `setContent` recomposes in a
     * JVM that already ran another composed test; without it only the first such test in the JVM sees its effects
     * (same trap as `ReadingChooserInGraphResultTest`).
     */
    @get:Rule val compose = createEmptyComposeRule()

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After fun tearDown() {
        net.bible.test.closeReadingHostsBeforeReset(controllers)
    }

    /**
     * Forced interleaving, a regression pin for the earlier settings-gate race (f62a9ec1d8): it is NOT a reproduction
     * of the recurring contended-suite flake (these variants also pass on the old code). A background thread runs a
     * full `DatabaseContainer.aiLifetime.replace` (which cancels/joins owned jobs and bumps the generation) whose
     * block lasts [ms] of real time, so the host's settings gate (`aiSettings.ensureLoaded()` in `setContent`)
     * cannot open sooner. The thread running the replace is a plain daemon thread, never the test/main thread.
     */
    private fun withAiLifetimeHeld(ms: Long, body: () -> Unit) {
        val held = java.util.concurrent.CountDownLatch(1)
        val holder = Thread {
            kotlinx.coroutines.runBlocking {
                DatabaseContainer.aiLifetime.replace { _ ->
                    held.countDown()
                    Thread.sleep(ms) // runs on the holder thread (inside the replace block), not the test thread
                }
            }
        }.apply { isDaemon = true; start() }
        try {
            assertTrue("fixture: lock taken", held.await(5, java.util.concurrent.TimeUnit.SECONDS))
            body()
        } finally {
            holder.join(10_000)
            check(!holder.isAlive) { "aiLifetime holder thread did not finish" }
        }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitForIdle()
    }

    /**
     * Opens daily reading, a live destination that publishes a history route while it is showing, and waits for that
     * route to be published (a LaunchedEffect on the Compose test clock).
     */
    private fun dailyReadingHost(): Pair<NavHostComposeActivity, NavHostController> {
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
        try {
            net.bible.test.awaitHostRoute(activity, NavRoutes.dailyReading(plan = PLAN, day = 5)) { compose.waitForIdle() }
        } catch (failure: AssertionError) {
            val logs = org.robolectric.shadows.ShadowLog.getLogs().takeLast(80)
                .joinToString("\n") { "${it.tag}: ${it.msg} ${it.throwable ?: ""}" }
            throw AssertionError("Daily history fixture route=${activity.currentRouteForTest()}, " +
                "visible=${ReadingViewVisibility.isVisible}\n$logs", failure)
        }
        idle()
        assertEquals("fixture: on daily reading", NavRoutes.dailyReading().substringBefore('?'), activity.currentRouteForTest()?.substringBefore('?'))
        // The graph publishes the route from a LaunchedEffect keyed on the loaded day, after the route is current.
        net.bible.test.awaitHostCondition("daily reading must publish its history route", { compose.waitForIdle() }) {
            activity.isIntegrateWithHistoryManager
        }
        assertTrue("fixture: the destination published its history route", activity.isIntegrateWithHistoryManager)
        val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController
        return activity to nav
    }

    private fun currentRoute(nav: NavHostController) = nav.currentDestination?.route?.substringBefore('?')

    @Test fun aHistoryRouteDestinationStepsHistoryBeforePopping() = stepsHistory()
    @Test fun aHistoryRouteDestinationStepsHistoryBeforePoppingWithSlowSettingsGate() = withAiLifetimeHeld(3_000) { stepsHistory() }
    private fun stepsHistory() {
        val (activity, _) = dailyReadingHost()
        val manager: HistoryManager = GlobalContext.get().get()
        val window = CommonUtils.windowControl.activeWindow
        manager.addHistoryItem(window, Intent(activity, ErrorActivity::class.java).putExtra("description", "probe"))
        // getHistory, not getEntities: the latter lists only KeyHistoryItems and would not see the probe (an
        // IntentHistoryItem); the brief's getEntities fixture read depth 0 before and after.
        val depth = manager.getHistory(window.id).size

        activity.onBackPressedDispatcher.onBackPressed()
        idle()

        assertEquals("BACK must consume one history item first (a bare NavHost pop leaves history untouched)",
            depth - 1, manager.getHistory(window.id).size)
    }

    /** With nothing to step back to, `goBackInHistory` declines and the press must reach the NavHost pop. */
    @Test fun aHistoryRouteDestinationWithEmptyHistoryPassesThroughToThePop() = emptyHistory()
    @Test fun aHistoryRouteDestinationWithEmptyHistoryPassesThroughToThePopWithSlowSettingsGate() = withAiLifetimeHeld(3_000) { emptyHistory() }
    private fun emptyHistory() {
        val (activity, nav) = dailyReadingHost()
        val manager: HistoryManager = GlobalContext.get().get()
        val window = CommonUtils.windowControl.activeWindow
        assertTrue("fixture: history is empty", manager.getHistory(window.id).isEmpty())
        assertEquals("fixture: on daily reading", NavRoutes.dailyReading().substringBefore('?'), currentRoute(nav))

        activity.onBackPressedDispatcher.onBackPressed()
        idle()

        assertEquals(NavRoutes.READING, currentRoute(nav))
    }

    private companion object {
        const val PLAN = "y1ot1nt1_OTandNT"
    }
}
