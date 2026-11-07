package net.bible.service.cloudsync

import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.event.UserMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** #4031: one failing sync run shows at most one error, and a streak of failing runs does not pop a modal every time. */
class SyncErrorReporterTest {
    private val shown = mutableListOf<UserMessage.ErrorNotification>()
    private val reporter = SyncErrorReporter { shown += it }

    private fun failingRun(quietRepeats: Boolean = true) =
        reporter.beginRun(quietRepeats).apply { report("sync error", background = false); end() }

    @Test fun aRunShowsOnlyItsFirstErrorWithItsCauseWhenItEnds() {
        val first = IllegalStateException("first")
        val run = reporter.beginRun()
        run.report("sync error", background = false, cause = first)
        run.report("another", background = false, cause = IllegalStateException("second"))
        assertTrue(shown.isEmpty(), "Nothing is shown before the run ends")
        run.end()
        run.end()
        assertEquals(1, shown.size)
        val message = shown.single()
        assertEquals("sync error", message.text)
        assertSame(first, message.cause)
        assertEquals(ActivityKind.SYNC, message.kind)
        assertFalse(message.background)
        assertFalse(message.repeated)
    }

    @Test fun aSpecificMessageWinsOverAnEarlierGenericOne() {
        val run = reporter.beginRun()
        run.report("sync error", background = false)
        run.report("Update the app to fetch bookmarks", showReportButton = false, background = false)
        run.report("wrong password", showReportButton = false, background = false)
        run.end()
        assertEquals(listOf("Update the app to fetch bookmarks"), shown.map { it.text })
        assertFalse(shown.single().showReportButton)
    }

    @Test fun theNextQuietRunAfterAFailedRunIsMarkedRepeatedAndBackground() {
        failingRun()
        failingRun(quietRepeats = true)
        assertEquals(2, shown.size)
        assertTrue(shown[1].repeated)
        assertTrue(shown[1].background, "A repeated error is never a foreground dialog")
    }

    @Test fun anInteractiveRunKeepsItsDialogEvenInAStreak() {
        failingRun()
        failingRun(quietRepeats = false)
        assertFalse(shown[1].repeated)
        assertFalse(shown[1].background)
    }

    @Test fun aCleanRunEndsTheStreak() {
        failingRun()
        reporter.beginRun().end()
        failingRun()
        assertFalse(shown.last().repeated)
        assertFalse(shown.last().background)
    }

    @Test fun aSilentFailureNeitherEndsNorStartsTheStreak() {
        reporter.beginRun().apply { noteSilentFailure(); end() }
        failingRun()
        assertFalse(shown.last().repeated, "A silent failure before must not make the first shown error quiet")
        reporter.beginRun().apply { noteSilentFailure(); end() }
        failingRun()
        assertTrue(shown.last().repeated, "A silent failure in between must not end the streak")
    }

    @Test fun aCancelledRunStillShowsItsErrorButLeavesTheStreak() {
        reporter.beginRun().apply { report("sync error", background = false); end(completed = false) }
        assertEquals(1, shown.size)
        failingRun()
        assertFalse(shown.last().repeated)
    }
}
