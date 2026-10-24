package net.bible.sharedui.app.bookmark

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.components.AppDialogHost
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class BookmarkCsvLocalizedSelectionTest {
    @Test fun editedColumnChecksSurviveLocaleChangeButNewRequestResets() = runComposeUiTest {
        val dialogs = AppDialogController()
        var finnish by mutableStateOf(false)
        val answers = mutableListOf<List<String>>()
        fun request() = AppDialogRequest.Localized {
            AppDialogRequest.MultiChoice(
                title = if (finnish) "Vie sarakkeet" else "Export columns",
                options = listOf(SettingsItem.Choice("osisRef", if (finnish) "Viite" else "Reference"),
                    SettingsItem.Choice("notes", if (finnish) "Muistiinpanot" else "Notes")),
                selectedIds = listOf("osisRef"), confirmText = "OK", dismissText = "Cancel",
            )
        }
        dialogs.post(request()) { answers += (it as AppDialogResult.SelectedMany).ids }
        setContent {
            val shown by dialogs.pending.collectAsState()
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    AppDialogHost(shown, null, dialogs::respond, {}, {}, {})
                }
            }
        }
        onNodeWithText("Reference").performClick()
        onNodeWithText("Notes").performClick()
        runOnIdle { finnish = true }
        onNodeWithText("Viite").assertIsOff()
        onNodeWithText("Muistiinpanot").assertIsOn()
        onNodeWithText("OK").performClick()
        runOnIdle {
            assertEquals(listOf(listOf("notes")), answers)
            dialogs.post(request()) { answers += (it as AppDialogResult.SelectedMany).ids }
        }
        onNodeWithText("Viite").assertIsOn()
        onNodeWithText("Muistiinpanot").assertIsOff()
        onNodeWithText("OK").performClick()
        runOnIdle { assertEquals(listOf(listOf("notes"), listOf("osisRef")), answers) }
    }
}
