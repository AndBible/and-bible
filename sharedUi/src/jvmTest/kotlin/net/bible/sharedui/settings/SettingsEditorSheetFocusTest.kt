package net.bible.sharedui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.ai.MaxIterationsSheetContent
import net.bible.sharedui.ai.RetentionSheetContent
import net.bible.sharedui.components.AbCreateItemSheet
import net.bible.sharedui.components.AbTextInputContent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** Catches sheets opening without transferring focus to their text editor (issue #4005). */
@OptIn(ExperimentalTestApi::class)
class SettingsEditorSheetFocusTest {
    private object ZeroMotionDurationScale : MotionDurationScale {
        override val scaleFactor = 0f
    }

    private fun settingsEditorFocusesText(
        disableAnimations: Boolean,
        effectContext: CoroutineContext = EmptyCoroutineContext,
    ) = runComposeUiTest(effectContext = effectContext) {
        var page by mutableStateOf<SettingsEditorPage?>(null)
        val editor = SettingsEditorStack()
        val rows = listOf(
            SettingsItem.TextInputRow("url", "Server address", value = "https://example.org"),
            SettingsItem.TextInputRow("password", "Password", value = "secret", masked = true),
        )
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, disableAnimations = disableAnimations) {
                    Column {
                        rows.forEach { row ->
                            TextButton(onClick = { page = SettingsEditorPage.Row(row.key) }) { Text("Open ${row.key}") }
                        }
                    }
                    GenericSettingsEditorSheet(
                        state = SettingsScreenState("Sync", rows), editor = editor, page = page, depth = 1,
                        onListChoice = { _, _ -> }, onTextInput = { _, _ -> }, onMultiSelectChange = { _, _ -> },
                    )
                }
            }
        }
        onNodeWithText("Open url").performClick()
        onNode(hasSetTextAction()).assertIsFocused()
        // Same sheet, different page: focus must follow the new editor as well.
        runOnIdle { page = SettingsEditorPage.Row("password") }
        onNode(hasSetTextAction()).assertIsFocused()
        runOnIdle { page = null }
        onNodeWithText("Open url").performClick()
        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test fun textSettingsFocusWhenOpenedAndReopened() = settingsEditorFocusesText(false)
    @Test fun textSettingsFocusWithAnimationsDisabled() = settingsEditorFocusesText(true)
    @Test fun textSettingsFocusWithAndroidZeroMotionScale() = settingsEditorFocusesText(true, ZeroMotionDurationScale)

    @Test fun textEditorFocusesFirstFieldAndDoesNotStealFocusBack() = runComposeUiTest {
        var second by mutableStateOf("")
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    SettingsEditorSheet(SettingsEditorPage.Row("form"), "Form", false, {}, {}) {
                        AbTextInputContent("first", {})
                        OutlinedTextField(second, { second = it })
                    }
                }
            }
        }
        onAllNodes(hasSetTextAction())[0].assertIsFocused()
        onAllNodes(hasSetTextAction())[1].assertIsNotFocused().performClick()
        runOnIdle { second = "changed" }
        onAllNodes(hasSetTextAction())[1].assertIsFocused()
    }

    @Test fun retentionEditorFocusesOnlyAfterItIsEnabled() = runComposeUiTest {
        val strings = iosStrings("en")
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    SettingsEditorSheet(SettingsEditorPage.Row("retention"), "Retention", false, {}, {}) {
                        RetentionSheetContent("Retention", -1, {}, {})
                    }
                }
            }
        }
        onNode(SemanticsMatcher.keyIsDefined(
            SemanticsProperties.EditableText,
        )).assertIsNotEnabled()
        onNodeWithText(strings.rawLogRetentionDisabledLabel).performClick()
        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test fun iterationsEditorFocusesOnlyAfterGlobalDefaultIsTurnedOff() = runComposeUiTest {
        val strings = iosStrings("en")
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    SettingsEditorSheet(SettingsEditorPage.Row("iterations"), "Iterations", false, {}, {}) {
                        MaxIterationsSheetContent(null, "10", {}, {})
                    }
                }
            }
        }
        onNode(SemanticsMatcher.keyIsDefined(
            SemanticsProperties.EditableText,
        )).assertIsNotEnabled()
        onNodeWithText(strings.promptMaxIterationsUseGlobal("10")).performClick()
        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test fun standaloneContentDoesNotAutofocus() = runComposeUiTest {
        setContent {
            AbTheme(darkTheme = false) { AbTextInputContent("unchanged", {}) }
        }
        onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test fun createItemSheetFocusesNameWithoutClickingField() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    AbCreateItemSheet("Create", "", "Create", "Import", {}, {}, {})
                }
            }
        }
        onNode(hasSetTextAction()).assertIsFocused()
    }
}
