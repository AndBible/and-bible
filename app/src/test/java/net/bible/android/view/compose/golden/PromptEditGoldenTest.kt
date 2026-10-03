package net.bible.android.view.compose.golden

import net.bible.sharedcore.docs.DocsLinks
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptEditData
import net.bible.sharedcore.ai.PromptEditTab
import net.bible.sharedcore.ai.ToolCategoryVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.ai.MaxIterationsSheetContent
import net.bible.sharedui.ai.PromptEditScreen
import net.bible.sharedui.ai.PromptPermissionSheetContent
import net.bible.sharedui.strings.LocalStrings
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
            ToolVd(id = "read_notes", displayName = "Read notes", description = "Read the user's MyNotes", requiresPermission = false, categoryId = readCat.id),
        ),
        writeCat to listOf(
            ToolVd(id = "create_bookmark", displayName = "Create bookmark", description = "Add a bookmark at the current verse", requiresPermission = true, categoryId = writeCat.id),
            ToolVd(id = "delete_note", displayName = "Delete note", description = "Remove a MyNote", requiresPermission = true, categoryId = writeCat.id),
            ToolVd(id = "share_note", displayName = "Share note", description = "Share a MyNote outside the app", requiresPermission = true, categoryId = writeCat.id),
        ),
    )

    private val modelChoices = listOf(
        SettingsItem.Choice("", "Use default"),
        SettingsItem.Choice("gpt-4o", "GPT-4o"),
        SettingsItem.Choice("claude-3-5-sonnet", "Claude 3.5 Sonnet"),
    )

    /** Read tools ENABLED by default, write tools mostly DENY by default (share_note is the neutral
     *  GLOBAL "ask every time" ASK default) -- mirrors a typical global config, and (Task E3/F39) lets
     *  the un-overridden write tools show BOTH DEFAULT-label flavors ("Default (denied)" for
     *  delete_note, "Ask (default)" for share_note) in the PROMPT-mode permission icon control. */
    private val globalToolPermission: (String) -> ToolPermission = { toolId ->
        when (toolId) {
            "get_passage", "search_bible", "read_notes" -> ToolPermission.ENABLED
            "create_bookmark", "delete_note" -> ToolPermission.DENY
            "share_note" -> ToolPermission.ASK
            else -> ToolPermission.DEFAULT
        }
    }

    private fun screen(
        state: PromptEditData,
        tab: PromptEditTab,
        isDirty: Boolean = false,
        canSave: Boolean = true,
        isNew: Boolean = false,
        initiallyHelpDialogOpen: Boolean = false,
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
            globalMaxIterationsLabel = "20",
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
            onSetCategoryRead = { _, _ -> },
            onSetCategoryWrite = { _, _ -> },
            onResetToolPermissions = {},
            onSetModelOverride = {},
            onSetMaxIterations = {},
            onSetSwitch = { _, _ -> },
            onSave = {},
            onDelete = {},
            onCopyToCustomize = {},
            onViewTools = {},
            onBack = {},
            helpBody = "Custom prompts let you define reusable AI instructions, including which tools they may use.",
            helpReadMoreUrl = DocsLinks.page("ai", "custom-prompts"),
            initiallyHelpDialogOpen = initiallyHelpDialogOpen,
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

    // heightDp=1200: name/description/template(minLines=5)/category dropdown/5 context chips
    // (17f)/bibleOnly checkbox/text-transformation chip -- the default viewport clips well before
    // the last row.
    @Test fun prompt_matrix() =
        captureMatrix("PromptEdit", "prompt", heightDp = 1200, content = screen(promptState, PromptEditTab.PROMPT))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun prompt_rtl() =
        captureRtl("PromptEdit", "prompt", heightDp = 1200, content = screen(promptState, PromptEditTab.PROMPT))

    /** textTransformation=true -> availableTabs drops PERMISSIONS; bibleOnly=true also exercises
     *  disabledContexts (17f: WORKSPACE_MENU/NOTE_EDITOR render as disabled [FilterChip]s alongside
     *  the enabled ones -- this is the only fixture in this file where a chip is genuinely
     *  disabled-but-editable, as opposed to [readonly_builtin_prompt] where every control is
     *  disabled by `editable = false` regardless). */
    @Test fun prompt_texttransform() =
        captureGolden(
            "PromptEdit", "prompt_texttransform", EDGE_MODE, heightDp = 1200,
            content = screen(promptState.copy(isTextTransformation = true, bibleOnly = true), PromptEditTab.PROMPT),
        )

    /** Permissions tab reachable (textTransformation=false). Some tools overridden away from the
     *  global default (create_bookmark ALLOW-overridden, read_notes ENABLED-overridden, search_bible
     *  DISABLED-overridden) and some left at DEFAULT (delete_note -> resolves DENY, share_note ->
     *  resolves ASK, get_passage -> resolves ENABLED) so (Task E3/F39) every [ToolPermission] the
     *  icon-based permission control can show is exercised on screen: explicit ENABLED/DISABLED
     *  (read), explicit ALLOW (write), and both DEFAULT label flavors (write "Default (denied)"/
     *  "Ask (default)"). */
    private val permissionsState = promptState.copy(
        permissionMode = "ALWAYS_ASK",
        allowedTools = setOf("create_bookmark", "read_notes"),
        deniedTools = setOf("search_bible"),
    )

    // heightDp=1100: permission-mode dropdown + 2 categories x 3 tools, each a name-row (+ trailing
    // info icon, E1/F34/F37, and E3/F39's icon-based permission control) -- clips at the default
    // viewport otherwise.
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

    // F30: the overflow "Help" item opens an AbInfoDialog (with a "Read more" docs link), replacing
    // classic's CommonUtils.showHelpDialog AlertDialog.
    @Test fun help_matrix() =
        captureMatrix(
            "PromptEdit", "help", heightDp = 1200,
            content = screen(promptState, PromptEditTab.PROMPT, initiallyHelpDialogOpen = true),
        )

    /** Same shape as the Permissions tab's own `permissionModeChoices` construction: a leading
     *  "use default" entry plus the four [net.bible.sharedcore.ai.agentPermissionModeChoices]. */
    private val permissionSheetChoices = listOf(
        SettingsItem.Choice("", "Use default"),
        SettingsItem.Choice("ALWAYS_ASK", "Always ask"),
        SettingsItem.Choice("ASK_ONCE_PER_RUN", "Ask once per run"),
        SettingsItem.Choice("ALLOW_ALL", "Allow all"),
        SettingsItem.Choice("DENY_ALL", "Deny all"),
    )

    /**
     * 17f: [PromptPermissionSheetContent] is the permission tab's new bottom-sheet body (a status
     * strip replaces the old full-width dropdown + "Reset all" button, both now inside this sheet).
     * Captured directly (never the open [androidx.compose.material3.ModalBottomSheet] itself, which
     * hangs Roborazzi -- `SettingsEditorSheetGuardTest` polices this), wrapped in [SheetSurface] for
     * background-colour consistency with every other sheet-content capture in this package.
     *
     * `editable = true` so BOTH things this task adds are visible in one capture: the explanation
     * text under the choice list, and the "Reset to default" button -- a capture of the radio list
     * alone would prove nothing about what changed here (the list itself is unmoved from Task 4).
     * heightDp=500: header + 5 choice rows + explanation text + reset button all fit with margin
     * (the 400dp `AbSheetContentMaxHeight` bound never engages for this short a list).
     */
    @Test fun permission_sheet_matrix() = captureMatrix("PromptEdit", "permission_sheet", heightDp = 500) {
        SheetSurface {
            PromptPermissionSheetContent(
                choices = permissionSheetChoices,
                selectedValue = "ALWAYS_ASK",
                editable = true,
                onSelect = {},
                onResetToolPermissions = {},
                onClose = {},
                strings = LocalStrings.current,
            )
        }
    }

    /**
     * 17f: [MaxIterationsSheetContent] replaces the old bare numeric editor for `max_iterations` --
     * a switch for "use the global setting" plus a number field enabled only when the switch is
     * off. Captured directly (never the open [androidx.compose.material3.ModalBottomSheet], which
     * hangs Roborazzi -- `SettingsEditorSheetGuardTest` polices this), wrapped in [SheetSurface] for
     * background-colour consistency with every other sheet-content capture in this package. Two
     * states, since a single capture wouldn't prove the switch's greying behavior: `current = null`
     * (switch on, field greyed) and `current = 15` (switch off, field showing the override).
     */
    @Test fun maxIterationsSheet_matrix() = captureMatrix("PromptEdit", "max_iterations_sheet") {
        SheetSurface {
            MaxIterationsSheetContent(current = null, globalLabel = "20", onApply = {}, onClose = {})
        }
    }

    @Test fun maxIterationsSheetOverridden_matrix() = captureMatrix("PromptEdit", "max_iterations_sheet_overridden") {
        SheetSurface {
            MaxIterationsSheetContent(current = 15, globalLabel = "20", onApply = {}, onClose = {})
        }
    }
}
