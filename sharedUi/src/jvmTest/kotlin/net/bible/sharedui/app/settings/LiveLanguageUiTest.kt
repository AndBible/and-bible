package net.bible.sharedui.app.settings

import net.bible.sharedui.app.workspace.buildTextDisplayScreenLabels
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
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
    fun openTextDisplayEditorRelocalizesAndKeepsItsPage() = runComposeUiTest {
        var language by mutableStateOf("en")
        var committed = -1
        setContent {
            val strings = iosStrings(language)
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    net.bible.sharedui.settings.TextDisplaySettingsScreen(
                        state = net.bible.sharedcore.settings.TextDisplaySettingsScreenState(
                            strings.settings,
                            listOf(SettingsItem.NavigationRow("FONTSIZE", strings.resetSettings)),
                            mapOf(net.bible.sharedcore.settings.TextSettingType.FONTSIZE to net.bible.sharedcore.settings.TextSettingRow(
                                net.bible.sharedcore.settings.TextSettingType.FONTSIZE,
                                net.bible.sharedcore.settings.TextSettingRowValue.Numeric(22, 8, 40, language + "22"),
                                net.bible.sharedcore.settings.InheritedFrom.NONE, true, true,
                            )),
                        ),
                        dialogLabels = strings.buildTextDisplayScreenLabels(), badgeFor = { null },
                        onUp = {}, onSwitch = { _, _ -> }, onListChoice = { _, _ -> },
                        onNumericChange = { _, value -> committed = value }, onMarginsChange = { _, _, _, _ -> },
                        onRevert = {}, onReset = {}, onNavigate = {},
                    )
                }
            }
        }
        onNodeWithText(iosStrings("en").resetSettings).performClick()
        waitForIdle()
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        language = "fi"
        waitForIdle()
        onAllNodesWithText(iosStrings("fi").buildTextDisplayScreenLabels().fontSizeDialogTitle).assertCountEquals(1)
        onAllNodesWithText(iosStrings("en").buildTextDisplayScreenLabels().fontSizeDialogTitle).assertCountEquals(0)
        onNodeWithText(iosStrings("fi").buildTextDisplayScreenLabels().okLabel).performClick()
        kotlin.test.assertEquals(30, committed)
    }

    @Test
    fun openSyncConfirmationRelocalizesWithoutClosing() = runComposeUiTest {
        var language by mutableStateOf("en")
        setContent {
            val strings = iosStrings(language)
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false) {
                    net.bible.sharedui.settings.SyncSettingsScreen(
                        uiState = net.bible.sharedcore.settings.SyncSettingsUiState(
                            screen = SettingsScreenState(strings.settings, emptyList()),
                            dialog = net.bible.sharedcore.settings.SyncDialog.ResetConfirm(strings.resetSettings),
                        ),
                        onUp = null, onSwitch = { _, _ -> }, onListChoice = { _, _ -> },
                        onTextInput = { _, _ -> }, onNavigate = {}, onConfirmReset = {},
                        onConfirmEnableDocuments = {}, onConfirmForgetCertificate = {}, onDismissDialog = {},
                    )
                }
            }
        }
        onAllNodesWithText(iosStrings("en").resetSettings).assertCountEquals(1)
        language = "fi"
        waitForIdle()
        onAllNodesWithText(iosStrings("fi").resetSettings).assertCountEquals(1)
        onAllNodesWithText(iosStrings("en").resetSettings).assertCountEquals(0)
    }

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
