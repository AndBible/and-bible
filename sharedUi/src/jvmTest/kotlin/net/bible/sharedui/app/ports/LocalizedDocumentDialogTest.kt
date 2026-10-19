package net.bible.sharedui.app.ports

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.components.AppDialogHost
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LocalizedDocumentDialogTest {
    @Test fun openUnlockDialogRelocalizesAndPreservesTypedKey() = runComposeUiTest {
        var language by mutableStateOf("en")
        val dialogs = AppDialogController()
        dialogs.post(AppDialogRequest.Localized {
            AppDialogRequest.TextInput(
                title = if (language == "en") "Enter key" else "Anna avain", message = null,
                initial = "", confirmText = "OK", dismissText = "Cancel", draftKey = "unlock:TEST",
            )
        })
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false) {
                    AppDialogHost(dialogs.pending.value, null, dialogs::respond, {}, {}, {},
                        draftFor = dialogs::draft, onDraftChange = dialogs::saveDraft)
                }
            }
        }
        onNodeWithText("Enter key").assertExists()
        onNodeWithText("").performTextInput("half typed")
        runOnIdle { language = "fi" }
        onNodeWithText("Anna avain").assertExists()
        onNodeWithText("half typed").assertExists()
        runOnIdle { assertEquals("half typed", dialogs.draft(dialogs.pending.value!!.id)) }
    }
}
