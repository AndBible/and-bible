package net.bible.sharedui.desktop.assembly

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.channels.Channel
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class DesktopStartupGateTest {
    @Test
    fun returningFromBackupShowsRecoveryAgainBeforeContinuing() = runComposeUiTest {
        var outcome: DesktopGateOutcome? = null
        var returnFromBackup: (() -> Unit)? = null
        setContent {
            DesktopStartupGate({ true }, false, iosStrings("en"), {}) { result, back ->
                outcome = result
                returnFromBackup = back
            }
        }
        onNodeWithTag("crash-recovery").assertExists()
        assertEquals(null, outcome, "content must not run behind the recovery gate")
        onNodeWithTag("crash-backup").performClick()
        runOnIdle { assertEquals(DesktopGateOutcome.OpenBackup, outcome) }
        onNodeWithTag("crash-recovery").assertDoesNotExist()
        runOnIdle { assertNotNull(returnFromBackup).invoke() }
        onNodeWithTag("crash-recovery").assertExists()
        onNodeWithTag("crash-continue").performClick()
        runOnIdle { assertEquals(DesktopGateOutcome.Continue, outcome) }
    }

    @Test
    fun reportIsSingleFlightAndCanRunAgainAfterCompletion() = runComposeUiTest {
        val release = Channel<Unit>(Channel.UNLIMITED)
        var reports = 0
        setContent {
            DesktopStartupGate({ true }, true, iosStrings("en"), {
                reports++
                release.receive()
            }) { _, _ -> error("report must not resolve the startup gate") }
        }
        try {
            onNodeWithTag("crash-report").performClick()
            runOnIdle { assertEquals(1, reports) }
            onNodeWithTag("crash-report").performClick()
            runOnIdle { assertEquals(1, reports, "a report is already running") }
            release.trySend(Unit).getOrThrow()
            waitForIdle()
            onNodeWithTag("crash-report").performClick()
            runOnIdle { assertEquals(2, reports, "a completed report must release the guard") }
            onNodeWithTag("crash-recovery").assertExists()
        } finally {
            // Release any outstanding calls even if an assertion fails.
            repeat(3) { release.trySend(Unit) }
        }
    }
}
