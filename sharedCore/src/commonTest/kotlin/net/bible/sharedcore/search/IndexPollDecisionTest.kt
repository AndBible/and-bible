package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals

class IndexPollDecisionTest {

    @Test
    fun anIndexThatIsAlreadyDoneStopsImmediately() {
        assertEquals(PollOutcome.Done, IndexPollDecision().onPoll(indexDone = true))
    }

    @Test
    fun pollingContinuesWhileTheIndexIsNotDone() {
        val d = IndexPollDecision(maxPolls = 3)
        assertEquals(PollOutcome.KeepPolling, d.onPoll(false))
        assertEquals(PollOutcome.KeepPolling, d.onPoll(false))
    }

    @Test
    fun pollingGivesUpAfterTheCap() {
        // Classic polled indexStatus six times at 2s intervals (<=12s) before treating the job as
        // failed — SearchIndexProgressComposeActivity.jobFinished:132-161. Preserved, because an index
        // that reports finished but is not yet DONE is the case that motivated it.
        val d = IndexPollDecision(maxPolls = 2)
        assertEquals(PollOutcome.KeepPolling, d.onPoll(false))
        assertEquals(PollOutcome.GaveUp, d.onPoll(false))
    }

    @Test
    fun doneAfterSomePollsStillReportsDone() {
        val d = IndexPollDecision(maxPolls = 3)
        d.onPoll(false)
        assertEquals(PollOutcome.Done, d.onPoll(true))
    }
    @Test
    fun aRunningStatusWithNoActiveJobGivesUpAfterTheStallCap() {
        // SCHEDULED/CREATING left behind by a crashed or cancelled build must not be waited on forever.
        val d = IndexPollDecision(maxStalledPolls = 3)
        assertEquals(PollOutcome.KeepPolling, d.onRunningPoll(buildActive = false))
        assertEquals(PollOutcome.KeepPolling, d.onRunningPoll(buildActive = false))
        assertEquals(PollOutcome.GaveUp, d.onRunningPoll(buildActive = false))
    }

    @Test
    fun anActiveBuildKeepsWaitingAndResetsTheStallCount() {
        val d = IndexPollDecision(maxStalledPolls = 2)
        assertEquals(PollOutcome.KeepPolling, d.onRunningPoll(buildActive = false))
        repeat(1_000) { assertEquals(PollOutcome.KeepPolling, d.onRunningPoll(buildActive = true)) }
        // The earlier stalled read no longer counts: the job made the status honest again.
        assertEquals(PollOutcome.KeepPolling, d.onRunningPoll(buildActive = false))
        assertEquals(PollOutcome.GaveUp, d.onRunningPoll(buildActive = false))
    }

    @Test
    fun runningPollsDoNotSpendThePostBuildReloadBudget() {
        val d = IndexPollDecision(maxPolls = 2, maxStalledPolls = 10)
        repeat(9) { d.onRunningPoll(buildActive = false) }
        assertEquals(PollOutcome.KeepPolling, d.onPoll(false))
        assertEquals(PollOutcome.GaveUp, d.onPoll(false))
    }
}
