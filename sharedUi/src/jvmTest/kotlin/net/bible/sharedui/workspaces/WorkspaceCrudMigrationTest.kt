package net.bible.sharedui.workspaces

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedcore.workspaces.WorkspaceSelectorController
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkspaceCrudMigrationTest {
    private class Store : WorkspaceService {
        private var current = "1"
        private var next = 2
        private var rows = listOf(WorkspaceRowVd("1", "Initial", null, 0xff444444.toInt(), true))
        fun select(id: String) { current = id }
        override fun currentWorkspaceId() = current
        override fun saveCurrentIntoDb() = Unit
        override suspend fun loadAll() = rows.map { it.copy(isCurrent = it.id == current) }
        override suspend fun createWorkspace(name: String): WorkspaceRowVd {
            val row = WorkspaceRowVd((next++).toString(), name, null, 0xff444444.toInt(), false)
            rows = rows + row
            return row
        }
        override suspend fun applyChanges(orderedIds: List<String>, deletedIds: List<String>, renamed: Map<String, String>, changedIds: Set<String>) {
            val remaining = rows.filterNot { it.id in deletedIds }.associateBy { it.id }
            rows = orderedIds.map { id -> remaining.getValue(id).let {
                if (id in changedIds) it.copy(name = renamed[id] ?: it.name) else it
            } }
        }
        override suspend fun cloneWorkspace(sourceId: String, name: String): WorkspaceRowVd = error("unused")
        override suspend fun deleteCreated(ids: List<String>) { rows = rows.filterNot { it.id in ids } }
        override fun settingTypeLabels(sourceId: String): List<String> = error("unused")
        override fun copySettings(sourceId: String, typeIndices: List<Int>, targetIds: List<String>): List<WorkspaceRowVd> = error("unused")
        override fun copySettingsToGlobal(sourceId: String, typeIndices: List<Int>) = error("unused")
        override fun settingsBundleJson(id: String): String = error("unused")
        override fun applyWorkspaceSettings(id: String, settingsBundleJson: String, reset: Boolean): WorkspaceRowVd = error("unused")
    }

    @Test
    fun createRenameDeleteSurvivesSelectorRecreation() = runComposeUiTest {
        val store = Store()
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val strings = iosStrings("en")
        var exits = 0
        fun controller() = WorkspaceSelectorController(
            service = store, scope = scope, writeScope = scope,
            onResult = { id, _ -> if (id != null) store.select(id); exits++ },
            onCancel = {}, onEditSettings = {},
        ).also { it.load() }
        val active = mutableStateOf(controller())
        val quick = mutableStateOf(false)
        try {
            setContent {
                val c = active.value
                val rows by c.workspaces.collectAsState()
                val dirty by c.dirty.collectAsState()
                val canDelete by c.canDelete.collectAsState()
                val canCopy by c.canCopySettingsToWorkspaces.collectAsState()
                val filtering by c.filtering.collectAsState()
                val query by c.query.collectAsState()
                val search by c.searchModeActive.collectAsState()
                val copy by c.copySettingsState.collectAsState()
                val pending by c.pendingSelectId.collectAsState()
                CompositionLocalProvider(LocalStrings provides strings) {
                    AbTheme(darkTheme = false, disableAnimations = true) {
                        if (quick.value) WorkspaceQuickContent(rows, onSelect = {})
                        else WorkspaceSelectorScreen(
                            title = strings.workspaceSelectorTitle, workspaces = rows, dirty = dirty,
                            canDelete = canDelete, canCopySettings = canCopy, filtering = filtering,
                            query = query, searchModeActive = search, copySettingsState = copy, pendingSelectId = pending,
                            onQueryChange = c::setQuery, onOpenSearch = c::openSearch, onCloseSearch = c::closeSearch,
                            onMove = c::moveIndex, onSelect = c::selectWorkspace, onRename = c::rename,
                            onClone = c::clone, onDelete = c::requestDelete, onEditSettings = c::editSettings,
                            onCopySettings = c::beginCopySettings, onCopySettingsToGlobal = c::beginCopySettingsToGlobal,
                            onChooseCopyTypes = c::chooseCopyTypes, onChooseCopyTargets = c::chooseCopyTargets,
                            onCancelCopySettings = c::cancelCopySettings, onCreate = c::createNew,
                            onSave = c::save, onCancel = c::cancel, onConfirmPendingSelect = c::confirmPendingSelect,
                            onDismissPendingSelect = c::dismissPendingSelect, onHelp = {}, onNavigateUp = {},
                        )
                    }
                }
            }
            fun reopen() { runOnIdle { active.value = controller() } }
            fun create(name: String) {
                onNodeWithContentDescription(strings.newItem).performClick()
                onNode(hasSetTextAction()).performTextReplacement(name)
                onNodeWithText(strings.okay).performClick()
            }
            create("UITest Alpha")
            runOnIdle { assertEquals(1, exits) }
            reopen()
            onNodeWithText("UITest Alpha", substring = true).assertIsDisplayed()
            onAllNodesWithContentDescription(strings.menu)[1].performClick()
            onNodeWithText(strings.rename).performClick()
            onNode(hasSetTextAction()).performTextReplacement("UITest Beta")
            onNodeWithText(strings.okay).performClick()
            onNodeWithText("UITest Beta", substring = true).assertIsDisplayed()
            onNodeWithText("UITest Alpha", substring = true).assertDoesNotExist()
            onNodeWithText(strings.saveAndExit).assertIsEnabled().performClick()
            runOnIdle { assertEquals(2, exits) }
            reopen()
            onNodeWithText("UITest Beta", substring = true).assertIsDisplayed()
            onNodeWithText("UITest Alpha", substring = true).assertDoesNotExist()
            create("UITest Gamma")
            runOnIdle { assertEquals(3, exits) }
            reopen()
            onAllNodesWithContentDescription(strings.menu)[1].performClick()
            onNodeWithText(strings.deleteWorkspaceLabel).performClick()
            onNodeWithText("UITest Beta", substring = true).assertDoesNotExist()
            onNodeWithText("UITest Gamma", substring = true).assertIsDisplayed()
            onNodeWithText(strings.saveAndExit).assertIsEnabled().performClick()
            runOnIdle { assertEquals(4, exits) }
            reopen()
            runOnIdle { quick.value = true }
            onNodeWithText("UITest Gamma", substring = true).assertIsDisplayed()
            onNodeWithText("UITest Beta", substring = true).assertDoesNotExist()
            onNodeWithText("UITest Alpha", substring = true).assertDoesNotExist()
        } finally {
            scope.cancel()
        }
    }
}
