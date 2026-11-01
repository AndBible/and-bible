package net.bible.sharedui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.LabelIdentitySheet
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalMaterial3Api::class)
class AppWideSheetChromeTest {
    @get:Rule val rule = createComposeRule()

    private fun assertCloseWithoutHandle(content: @Composable (() -> Unit) -> Unit) {
        var closed = 0
        var closeLabel = ""
        rule.setContent {
            ProvideAppLocals {
                AbTheme(disableAnimations = true) {
                    CompositionLocalProvider(LocalSheetDragHandleDescription provides "Sheet handle") {
                        closeLabel = LocalStrings.current.settingsEditorClose
                        content { closed++ }
                    }
                }
            }
        }
        rule.onNodeWithContentDescription(closeLabel).assertHasClickAction().performClick()
        rule.onNodeWithContentDescription("Sheet handle").assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun choiceClosesWithoutSelecting() = assertCloseWithoutHandle { close ->
        AbChoiceSheet(true, "Choose", listOf(SettingsItem.Choice("a", "Alpha")), "a",
            onSelect = { error("Close must not select") }, onDismiss = close)
    }

    @Test fun untitledChoiceClosesWithoutSelecting() = assertCloseWithoutHandle { close ->
        AbChoiceSheet(true, "", listOf(SettingsItem.Choice("a", "Alpha")), "a",
            onSelect = { error("Close must not select") }, onDismiss = close)
    }

    @Test fun multiSelectClosesWithoutConfirming() = assertCloseWithoutHandle { close ->
        AbMultiSelectSheet(true, "Choose", listOf("Alpha"), emptyList<String>(), { it }, { it },
            "OK", "Cancel", onConfirm = { error("Close must not confirm") }, onDismiss = close)
    }

    @Test fun actionClosesWithoutChoosing() = assertCloseWithoutHandle { close ->
        AbActionSheet(true, "Actions", onDismiss = close) { Text("Action") }
    }

    @Test fun untitledActionClosesWithoutChoosing() = assertCloseWithoutHandle { close ->
        AbActionSheet(true, "", onDismiss = close) { Text("Action") }
    }

    @Test fun createClosesWithoutCreatingOrImporting() = assertCloseWithoutHandle { close ->
        AbCreateItemSheet("Create", "", "Create", "Import", { error("Unexpected create") },
            { error("Unexpected import") }, close)
    }

    @Test fun searchablePickerClosesWithoutSelecting() = assertCloseWithoutHandle { close ->
        AbSearchableOptionSheet(listOf("Alpha"), "Alpha", { it },
            { error("Unexpected selection") }, close, searchPlaceholder = "Search")
    }

    @Test fun shortPickerClosesWithoutSelecting() = assertCloseWithoutHandle { close ->
        AbSearchableOptionSheet(listOf("Alpha"), "Alpha", { it },
            { error("Unexpected selection") }, close)
    }

    @Test fun labelSearchOptionsCloseWithoutChangingFilters() = assertCloseWithoutHandle { close ->
        net.bible.sharedui.bookmark.ManageLabelsSearchOptionsSheet(
            true, net.bible.sharedcore.bookmark.ManageLabelsMode.STUDYPAD,
            net.bible.sharedcore.bookmark.SearchMode.NAME_START, emptySet(),
            { error("Unexpected search change") }, { error("Unexpected filter change") }, close,
        )
    }

    @Test fun labelIdentityClosesWithoutEditing() = assertCloseWithoutHandle { close ->
        LabelIdentitySheet("Name", true, 0xff808080.toInt(), null, emptyList(), false,
            { error("Unexpected name change") }, { error("Unexpected color change") },
            { error("Unexpected icon change") }, close, iconSlot = { _, _ -> })
    }

    @Test fun summaryHasCloseInsteadOfHandle() {
        var closeLabel = ""
        rule.setContent {
            ProvideAppLocals {
                AbTheme(disableAnimations = true) {
                    CompositionLocalProvider(LocalSheetDragHandleDescription provides "Sheet handle") {
                        closeLabel = LocalStrings.current.settingsEditorClose
                        AbSettingsSummarySheet("Settings", initiallyOpen = true) { Text("Sheet body") }
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("Sheet handle").assertDoesNotExist()
        rule.onNodeWithContentDescription(closeLabel).performClick()
        rule.onNodeWithText("Sheet body").assertDoesNotExist()
    }

    @Test fun resizableSheetKeepsCompactHandle() {
        lateinit var state: SheetState
        rule.setContent {
            AbTheme(disableAnimations = true) {
                state = rememberModalBottomSheetState()
                AbModalBottomSheet(onDismissRequest = {}, sheetState = state, dragHandleDescription = "Sheet handle") {
                    Box(Modifier.height(600.dp))
                }
            }
        }
        rule.onNodeWithContentDescription("Sheet handle").assertExists()
        rule.runOnIdle {
            org.junit.Assert.assertTrue(state.hasPartiallyExpandedState)
            org.junit.Assert.assertTrue(state.hasExpandedState)
        }
    }

    @Test fun untitledActionSharesFirstRowWithClose() {
        var closeLabel = ""
        rule.setContent {
            ProvideAppLocals {
                AbTheme(disableAnimations = true) {
                    closeLabel = LocalStrings.current.settingsEditorClose
                    AbActionSheetContent(title = "", onClose = {}) {
                        AbActionSheetRow("First action", onClick = {})
                    }
                }
            }
        }
        val close = rule.onNodeWithContentDescription(closeLabel).fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithText("First action", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Close must share the first action row", close.top < action.bottom)
    }

    @Test fun untitledChoiceSharesFirstRowWithClose() {
        var closeLabel = ""
        rule.setContent {
            ProvideAppLocals {
                AbTheme(disableAnimations = true) {
                    closeLabel = LocalStrings.current.settingsEditorClose
                    AbChoiceSheetContent("", listOf(SettingsItem.Choice("a", "Alpha")), "a", {}, {})
                }
            }
        }
        val close = rule.onNodeWithContentDescription(closeLabel).fetchSemanticsNode().boundsInRoot
        val choice = rule.onNodeWithText("Alpha", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Close must share the first choice row", close.top < choice.bottom)
    }

    @Test fun nonCancellableActionHasNeitherCloseNorHandle() {
        var closeLabel = ""
        rule.setContent {
            ProvideAppLocals {
                AbTheme(disableAnimations = true) {
                    CompositionLocalProvider(LocalSheetDragHandleDescription provides "Sheet handle") {
                        closeLabel = LocalStrings.current.settingsEditorClose
                        AbActionSheet(true, "Required action", cancellable = false,
                            onDismiss = { error("Must not dismiss") }) { Text("Continue") }
                    }
                }
            }
        }
        rule.onNodeWithContentDescription(closeLabel).assertDoesNotExist()
        rule.onNodeWithContentDescription("Sheet handle").assertDoesNotExist()
    }
}
