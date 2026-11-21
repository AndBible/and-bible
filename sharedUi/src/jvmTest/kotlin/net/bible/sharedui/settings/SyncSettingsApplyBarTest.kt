package net.bible.sharedui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.sharedcore.settings.ApplyResult
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.DocSyncSummaryData
import net.bible.sharedcore.settings.SyncCategoryKeys
import net.bible.sharedcore.settings.SyncSettingsController
import net.bible.sharedcore.settings.SyncSettingsLabels
import net.bible.sharedcore.settings.SyncSettingsService
import net.bible.sharedcore.settings.SyncSettingsSnapshot
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pending-switch UI of the sync settings screen: Apply bar, checking dialog and leave dialog. */
@OptIn(ExperimentalTestApi::class)
class SyncSettingsApplyBarTest {
    private val key = "sync_enable_bookmarks"

    private class Fixture(val service: FakeSyncService, val scope: CoroutineScope) {
        var exits = 0
        val controller = SyncSettingsController(service, scope, SyncSettingsLabels.forTest(), {}, onExit = { exits++ })
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(f: Fixture) = setContent {
        val state by f.controller.state.collectAsState()
        CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false, disableAnimations = true) {
                val c = f.controller
                SyncSettingsScreen(
                    uiState = state, onUp = {}, onSwitch = c::onSwitch, onListChoice = c::onListChoice,
                    onTextInput = c::onTextInput, onNavigate = c::onNavigate, onConfirmReset = c::confirmReset,
                    onConfirmEnableDocuments = c::confirmEnableDocuments,
                    onConfirmForgetCertificate = c::confirmForgetCertificate, onDismissDialog = c::dismissDialog,
                    onApply = c::applyPending, onDiscard = c::discardPending, onCancelChecking = c::cancelChecking,
                    onConfirmLeaveApply = c::confirmLeaveApply, onConfirmLeaveDiscard = c::confirmLeaveDiscard,
                )
            }
        }
    }

    private fun withFixture(on: Boolean, body: androidx.compose.ui.test.ComposeUiTest.(Fixture) -> Unit) = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val f = Fixture(FakeSyncService(syncSnapshot(on)), scope)
            show(f)
            body(f)
        } finally { scope.coroutineContext[Job]?.cancel() }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.toggle() {
        onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-$key"))
        onNodeWithTag("settings-row-$key").performClick()
    }

    private val en = iosStrings("en")

    @Test fun switchingMarksPendingAndShowsTheBarWithApplyAndDiscard() = withFixture(on = true) { f ->
        onNodeWithTag("sync-apply-bar").assertDoesNotExist()
        toggle()
        onNodeWithTag("sync-apply-bar").assertIsDisplayed()
        onNodeWithText(en.syncPendingChanges(1)).assertIsDisplayed()
        onNodeWithText(en.dialogApply).assertIsDisplayed()
        onNodeWithText(en.discard).assertIsDisplayed()
        assertTrue(f.service.applied.isEmpty(), "a switch alone must not touch the service")
    }

    @Test fun discardRestoresTheRowAndHidesTheBar() = withFixture(on = true) { f ->
        toggle()
        onNodeWithText(en.discard).performClick()
        onNodeWithTag("sync-apply-bar").assertDoesNotExist()
        onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("settings-row-$key"))
        onNodeWithTag("settings-row-$key").assertIsOn()
        assertTrue(f.service.applied.isEmpty())
    }

    @Test fun applySendsThePendingChangesAndHidesTheBar() = withFixture(on = true) { f ->
        toggle()
        onNodeWithText(en.dialogApply).performClick()
        assertEquals(listOf(mapOf(key to false)), f.service.applied)
        onNodeWithTag("sync-apply-bar").assertDoesNotExist()
    }

    @Test fun checkingDialogShowsCancelAndBackDoesNotDismissIt() = withFixture(on = false) { f ->
        f.service.gate = CompletableDeferred()
        toggle()
        onNodeWithText(en.dialogApply).performClick()
        onNodeWithText(en.syncCheckingCloudTitle).assertIsDisplayed()
        onNodeWithText(en.syncCheckingCloudText).assertIsDisplayed()
        onNodeWithTag("sync-apply-bar").assertDoesNotExist()  // no second Apply while one runs
        onNodeWithText(en.syncCheckingCloudTitle).performKeyInput { pressKey(Key.Escape) }
        onNodeWithText(en.syncCheckingCloudTitle).assertExists()
        assertFalse(f.service.cancelledApply)
        onNodeWithText(en.cancel).performClick()
        assertTrue(f.service.cancelledApply, "Cancel must abort the running apply")
        onNodeWithText(en.syncCheckingCloudTitle).assertDoesNotExist()
    }

    @Test fun leaveDialogDiscardDropsPendingAndExits() = withFixture(on = true) { f ->
        toggle()
        runOnIdle { assertFalse(f.controller.onLeave()) }
        onNodeWithText(en.syncPendingLeaveTitle).assertIsDisplayed()
        onNodeWithText(en.discard).performClick()
        onNodeWithText(en.syncPendingLeaveTitle).assertDoesNotExist()
        assertEquals(1, f.exits)
        assertTrue(f.service.applied.isEmpty())
        onNodeWithTag("sync-apply-bar").assertDoesNotExist()
    }

    @Test fun leaveDialogApplyAppliesThenExits() = withFixture(on = true) { f ->
        toggle()
        runOnIdle { f.controller.onLeave() }
        onNodeWithText(en.dialogApply).performClick()
        assertEquals(listOf(mapOf(key to false)), f.service.applied)
        assertEquals(1, f.exits)
    }

    @Test fun leaveDialogCancelStaysWithPendingKept() = withFixture(on = true) { f ->
        toggle()
        runOnIdle { f.controller.onLeave() }
        onNodeWithText(en.cancel).performClick()
        onNodeWithText(en.syncPendingLeaveTitle).assertDoesNotExist()
        onNodeWithTag("sync-apply-bar").assertIsDisplayed()
        assertEquals(0, f.exits)
    }
}
