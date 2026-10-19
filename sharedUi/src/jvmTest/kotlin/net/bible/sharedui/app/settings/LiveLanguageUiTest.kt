package net.bible.sharedui.app.settings

import net.bible.sharedui.theme.AbTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.settings.AppSettingsDialog
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LiveLanguageUiTest {
    @Test
    fun openEditorSheetUsesNewLabelsWithoutClosing() = runComposeUiTest {
        var language by mutableStateOf("en")
        setContent {
            val strings = iosStrings(language)
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    AppSettingsScreen(
                        state = SettingsScreenState(strings.settings, listOf(SettingsItem.ListChoiceRow(
                            key = "theme", title = strings.resetSettings,
                            entries = listOf(SettingsItem.Choice("x", strings.cancel)), selectedValue = "x",
                        ))),
                        onUp = null, onSwitch = { _, _ -> }, onListChoice = { _, _ -> },
                        onTextInput = { _, _ -> }, onSliderChange = { _, _ -> },
                        onMultiSelectChange = { _, _ -> }, onNavigate = {}, onReset = {},
                        resetContentDescription = strings.resetSettings,
                    )
                }
            }
        }
        onNodeWithText(iosStrings("en").resetSettings).performClick()
        waitForIdle()
        language = "fi"
        waitForIdle()
        // The same row and open sheet both have the translated title; replacing/dismissing the sheet leaves one.
        onAllNodesWithText(iosStrings("fi").resetSettings).assertCountEquals(2)
        onAllNodesWithText(iosStrings("en").resetSettings).assertCountEquals(0)
    }

    @Test
    fun openResetDialogUsesNewButtonsOnNextComposition() = runComposeUiTest {
        var language by mutableStateOf("en")
        setContent {
            val strings = iosStrings(language)
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    AppSettingsScreen(
                        state = SettingsScreenState(strings.settings, emptyList()),
                        onUp = null, onSwitch = { _, _ -> }, onListChoice = { _, _ -> },
                        onTextInput = { _, _ -> }, onSliderChange = { _, _ -> },
                        onMultiSelectChange = { _, _ -> }, onNavigate = {}, onReset = {},
                        resetContentDescription = strings.resetSettings,
                        dialog = AppSettingsDialog.ConfirmReset(strings.resetSettings),
                    )
                }
            }
        }
        onAllNodesWithText(iosStrings("en").yes).assertCountEquals(1)
        language = "fi"
        waitForIdle()
        onAllNodesWithText(iosStrings("fi").yes).assertCountEquals(1)
        onAllNodesWithText(iosStrings("en").yes).assertCountEquals(0)
    }
}
