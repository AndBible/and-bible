package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 8 §5.1: the history item for "where I left the reading view" is recorded at the host's two
 * chokepoints, and this guard watches THEM -- not `navigate(` calls in `ReadingNavGraph.kt`, which has
 * none (slice 7 Task 7 was dropped because its guard watched that file and could never fail).
 */
class ReadingHistoryExitGuardTest {

    private val host = File("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")
        .readText()

    private fun body(signature: String): String {
        val at = host.indexOf(signature)
        assertTrue("'$signature' not found -- the guard has lost its subject", at >= 0)
        var i = host.indexOf('{', at + signature.length)
        var depth = 0
        val start = i
        while (i < host.length) {
            when (host[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return host.substring(start, i + 1) }
            }
            i++
        }
        throw AssertionError("unbalanced body for '$signature'")
    }

    @Test
    fun theLaunchFunnelRecordsTheExit() {
        assertTrue(
            "every startActivity/startActivityForResult from the reading destination must record where " +
                "the user was (M3)",
            body("override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?)")
                .contains("recordHistoryOnLeavingReading("),
        )
    }

    @Test
    fun theInGraphNavigateRecordsTheExit() {
        assertTrue(
            "an EXTRA_ROUTE navigated while reading is current must record too (M3)",
            body("private fun navigateToRoute(controller: NavHostController, route: String)")
                .contains("recordHistoryOnLeavingReading("),
        )
    }

    @Test
    fun theRecorderIsGatedOnTheReadingDestinationAndCallsTheManagerDirectly() {
        val recorder = body("private fun recordHistoryOnLeavingReading(targetRoute: String?)")
        assertTrue("gated on the reading destination", recorder.contains("readingDestinationIsCurrent()"))
        assertTrue(
            "a DIRECT call, not a bus event -- the item must be created while " +
                "ReadingViewVisibility is still true, ordered by the call stack",
            recorder.contains("historyManager.addHistoryItem(") && !recorder.contains("ABEventBus.post"),
        )
    }
}
