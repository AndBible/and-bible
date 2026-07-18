package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptEditData
import net.bible.sharedcore.ai.PromptEditTab
import net.bible.sharedcore.ai.ToolCategoryVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.ai.PromptEditScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class PromptEditGoldenTest {

    private val categories = listOf(
        PromptCategoryVd(id = "cat-summary", name = "Summarization", isBuiltIn = false, isHidden = false),
        PromptCategoryVd(id = "cat-study", name = "Study tools", isBuiltIn = true, isHidden = false),
    )

    private val readCat = ToolCategoryVd(id = "READ", displayName = "Reading tools")
    private val writeCat = ToolCategoryVd(id = "WRITE", displayName = "Writing tools")
    private val toolsByCategory = listOf(
        readCat to listOf(
            ToolVd(id = "get_passage", displayName = "Get passage text", description = "Fetch verse text for the current reference", requiresPermission = false, categoryId = readCat.id),
            ToolVd(id = "search_bible", displayName = "Search Bible", description = "Full-text search across the current document", requiresPermission = false, categoryId = readCat.id),
        ),
        writeCat to listOf(
            ToolVd(id = "create_bookmark", displayName = "Create bookmark", description = "Add a bookmark at the current verse", requiresPermission = true, categoryId = writeCat.id),
            ToolVd(id = "delete_note", displayName = "Delete note", description = "Remove a MyNote", requiresPermission = true, categoryId = writeCat.id),
        ),
    )

    private val modelChoices = listOf(
        SettingsItem.Choice("", "Use default"),
        SettingsItem.Choice("gpt-4o", "GPT-4o"),
        SettingsItem.Choice("claude-3-5-sonnet", "Claude 3.5 Sonnet"),
    )

    /** Read tools ENABLED by default, write tools DENY by default -- mirrors a typical global config. */
    private val globalToolPermission: (String) -> ToolPermission = { toolId ->
        when (toolId) {
            "get_passage", "search_bible" -> ToolPermission.ENABLED
            "create_bookmark", "delete_note" -> ToolPermission.DENY
            else -> ToolPermission.DEFAULT
        }
    }

    private fun screen(
        state: PromptEditData,
        tab: PromptEditTab,
        isDirty: Boolean = false,
        canSave: Boolean = true,
        isNew: Boolean = false,
    ) = @androidx.compose.runtime.Composable {
        val availableTabs = if (state.isTextTransformation) {
            listOf(PromptEditTab.PROMPT, PromptEditTab.ADVANCED)
        } else {
            listOf(PromptEditTab.PROMPT, PromptEditTab.PERMISSIONS, PromptEditTab.ADVANCED)
        }
        val disabledContexts = if (state.bibleOnly) setOf("WORKSPACE_MENU", "NOTE_EDITOR") else emptySet()
        val hiddenAdvancedKeys = if (state.isTextTransformation) {
            setOf("max_iterations", "no_document_creation", "auto_include_documents", "auto_include_commentaries")
        } else {
            emptySet()
        }
        PromptEditScreen(
            state = state,
            tab = tab,
            availableTabs = availableTabs,
            disabledContexts = disabledContexts,
            hiddenAdvancedKeys = hiddenAdvancedKeys,
            isDirty = isDirty,
            canSave = canSave,
            isReadOnly = state.isReadOnly,
            isBuiltIn = state.isBuiltIn,
            isNew = isNew,
            categories = categories,
            toolsByCategory = toolsByCategory,
            modelChoices = modelChoices,
            globalToolPermission = globalToolPermission,
            onSelectTab = {},
            onSetName = {},
            onSetDescription = {},
            onSetTemplate = {},
            onSetCategory = {},
            onToggleContext = {},
            onSetBibleOnly = {},
            onSetTextTransformation = {},
            onSetPermissionMode = {},
            onSetToolPermission = { _, _ -> },
            onResetToolPermissions = {},
            onSetModelOverride = {},
            onSetMaxIterations = {},
            onSetSwitch = { _, _ -> },
            onSave = {},
            onDelete = {},
            onCopyToCustomize = {},
            onViewTools = {},
            onHelp = {},
            onBack = {},
        )
    }

    private val promptState = PromptEditData(
        id = "p-1", name = "Explain passage", description = "Explains the selected passage in plain language",
        template = "You are a helpful Bible study assistant. Explain the following passage in plain language:\n\n{{passage}}",
        categoryId = "cat-summary", contexts = setOf("VERSE_SELECTION", "TEXT_SELECTION"),
        isTextTransformation = false, permissionMode = null, allowedTools = emptySet(), deniedTools = emptySet(),
        modelOverrideId = null, maxIterations = 10, strictContextMatching = false, specifyBeforeRun = false,
        noDocumentCreation = false, autoIncludeDocuments = true, autoIncludeCommentaries = false,
        isReadOnly = false, isBuiltIn = false, bibleOnly = false,
    )

    // heightDp=1200: name/description/template(minLines=5)/category dropdown/5 context checkboxes/
    // 2 more checkboxes -- the default viewport clips well before the last checkbox.
    @Test fun prompt_matrix() =
        captureMatrix("PromptEdit", "prompt", heightDp = 1200, content = screen(promptState, PromptEditTab.PROMPT))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun prompt_rtl() =
        captureRtl("PromptEdit", "prompt", heightDp = 1200, content = screen(promptState, PromptEditTab.PROMPT))

    /** textTransformation=true -> availableTabs drops PERMISSIONS; disabledContexts/hiddenAdvancedKeys
     *  no longer apply here (bibleOnly=false), but this exercises the tab-availability branch. */
    @Test fun prompt_texttransform() =
        captureGolden(
            "PromptEdit", "prompt_texttransform", EDGE_MODE, heightDp = 1200,
            content = screen(promptState.copy(isTextTransformation = true), PromptEditTab.PROMPT),
        )

    /** Permissions tab reachable (textTransformation=false). Two tools overridden away from the
     *  global default (create_bookmark ALLOW-overridden, search_bible DISABLED-overridden) so both
     *  the "Default (X)" option and an explicit override render. */
    private val permissionsState = promptState.copy(
        permissionMode = "ALWAYS_ASK",
        allowedTools = setOf("create_bookmark"),
        deniedTools = setOf("search_bible"),
    )

    // heightDp=1100: permission-mode dropdown + 2 categories x 2 tools, each with a description line
    // and a segmented-button row -- clips at the default viewport otherwise.
    @Test fun permissions_matrix() =
        captureMatrix("PromptEdit", "permissions", heightDp = 1100, content = screen(permissionsState, PromptEditTab.PERMISSIONS))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun permissions_rtl() =
        captureRtl("PromptEdit", "permissions", heightDp = 1100, content = screen(permissionsState, PromptEditTab.PERMISSIONS))

    /** Advanced tab: model override + max_iterations + 5 switch rows, all enabled (not read-only).
     *  Also the case verifying no double app bar renders under the PrimaryTabRow (AbSettingsContent,
     *  not AbSettingsScreen, is used here -- see PromptEditScreen kdoc). */
    @Test fun advanced_matrix() =
        captureMatrix("PromptEdit", "advanced", heightDp = 900, content = screen(promptState, PromptEditTab.ADVANCED))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun advanced_rtl() =
        captureRtl("PromptEdit", "advanced", heightDp = 900, content = screen(promptState, PromptEditTab.ADVANCED))

    /** textTransformation=true hides max_iterations/no_document_creation/auto_include_documents/
     *  auto_include_commentaries, leaving only model override + strict_context_matching +
     *  specify_before_run. */
    @Test fun advanced_texttransform() =
        captureGolden(
            "PromptEdit", "advanced_texttransform", EDGE_MODE, heightDp = 900,
            content = screen(promptState.copy(isTextTransformation = true), PromptEditTab.ADVANCED),
        )

    /** Read-only BUILT-IN prompt on the Prompt tab: every field greyed out (isBuiltIn's only
     *  editable field, the Advanced model override, isn't visible on this tab). Title -> "Built-in". */
    @Test fun readonly_builtin_prompt() =
        captureGolden(
            "PromptEdit", "readonly_builtin", EDGE_MODE, heightDp = 1200,
            content = screen(
                promptState.copy(isReadOnly = true, isBuiltIn = true, modelOverrideId = "gpt-4o"),
                PromptEditTab.PROMPT,
                canSave = false,
            ),
        )
}
