package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.ToolCategoryVd
import net.bible.sharedcore.ai.ToolPermGroupVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedui.ai.GlobalToolPermissionsScreen
import net.bible.sharedui.ai.ToolPermissionList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class GlobalToolPermissionsGoldenTest {

    private val bibleCat = ToolCategoryVd(id = "BIBLE", displayName = "Bible tools")
    private val notesCat = ToolCategoryVd(id = "NOTES", displayName = "Notes tools")

    // Read tools ENABLED/DISABLED plus write tools across the GLOBAL 3-state (Ask/Allow/Deny),
    // several non-default so all three segmented options are exercised on screen.
    private val groups = listOf(
        ToolPermGroupVd(
            category = bibleCat,
            tools = listOf(
                ToolVd(id = "get_passage", displayName = "Get passage text", description = "Fetch verse text for the current reference", requiresPermission = false, categoryId = bibleCat.id),
                ToolVd(id = "search_bible", displayName = "Search Bible", description = "Full-text search across the current document", requiresPermission = false, categoryId = bibleCat.id),
                ToolVd(id = "create_bookmark", displayName = "Create bookmark", description = "Add a bookmark at the current verse", requiresPermission = true, categoryId = bibleCat.id),
                ToolVd(id = "delete_note", displayName = "Delete note", description = "Remove a MyNote", requiresPermission = true, categoryId = bibleCat.id),
            ),
        ),
        ToolPermGroupVd(
            category = notesCat,
            tools = listOf(
                ToolVd(id = "read_notes", displayName = "Read notes", description = "Read the user's MyNotes", requiresPermission = false, categoryId = notesCat.id),
                ToolVd(id = "edit_note", displayName = "Edit note", description = "Modify an existing MyNote", requiresPermission = true, categoryId = notesCat.id),
            ),
        ),
    )

    // get_passage=ENABLED (default), search_bible=DISABLED (non-default), create_bookmark=ALLOW
    // (non-default), delete_note=DENY (non-default), read_notes=ENABLED (default), edit_note=ASK
    // (the neutral GLOBAL default) -- so Ask/Allow/Deny all appear selected somewhere on screen.
    // (Task E3/F39: this also exercises every icon the permission control's [permissionIcon] draws
    // except DEFAULT, which only exists in PROMPT mode -- see PromptEditGoldenTest for that one.)
    private val permissions = mapOf(
        "get_passage" to ToolPermission.ENABLED,
        "search_bible" to ToolPermission.DISABLED,
        "create_bookmark" to ToolPermission.ALLOW,
        "delete_note" to ToolPermission.DENY,
        "read_notes" to ToolPermission.ENABLED,
        "edit_note" to ToolPermission.ASK,
    )

    private fun screen(isDirty: Boolean = true, initiallyHelpDialogOpen: Boolean = false) = @androidx.compose.runtime.Composable {
        GlobalToolPermissionsScreen(
            groups = groups,
            permissionFor = { toolId -> permissions[toolId] ?: ToolPermission.ASK },
            isDirty = isDirty,
            onUp = {},
            onSetPermission = { _, _ -> },
            onSetCategoryRead = { _, _ -> },
            onSetCategoryWrite = { _, _ -> },
            onResetAll = {},
            onSave = {},
            helpBody = "Set default read/write permissions for AI tools across all prompts.",
            helpReadMoreUrl = DocsLinks.page("ai", "setting-permissions"),
            initiallyHelpDialogOpen = initiallyHelpDialogOpen,
        )
    }

    // heightDp=1100: 2 categories x (2-4 tools each), each tool a name-row (+ trailing info icon
    // when it has a description, E1/F34/F37) plus a segmented row -- the default viewport clips
    // before the second category's tools.
    @Test fun populated_matrix() =
        captureMatrix("GlobalToolPermissions", "populated", heightDp = 1100, content = screen())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun populated_rtl() =
        captureRtl("GlobalToolPermissions", "populated", heightDp = 1100, content = screen())

    // F30: the overflow "Help" item opens an AbInfoDialog (with a "Read more" docs link), replacing
    // classic's CommonUtils.showHelpDialog AlertDialog.
    @Test fun help_matrix() =
        captureMatrix("GlobalToolPermissions", "help", heightDp = 1100, content = screen(initiallyHelpDialogOpen = true))

    // E1/F34/F37: the trailing info icon on a tool row opens an AbInfoDialog with that tool's
    // description, replacing the old always-visible inline bodySmall description line. Renders
    // ToolPermissionList directly (not through GlobalToolPermissionsScreen, which has no concept of
    // this per-tool dialog state -- it's hoisted inside ToolPermissionList itself) using the
    // test-only initiallyShownToolInfo hook to seed the dialog open without simulating a click.
    @Test fun tool_info_dialog_matrix() =
        captureMatrix("GlobalToolPermissions", "tool_info", heightDp = 1100) {
            ToolPermissionList(
                categories = groups.map { it.category to it.tools },
                permissionFor = { toolId -> permissions[toolId] ?: ToolPermission.ASK },
                globalDefaultLabelFor = { null },
                onSet = { _, _ -> },
                onSetCategoryRead = { _, _ -> },
                onSetCategoryWrite = { _, _ -> },
                initiallyShownToolInfo = groups.first().tools.first(),
            )
        }

    // E2/F35/F37: dedicated capture of the category header's bulk read/write controls, exercising
    // both CategoryPermissionState.Uniform and CategoryPermissionState.Mixed distinctly across
    // BIBLE's own read+write pair (BIBLE has both 2 read + 2 write tools, so both controls render
    // side-by-side on the SAME header): BIBLE read=Uniform(ENABLED) (get_passage+search_bible both
    // ENABLED -> the ENABLED/ALLOW icon), BIBLE write=Mixed (create_bookmark=ASK, delete_note=DENY
    // -> the neutral "mixed" dash icon); NOTES read=Uniform(DISABLED) (read_notes=DISABLED -> the
    // DISABLED/DENY icon), NOTES write=Uniform(ALLOW) (edit_note=ALLOW -> the ENABLED/ALLOW icon).
    @Test fun category_bulk_toggles_matrix() =
        captureMatrix("GlobalToolPermissions", "bulk_toggles", heightDp = 1100) {
            val bulkPermissions = mapOf(
                "get_passage" to ToolPermission.ENABLED,
                "search_bible" to ToolPermission.ENABLED,
                "create_bookmark" to ToolPermission.ASK,
                "delete_note" to ToolPermission.DENY,
                "read_notes" to ToolPermission.DISABLED,
                "edit_note" to ToolPermission.ALLOW,
            )
            ToolPermissionList(
                categories = groups.map { it.category to it.tools },
                permissionFor = { toolId -> bulkPermissions[toolId] ?: ToolPermission.ASK },
                globalDefaultLabelFor = { null },
                onSet = { _, _ -> },
                onSetCategoryRead = { _, _ -> },
                onSetCategoryWrite = { _, _ -> },
            )
        }
}
