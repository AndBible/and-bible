package net.bible.android.view.compose.golden

import net.bible.sharedcore.docs.DocsLinks
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedui.ai.ToolInfoScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ToolInfoGoldenTest {

    // ToolInfoScreen splits only by requiresPermission (no per-category grouping), so categoryId
    // is irrelevant here -- kept a plausible value for realism.
    private val readTools = listOf(
        ToolVd(id = "get_passage", displayName = "Get passage text", description = "Fetch verse text for the current reference", requiresPermission = false, categoryId = "BIBLE"),
        ToolVd(id = "search_bible", displayName = "Search Bible", description = "Full-text search across the current document", requiresPermission = false, categoryId = "BIBLE"),
        ToolVd(id = "read_notes", displayName = "Read notes", description = "Read the user's MyNotes", requiresPermission = false, categoryId = "NOTES"),
    )
    private val writeTools = listOf(
        ToolVd(id = "create_bookmark", displayName = "Create bookmark", description = "Add a bookmark at the current verse", requiresPermission = true, categoryId = "BIBLE"),
        ToolVd(id = "delete_note", displayName = "Delete note", description = "Remove a MyNote", requiresPermission = true, categoryId = "NOTES"),
    )

    private fun screen(
        readTools: List<ToolVd> = this.readTools,
        writeTools: List<ToolVd> = this.writeTools,
        initiallyHelpDialogOpen: Boolean = false,
    ) =
        @androidx.compose.runtime.Composable {
            ToolInfoScreen(
                readTools = readTools,
                writeTools = writeTools,
                onUp = {},
                helpBody = "This screen lists the tools an AI prompt may use, split into read-only and write-capable tools.",
                helpReadMoreUrl = DocsLinks.page("ai", "ai-tools"),
                initiallyHelpDialogOpen = initiallyHelpDialogOpen,
            )
        }

    @Test fun populated_matrix() =
        captureMatrix("ToolInfo", "populated", heightDp = 700, content = screen())

    /** No write tools registered at all -- only the read-tools section + header render, no divider. */
    @Test fun readonly_none_writable() =
        captureGolden("ToolInfo", "readonly", EDGE_MODE, content = screen(writeTools = emptyList()))

    // F30: the overflow "Help" item opens an AbInfoDialog (with a "Read more" docs link), replacing
    // classic's CommonUtils.showHelpDialog AlertDialog.
    @Test fun help_matrix() =
        captureMatrix("ToolInfo", "help", heightDp = 700, content = screen(initiallyHelpDialogOpen = true))
}
