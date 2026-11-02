package net.bible.android.view.compose

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.workspaces.WorkspaceQuickController
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.components.AbQuickSheetContent
import net.bible.sharedui.workspaces.WorkspaceCreateAction
import net.bible.sharedui.workspaces.WorkspaceQuickContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WorkspaceQuickCreateTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val service = MemoryWorkspaces()
    private val open = mutableStateOf(true)

    @After fun close() = scope.cancel()

    private fun show() {
        val controller = WorkspaceQuickController(service, scope) { id ->
            service.activeId = id
            open.value = false
        }
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    val rows by controller.rows.collectAsState()
                    if (open.value) AbQuickSheetContent(
                        title = "Switch to workspace", onClose = { open.value = false },
                        actions = { WorkspaceCreateAction(rows.size, controller::createNew) },
                    ) {
                        WorkspaceQuickContent(rows = rows, onSelect = controller::select)
                    }
                }
            }
        }
    }

    @Test fun confirmingTheNameCreatesAndActivatesTheWorkspaceAndClosesTheSheet() {
        show()
        compose.onNodeWithContentDescription("New").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("  Sermon study  ")
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle {
            assertEquals(listOf("Study", "Sermon study"), service.rows.map { it.name })
            assertEquals("new", service.activeId)
            assertFalse(open.value)
            assertEquals(listOf("load", "save", "load", "create", "apply"), service.calls)
        }
    }

    @Test fun cancellingTheNameLeavesTheWorkspaceAndSheetUnchanged() {
        show()
        compose.onNodeWithContentDescription("New").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Discarded")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("New").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("Study"), service.rows.map { it.name })
            assertEquals("study", service.activeId)
            assertTrue(open.value)
            assertEquals(listOf("load"), service.calls)
        }
    }

    private class MemoryWorkspaces : WorkspaceService {
        var activeId = "study"
        val calls = mutableListOf<String>()
        val rows = mutableListOf(WorkspaceRowVd("study", "Study", null, 0, true))
        override fun currentWorkspaceId() = activeId
        override fun saveCurrentIntoDb() { calls += "save" }
        override suspend fun loadAll(): List<WorkspaceRowVd> { calls += "load"; return rows.toList() }
        override suspend fun createWorkspace(name: String): WorkspaceRowVd {
            calls += "create"
            return WorkspaceRowVd("new", name, null, 0, false).also { rows += it }
        }
        override suspend fun applyChanges(orderedIds: List<String>, deletedIds: List<String>, renamed: Map<String, String>, changedIds: Set<String>) { calls += "apply" }
        override suspend fun cloneWorkspace(sourceId: String, name: String): WorkspaceRowVd = error("unexpected")
        override suspend fun deleteCreated(ids: List<String>) = error("unexpected")
        override fun settingTypeLabels(sourceId: String): List<String> = error("unexpected")
        override fun copySettings(sourceId: String, typeIndices: List<Int>, targetIds: List<String>): List<WorkspaceRowVd> = error("unexpected")
        override fun copySettingsToGlobal(sourceId: String, typeIndices: List<Int>) = error("unexpected")
        override fun settingsBundleJson(id: String): String = error("unexpected")
        override fun applyWorkspaceSettings(id: String, settingsBundleJson: String, reset: Boolean): WorkspaceRowVd = error("unexpected")
    }
}
