package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.workspaces.CopySettingsState
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedui.workspaces.WorkspaceSelectorScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WorkspaceSelectorGoldenTest {
    private val rows = listOf(
        WorkspaceRowVd("1", "Study", "3 windows", 0xFF2E7D32.toInt(), isCurrent = true),
        WorkspaceRowVd("2", "Sermon prep", "1 window", 0xFF1565C0.toInt(), isCurrent = false),
        WorkspaceRowVd("3", "Greek", null, 0xFF444444.toInt(), isCurrent = false),
    )

    @Composable
    private fun screen(
        items: List<WorkspaceRowVd> = rows, dirty: Boolean = false, canDelete: Boolean = true,
        filtering: Boolean = false, query: String = "",
        copy: CopySettingsState? = null, pending: String? = null,
    ) = WorkspaceSelectorScreen(
        title = "Workspaces", workspaces = items, dirty = dirty, canDelete = canDelete,
        filtering = filtering, query = query, copySettingsState = copy, pendingSelectId = pending,
        onQueryChange = {}, onMove = { _, _ -> }, onSelect = {}, onRename = { _, _ -> },
        onClone = { _, _ -> }, onDelete = {}, onEditSettings = {}, onCopySettings = {},
        onCopySettingsToGlobal = {}, onChooseCopyTypes = {}, onChooseCopyTargets = {},
        onCancelCopySettings = {}, onCreate = {}, onSave = {}, onCancel = {},
        onConfirmPendingSelect = {}, onDismissPendingSelect = {}, onHelp = {}, onNavigateUp = {},
    )

    // heightDp=900: this is a list screen; the default viewport clips row 3 + the Save/Cancel bar.
    @Test fun workspaceSelector_populated() { captureMatrix("WorkspaceSelector", "populated", heightDp = 900) { screen() } }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun workspaceSelector_populated_rtl() { captureRtl("WorkspaceSelector", "populated", heightDp = 900) { screen() } }

    @Test fun workspaceSelector_dirty() {
        captureGolden("WorkspaceSelector", "dirty", EDGE_MODE, heightDp = 900) { screen(dirty = true) }
    }

    @Test fun workspaceSelector_filtering() {
        captureGolden("WorkspaceSelector", "filtering", EDGE_MODE, heightDp = 900) {
            screen(items = rows.filter { it.name.contains("Ser") }, filtering = true, query = "Ser")
        }
    }

    @Test fun workspaceSelector_single() {
        captureGolden("WorkspaceSelector", "single", EDGE_MODE, heightDp = 900) {
            screen(items = rows.take(1), canDelete = false)
        }
    }

    @Test fun workspaceSelector_copySettings() {
        captureGolden("WorkspaceSelector", "copySettings", EDGE_MODE, heightDp = 900) {
            screen(copy = CopySettingsState.ChooseTypes("1", listOf("Font size", "Colors", "Margins")))
        }
    }
}
