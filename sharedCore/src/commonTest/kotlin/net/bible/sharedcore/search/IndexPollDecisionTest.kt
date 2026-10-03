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
}
