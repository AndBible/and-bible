package net.bible.test

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AwaitHostConditionTest {
    /**
     * Reproduces the contended-machine case: a host effect that completes after 12 s of wall clock while each
     * composition pump is slow. The former fixed 10 s deadline threw here (ReadingPlanSelectorResetFlowTest, 40-50 s cold).
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

    @Test fun theDefaultDeadlineIsSixtySeconds() {
        assertTrue(HOST_CONDITION_TIMEOUT_MS == 60_000L)
    }
}
