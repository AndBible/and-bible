package net.bible.sharedui.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class QueuedAppDialogsTest {
    @Test fun realQueuePublishesProgressRelocalizesAndAnswersInOrder() = runComposeUiTest {
        val dialogs = AppDialogController()
        val permissions = AgentPermissionController()
        var language by mutableStateOf("en")
        val answers = mutableListOf<AppDialogResult>()
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings(language)) {
                AbTheme(darkTheme = false) {
                    QueuedAppDialogs(dialogs, permissions, {}, false, {})
                }
            }
        }
        var progressId = 0L
        runOnIdle {
            progressId = dialogs.show(AppDialogRequest.Progress(null, "Working"))
            dialogs.post(AppDialogRequest.Localized {
                AppDialogRequest.Message(null, if (language == "en") "Actual module details" else "Moduulin tiedot",
                    if (language == "en") "OK" else "Valmis")
            }) { answers += it }
            dialogs.post(AppDialogRequest.Confirm(null, "Next request", "Yes", "Cancel")) { answers += it }
        }
        onNodeWithText("Working").assertExists()
        onNodeWithText("Actual module details").assertExists()
        onNodeWithText("Next request").assertDoesNotExist()
        runOnIdle { language = "fi" }
        onNodeWithText("Moduulin tiedot").assertExists()
        onNodeWithText("Actual module details").assertDoesNotExist()
        onNodeWithText("Valmis").performClick()
        onNodeWithText("Next request").assertExists()
        onNodeWithText("Cancel").performClick()
        runOnIdle {
            assertEquals(listOf(AppDialogResult.Ok, AppDialogResult.Cancel), answers)
            assertNull(dialogs.pending.value)
            dialogs.dismiss(progressId)
        }
        onNodeWithText("Working").assertDoesNotExist()
        runOnIdle { assertNull(dialogs.progress.value) }
    }
}
