package net.bible.sharedui.workspaces

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** The current workspace must be visible and identifiable when the selector opens. */
@OptIn(ExperimentalTestApi::class)
class WorkspaceSelectorCurrentRowTest {
    private fun rows(count: Int, current: Int) = (1..count).map {
        WorkspaceRowVd("$it", "Workspace $it", "1 window", 0xFF2E7D32.toInt(), isCurrent = it == current)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(items: List<WorkspaceRowVd>, animations: Boolean = false) =
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false, disableAnimations = !animations) {
                    WorkspaceSelectorScreen(
                        title = "Select workspace", workspaces = items, dirty = false, canDelete = true,
                        filtering = false, query = "", searchModeActive = false,
                        copySettingsState = null, pendingSelectId = null,
                        onQueryChange = {}, onOpenSearch = {}, onCloseSearch = {},
                        onMove = { _, _ -> }, onSelect = {}, onRename = { _, _ -> },
                        onClone = { _, _ -> }, onDelete = {}, onEditSettings = {}, onCopySettings = {},
                        onCopySettingsToGlobal = {}, onChooseCopyTypes = {}, onChooseCopyTargets = {},
                        onCancelCopySettings = {}, onCreate = {}, onSave = {}, onCancel = {},
                        onConfirmPendingSelect = {}, onDismissPendingSelect = {}, onHelp = {}, onNavigateUp = {},
                    )
                }
            }
        }

    @Test
    fun startsScrolledToCurrentWorkspaceFarDown() = runComposeUiTest {
        show(rows(30, current = 25))
        waitForIdle()
        onNodeWithText("Workspace 25", substring = true).assertIsDisplayed()
    }

    @Test
    fun startsScrolledToCurrentWorkspaceWithAnimations() = runComposeUiTest {
        show(rows(30, current = 25), animations = true)
        waitForIdle()
        onNodeWithText("Workspace 25", substring = true).assertIsDisplayed()
    }

    @Test
    fun currentRowReportsSelectedSemantics() = runComposeUiTest {
        show(rows(3, current = 2))
        waitForIdle()
        onNodeWithText("Workspace 2", substring = true).assertIsSelected()
        onNodeWithText("Workspace 1").assertIsNotSelected()
    }
}
