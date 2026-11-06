package net.bible.test

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.After
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AwaitHostConditionTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After fun closeHosts() = closeReadingHostsBeforeReset(controllers)
    /**
     * Reproduces the contended-machine case: a host effect that completes after 12 s of wall clock while each
     * composition pump is slow. The former fixed 10 s deadline threw here (ReadingPlanSelectorResetFlowTest; 40-50 s is that WHOLE test cold, not the wait).
     */
    @Test fun aConditionThatSettlesAfterTwelveSecondsOfWallClockIsAwaited() {
        val start = System.nanoTime()
        awaitHostCondition("slow condition", advanceComposition = { Thread.sleep(300) }) {
            System.nanoTime() - start > 12_000_000_000L
        }
    }

    @Test fun aTimeoutNamesTheConditionAndTheElapsedTime() {
        try {
            awaitHostCondition("never ready", timeoutMs = 200) { false }
            fail("must time out")
        } catch (e: AssertionError) {
            assertTrue(e.message, e.message!!.contains("never ready"))
            assertTrue(e.message, Regex("after \\d+ ms").containsMatchIn(e.message!!))
        }
    }

    /**
     * #3947: the one SystemBarPolicyHostTest 60 s stall left no evidence of what the host was waiting for. A
     * timeout must carry the stacks of the live threads and the main looper state, so the next stall is attributable.
     */
    @Test fun aTimeoutCarriesTheLiveThreadStacksAndTheMainLooperState() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val blocked = Thread({ gate.await() }, "issue-3947-blocked-actor").apply { isDaemon = true; start() }
        try {
            awaitHostCondition("never ready", timeoutMs = 200) { false }
            fail("must time out")
        } catch (e: AssertionError) {
            val report = e.message!!
            assertTrue(report, report.contains("\"issue-3947-blocked-actor\""))
            assertTrue(report, report.contains("CountDownLatch.await"))
            assertTrue(report, report.contains("Main looper:"))
        } finally {
            gate.countDown(); blocked.join(5_000)
        }
    }

    /**
     * #3947: the stall report had stacks but not which part of the host start was missing. A timeout carries the
     * caller's state description.
     */
    @Test fun aTimeoutCarriesTheCallersStateDescription() {
        try {
            awaitHostCondition("never ready", timeoutMs = 200, describe = { "STATE-3947" }) { false }
            fail("must time out")
        } catch (e: AssertionError) {
            assertTrue(e.message, e.message!!.contains("STATE-3947"))
        }
    }

    /** The host state names the start stage, route and both visibility inputs of a real started host. */
    @Test fun hostStartStateNamesTheStageTheHostReached() {
        firstTime = false
        val a = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.AI_TOOL_INFO),
        ).also { controllers += it }.setup().get()
        awaitHostRoute(a, NavRoutes.AI_TOOL_INFO)
        val state = hostStartState(a)
        assertTrue(state, state.contains("stage=nav controller published"))
        assertTrue(state, state.contains("route=${NavRoutes.AI_TOOL_INFO}"))
        assertTrue(state, state.contains("readingVisible=false"))
        assertTrue(state, state.contains("lifecycle=RESUMED"))
    }
}
