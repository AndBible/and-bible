/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.PromptAdvancedSwitchKeys
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptContextIds
import net.bible.sharedcore.ai.PromptEditData
import net.bible.sharedcore.ai.PromptEditTab
import net.bible.sharedcore.ai.ToolCategoryVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.ai.agentPermissionModeChoices
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.settings.AbSettingsContent
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * The 3-tab prompt editor (mirrors classic `PromptEditActivity` / `prompt_edit.xml`). Fully
 * stateless: every value the tabs render comes from [state] (the controller's current
 * [PromptEditData] snapshot) plus the small set of derived getters the controller exposes
 * separately ([availableTabs], [disabledContexts], [hiddenAdvancedKeys], [isDirty], [canSave],
 * [isReadOnly], [isBuiltIn], [isNew] — mirroring `PromptEditController`'s own split between `state`
 * and its plain-getter derived properties) and the read-only reference data the host resolves via
 * [net.bible.sharedcore.ai.PromptService] ([categories], [toolsByCategory], [modelChoices],
 * [globalToolPermission]). Every mutation is a callback back to the host, which forwards it 1:1 to
 * the matching `PromptEditController` action — this composable owns no domain state, only the two
 * confirm-dialog visibility flags (discard / delete).
 *
 * **Tabs.** [availableTabs] (PROMPT always; PERMISSIONS dropped when `state.isTextTransformation`;
 * ADVANCED always) drives an M3 [PrimaryTabRow] — hidden entirely when there is only one tab (never
 * happens with the controller's current tab set, but keeps the screen correct if that changes).
 * [tab] selects which tab body renders below it; [onSelectTab] forwards the tap to
 * `PromptEditController.selectTab` (which itself validates the tab is in `availableTabs`).
 *
 * **Prompt tab.** Name/description (single-line) + template (multiline, `minLines = 5`)
 * [OutlinedTextField]s, a category [AbDropdownField] (`""` sentinel = no category, matching the
 * `modelChoices`/`permissionMode` convention below), the context checkboxes ([PromptContextIds.ordered],
 * greyed out per [disabledContexts] — i.e. `WORKSPACE_MENU`/`NOTE_EDITOR` while `state.bibleOnly`),
 * a "Bible documents only" checkbox, and a "Text transformation" checkbox (drives
 * `state.isTextTransformation`, which is what makes the Permissions tab disappear and hides four
 * Advanced-tab rows — mirrors classic's `checkTextTransformation`, which sits on this same tab).
 * Every control is disabled together via one `editable = !isReadOnly` flag — a built-in prompt's
 * *only* editable field is the Advanced tab's model override, so Prompt-tab fields stay locked even
 * for `isBuiltIn` (matches `PromptEditController`'s doc: only [net.bible.sharedcore.ai.PromptEditController.setModelOverride]
 * bypasses the read-only guard).
 *
 * **Permissions tab** (only reachable while in [availableTabs]). A permission-mode [AbDropdownField]
 * built from [net.bible.sharedcore.ai.agentPermissionModeChoices] (reused as-is from `AiEnums.kt`)
 * plus a leading `""` = "use default" entry, then [ToolPermissionList] fed [toolsByCategory] /
 * [globalToolPermission] (always PROMPT mode — `globalDefaultLabelFor` always returns the resolved
 * global-default TOKEN, never `null`), then a "Reset all" [TextButton] (hidden, not merely disabled,
 * while read-only — matches classic's `btnResetToolPermissions.visibility = GONE`). [toolPermissionFor]
 * derives each tool's current [ToolPermission] from `state.allowedTools`/`deniedTools` membership,
 * mapped to ALLOW/ENABLED or DENY/DISABLED by [ToolVd.requiresPermission] (see its kdoc). NOTE:
 * unlike the other controls here, [ToolPermissionList] (Task 4) has no `enabled`/read-only concept —
 * its segmented buttons stay visually tappable even in read-only mode. This is safe (the controller's
 * `setToolPermission` no-ops while read-only, so a tap has no effect and `permissionFor` keeps
 * re-rendering the unchanged state), but not a visual match for the rest of the read-only affordance;
 * documented as a known gap rather than reaching into Task 4's shared component from this task.
 *
 * **Advanced tab.** Builds a [SettingsScreenState] straight from [state] (model override
 * [SettingsItem.ListChoiceRow] fed [modelChoices]; `max_iterations` numeric [SettingsItem.TextInputRow];
 * the 5 [PromptAdvancedSwitchKeys] as [SettingsItem.SwitchRow]s) and hands it to [AbSettingsContent]
 * (Task/Batch 9a's generic renderer, scaffold-less variant — see below) with its callbacks routed
 * straight back to [onSetModelOverride]/[onSetMaxIterations]/[onSetSwitch]. Rows named in
 * [hiddenAdvancedKeys] get `visible = false` (still built, just filtered by
 * [SettingsScreenState.visibleItems]) — mirrors classic's `setTextTransformationMode` hiding
 * `max_iterations`/`no_document_creation`/`auto_include_documents`/`auto_include_commentaries`, but
 * NOT `strict_context_matching`/`specify_before_run` (never hidden, per the controller's constant).
 * The model-override row is enabled whenever `!isReadOnly || isBuiltIn` (the controller's built-in
 * override exception); every other Advanced row is enabled only when `!isReadOnly` (matches classic's
 * `setReadOnly(keepModelEditable = isBuiltIn)`, which locks everything else regardless of `isBuiltIn`).
 *
 * Uses [AbSettingsContent] (the scaffold-less counterpart of [AbSettingsScreen], extracted in a
 * Batch 9c fix) rather than [AbSettingsScreen] itself: the tab body already sits under this screen's
 * own (title + tabs) top bar, so wrapping it in another [AbScaffold] would draw a second, redundant
 * M3 app bar. [AbSettingsContent] renders the identical settings list/dialog logic with no top bar.
 *
 * **Top bar.** Title: [PromptEditData.id] `== null` (a new, unsaved prompt) → the generic "New
 * prompt" string (matches classic, which never distinguishes a title further for a new prompt);
 * [isBuiltIn] → "Built-in"; otherwise → "Edit prompt" (classic also shows an add-on module badge
 * with the module name here, but [PromptEditData] carries no `sourceModule` field to reproduce that
 * distinction, so a genuinely read-only, non-built-in add-on prompt falls back to the same generic
 * "Edit prompt" title — a deliberate, documented simplification). A save [IconButton] (check icon,
 * `enabled = canSave` — shown, not hidden, even when it can never fire, so the row layout never
 * shifts) plus an [AbOverflowMenu]: Delete (visible only `!isReadOnly && !isNew` — a brand-new or
 * any read-only prompt has nothing to delete), "Copy to customize" (visible whenever `isReadOnly ||
 * !isNew` — the classic condition, `!isNewPrompt` when editable, always-on when read-only), "View
 * tools" (always visible — a pure host-navigation callback, [onViewTools], this screen never
 * renders its content itself) and "Help" (always visible — opens an [AbInfoDialog] owned by this
 * screen (F30), fed the host-supplied [helpBody]/[helpReadMoreUrl]).
 *
 * **Back / discard.** [onBack] is NOT called directly from the up-navigation icon: tapping it shows
 * an [AbConfirmDialog] ("Discard unsaved changes?") whenever [isDirty], calling [onBack] only on
 * confirm (or immediately, with no dialog, when not dirty) — mirrors classic's
 * `cancelOrConfirmDiscard()`. The delete menu item goes through its own confirm dialog
 * (`deletePromptConfirmMessage`, an existing generic string — no per-prompt name interpolation,
 * unlike classic's parameterized message) before calling [onDelete].
 *
 * No new resource strings: every label here is an existing `R.string.*` newly exposed through
 * [Strings]/`AndroidStrings` (Batch 9c task 6 section) — see that file's diff for the exact mapping.
 *
 * @param state The controller's current editable snapshot.
 * @param tab The active tab (must be a member of [availableTabs]).
 * @param availableTabs Tabs to render in the [PrimaryTabRow] (`PromptEditController.availableTabs`).
 * @param disabledContexts Context ids to grey out on the Prompt tab (`PromptEditController.disabledContexts`).
 * @param hiddenAdvancedKeys Advanced-tab keys to hide (`PromptEditController.hiddenAdvancedKeys`).
 * @param isDirty Whether the snapshot differs from what was loaded (`PromptEditController.isDirty`).
 * @param canSave Whether [onSave] would actually persist anything (`PromptEditController.canSave`).
 * @param isReadOnly `state.isReadOnly`, passed explicitly (not read off [state]) to keep this screen's
 *   read-only gating visibly driven by the same controller getters as [disabledContexts]/[hiddenAdvancedKeys].
 * @param isBuiltIn `state.isBuiltIn`, see [isReadOnly].
 * @param isNew `PromptEditController.isNew` (`state.id == null`).
 * @param categories Prompt categories for the category dropdown (`PromptService.categories()`).
 * @param toolsByCategory Tool categories + tools for the Permissions tab (`PromptService.toolsByCategory()`).
 * @param modelChoices Model-override choices for the Advanced tab (`PromptService.modelChoices()`).
 * @param globalToolPermission Resolves a tool's current global default, for the Permissions tab's
 *   "Default (X)" option (`PromptService.globalToolPermission`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditScreen(
    state: PromptEditData,
    tab: PromptEditTab,
    availableTabs: List<PromptEditTab>,
    disabledContexts: Set<String>,
    hiddenAdvancedKeys: Set<String>,
    isDirty: Boolean,
    canSave: Boolean,
    isReadOnly: Boolean,
    isBuiltIn: Boolean,
    isNew: Boolean,
    categories: List<PromptCategoryVd>,
    toolsByCategory: List<Pair<ToolCategoryVd, List<ToolVd>>>,
    modelChoices: List<SettingsItem.Choice>,
    globalToolPermission: (toolId: String) -> ToolPermission,
    onSelectTab: (PromptEditTab) -> Unit,
    onSetName: (String) -> Unit,
    onSetDescription: (String) -> Unit,
    onSetTemplate: (String) -> Unit,
    onSetCategory: (String?) -> Unit,
    onToggleContext: (String) -> Unit,
    onSetBibleOnly: (Boolean) -> Unit,
    onSetTextTransformation: (Boolean) -> Unit,
    onSetPermissionMode: (String?) -> Unit,
    onSetToolPermission: (toolId: String, ToolPermission) -> Unit,
    onResetToolPermissions: () -> Unit,
    onSetModelOverride: (String?) -> Unit,
    onSetMaxIterations: (Int?) -> Unit,
    onSetSwitch: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCopyToCustomize: () -> Unit,
    onViewTools: () -> Unit,
    onBack: () -> Unit,
    helpBody: String,
    helpReadMoreUrl: String,
    initiallyHelpDialogOpen: Boolean = false,
) {
    val strings = LocalStrings.current
    val editable = !isReadOnly
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(initiallyHelpDialogOpen) }

    val title = when {
        isNew -> strings.newPrompt
        isBuiltIn -> strings.promptEditTitleBuiltIn
        else -> strings.promptEditTitleEdit
    }
    val requestBack: () -> Unit = { if (isDirty) showDiscardConfirm = true else onBack() }

    AbScaffold(
        title = title,
        onNavigateUp = requestBack,
        actions = {
            IconButton(onClick = onSave, enabled = canSave) {
                Icon(Icons.Filled.Check, contentDescription = strings.okay, modifier = Modifier.size(AbActionIconSize))
            }
            AbOverflowMenu(contentDescription = null) { close ->
                if (!isReadOnly && !isNew) {
                    DropdownMenuItem(text = { Text(strings.deleteLabel) }, onClick = { close(); showDeleteConfirm = true })
                }
                if (isReadOnly || !isNew) {
                    DropdownMenuItem(text = { Text(strings.copyToCustomizeLabel) }, onClick = { close(); onCopyToCustomize() })
                }
                DropdownMenuItem(text = { Text(strings.viewToolsMenuLabel) }, onClick = { close(); onViewTools() })
                DropdownMenuItem(text = { Text(strings.helpLabel) }, onClick = { close(); showHelp = true })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (availableTabs.size > 1) {
                PrimaryTabRow(selectedTabIndex = availableTabs.indexOf(tab).coerceAtLeast(0)) {
                    availableTabs.forEach { t ->
                        Tab(selected = t == tab, onClick = { onSelectTab(t) }, text = { Text(tabLabel(t, strings)) })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    PromptEditTab.PROMPT -> PromptTabContent(
                        state = state,
                        disabledContexts = disabledContexts,
                        categories = categories,
                        editable = editable,
                        onSetName = onSetName,
                        onSetDescription = onSetDescription,
                        onSetTemplate = onSetTemplate,
                        onSetCategory = onSetCategory,
                        onToggleContext = onToggleContext,
                        onSetBibleOnly = onSetBibleOnly,
                        onSetTextTransformation = onSetTextTransformation,
                        strings = strings,
                    )
                    PromptEditTab.PERMISSIONS -> PermissionsTabContent(
                        state = state,
                        toolsByCategory = toolsByCategory,
                        globalToolPermission = globalToolPermission,
                        editable = editable,
                        onSetPermissionMode = onSetPermissionMode,
                        onSetToolPermission = onSetToolPermission,
                        onResetToolPermissions = onResetToolPermissions,
                        strings = strings,
                    )
                    PromptEditTab.ADVANCED -> AdvancedTabContent(
                        state = state,
                        hiddenAdvancedKeys = hiddenAdvancedKeys,
                        isReadOnly = isReadOnly,
                        isBuiltIn = isBuiltIn,
                        modelChoices = modelChoices,
                        onSetModelOverride = onSetModelOverride,
                        onSetMaxIterations = onSetMaxIterations,
                        onSetSwitch = onSetSwitch,
                    )
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AbConfirmDialog(
            title = null,
            message = strings.discardChangesConfirmation,
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = { showDiscardConfirm = false; onBack() },
            onDismiss = { showDiscardConfirm = false },
        )
    }
    if (showDeleteConfirm) {
        AbConfirmDialog(
            title = null,
            message = strings.deletePromptConfirmMessage,
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
    if (showHelp) {
        AbInfoDialog(
            title = strings.helpLabel,
            body = helpBody,
            onDismiss = { showHelp = false },
            readMoreLabel = strings.helpReadMoreLink,
            readMoreUrl = helpReadMoreUrl,
        )
    }
}

private fun tabLabel(tab: PromptEditTab, strings: Strings): String = when (tab) {
    PromptEditTab.PROMPT -> strings.promptTabPrompt
    PromptEditTab.PERMISSIONS -> strings.promptTabPermissions
    PromptEditTab.ADVANCED -> strings.promptTabAdvanced
}

private fun contextLabel(contextId: String, strings: Strings): String = when (contextId) {
    "VERSE_SELECTION" -> strings.promptContextVerseSelection
    "TEXT_SELECTION" -> strings.promptContextTextSelection
    "WINDOW_MENU" -> strings.promptContextWindowMenu
    "WORKSPACE_MENU" -> strings.promptContextWorkspaceMenu
    "NOTE_EDITOR" -> strings.promptContextNoteEditor
    else -> contextId
}

@Composable
private fun PromptTabContent(
    state: PromptEditData,
    disabledContexts: Set<String>,
    categories: List<PromptCategoryVd>,
    editable: Boolean,
    onSetName: (String) -> Unit,
    onSetDescription: (String) -> Unit,
    onSetTemplate: (String) -> Unit,
    onSetCategory: (String?) -> Unit,
    onToggleContext: (String) -> Unit,
    onSetBibleOnly: (Boolean) -> Unit,
    onSetTextTransformation: (Boolean) -> Unit,
    strings: Strings,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onSetName,
            label = { Text(strings.promptNameLabel) },
            enabled = editable,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.description,
            onValueChange = onSetDescription,
            label = { Text(strings.promptDescriptionLabel) },
            enabled = editable,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.template,
            onValueChange = onSetTemplate,
            label = { Text(strings.promptTemplateLabel) },
            enabled = editable,
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AbDropdownField(
            label = strings.promptCategoryLabel,
            selected = state.categoryId ?: "",
            options = listOf("") + categories.map { it.id },
            optionLabel = { id ->
                if (id.isEmpty()) strings.categoryNoneLabel else categories.firstOrNull { it.id == id }?.name ?: id
            },
            onSelect = { id -> onSetCategory(id.ifEmpty { null }) },
            enabled = editable,
            horizontalPadding = 0.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            strings.promptShowInLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        PromptContextIds.ordered.forEach { contextId ->
            LabeledCheckboxRow(
                label = contextLabel(contextId, strings),
                checked = contextId in state.contexts,
                enabled = editable && contextId !in disabledContexts,
                onCheckedChange = { onToggleContext(contextId) },
            )
        }
        Spacer(Modifier.height(8.dp))
        LabeledCheckboxRow(
            label = strings.promptBibleOnlyLabel,
            checked = state.bibleOnly,
            enabled = editable,
            onCheckedChange = onSetBibleOnly,
        )
        LabeledCheckboxRow(
            label = strings.promptIsTextTransformationLabel,
            summary = strings.promptIsTextTransformationDescription,
            checked = state.isTextTransformation,
            enabled = editable,
            onCheckedChange = onSetTextTransformation,
        )
    }
}

@Composable
private fun LabeledCheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .then(if (enabled) Modifier else Modifier.alpha(0.38f))
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp),
            )
        }
    }
}

/** Read tools: `state.allowedTools`/`deniedTools` membership -> ENABLED/DISABLED; write tools
 *  (`requiresPermission`) -> ALLOW/DENY; absent from both -> DEFAULT. Mirrors
 *  `PromptEditController.setToolPermission`'s inverse mapping. */
private fun toolPermissionFor(state: PromptEditData, tool: ToolVd): ToolPermission = when {
    tool.id in state.allowedTools -> if (tool.requiresPermission) ToolPermission.ALLOW else ToolPermission.ENABLED
    tool.id in state.deniedTools -> if (tool.requiresPermission) ToolPermission.DENY else ToolPermission.DISABLED
    else -> ToolPermission.DEFAULT
}

@Composable
private fun PermissionsTabContent(
    state: PromptEditData,
    toolsByCategory: List<Pair<ToolCategoryVd, List<ToolVd>>>,
    globalToolPermission: (toolId: String) -> ToolPermission,
    editable: Boolean,
    onSetPermissionMode: (String?) -> Unit,
    onSetToolPermission: (toolId: String, ToolPermission) -> Unit,
    onResetToolPermissions: () -> Unit,
    strings: Strings,
) {
    val toolsById = remember(toolsByCategory) { toolsByCategory.flatMap { it.second }.associateBy { it.id } }
    val permissionModeChoices = remember(strings) {
        listOf(SettingsItem.Choice("", strings.promptPermissionUseDefault)) +
            agentPermissionModeChoices(
                mapOf(
                    "ALWAYS_ASK" to strings.permissionAlwaysAsk,
                    "ASK_ONCE_PER_RUN" to strings.permissionAskOncePerRun,
                    "ALLOW_ALL" to strings.permissionAllowAll,
                    "DENY_ALL" to strings.permissionDenyAll,
                ),
            )
    }

    Column(Modifier.fillMaxSize()) {
        AbDropdownField(
            label = strings.promptPermissionModeLabel,
            selected = state.permissionMode ?: "",
            options = permissionModeChoices.map { it.value },
            optionLabel = { v -> permissionModeChoices.firstOrNull { it.value == v }?.label ?: v },
            onSelect = { v -> onSetPermissionMode(v.ifEmpty { null }) },
            enabled = editable,
        )
        ToolPermissionList(
            categories = toolsByCategory,
            permissionFor = { toolId -> toolsById[toolId]?.let { toolPermissionFor(state, it) } ?: ToolPermission.DEFAULT },
            globalDefaultLabelFor = { toolId -> globalToolPermission(toolId).name },
            onSet = onSetToolPermission,
            modifier = Modifier.weight(1f),
        )
        if (editable) {
            TextButton(
                onClick = onResetToolPermissions,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) { Text(strings.resetToolPermissionsLabel) }
        }
    }
}

@Composable
private fun AdvancedTabContent(
    state: PromptEditData,
    hiddenAdvancedKeys: Set<String>,
    isReadOnly: Boolean,
    isBuiltIn: Boolean,
    modelChoices: List<SettingsItem.Choice>,
    onSetModelOverride: (String?) -> Unit,
    onSetMaxIterations: (Int?) -> Unit,
    onSetSwitch: (String, Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    val modelEnabled = !isReadOnly || isBuiltIn
    val otherEnabled = !isReadOnly

    val settingsState = SettingsScreenState(
        title = "",
        items = listOf(
            SettingsItem.ListChoiceRow(
                key = "model_override",
                title = strings.promptModelOverrideLabel,
                entries = modelChoices,
                selectedValue = state.modelOverrideId ?: "",
                enabled = modelEnabled,
            ),
            SettingsItem.TextInputRow(
                key = "max_iterations",
                title = strings.promptMaxIterationsLabel,
                summary = state.maxIterations?.toString() ?: strings.promptMaxIterationsHint,
                value = state.maxIterations?.toString() ?: "",
                numeric = true,
                visible = "max_iterations" !in hiddenAdvancedKeys,
                enabled = otherEnabled,
            ),
            SettingsItem.SwitchRow(
                key = PromptAdvancedSwitchKeys.STRICT_CONTEXT_MATCHING,
                title = strings.promptStrictContextMatchingLabel,
                summary = strings.promptStrictContextMatchingDescription,
                checked = state.strictContextMatching,
                enabled = otherEnabled,
            ),
            SettingsItem.SwitchRow(
                key = PromptAdvancedSwitchKeys.SPECIFY_BEFORE_RUN,
                title = strings.promptSpecifyBeforeRunLabel,
                summary = strings.promptSpecifyBeforeRunDescription,
                checked = state.specifyBeforeRun,
                enabled = otherEnabled,
            ),
            SettingsItem.SwitchRow(
                key = PromptAdvancedSwitchKeys.NO_DOCUMENT_CREATION,
                title = strings.promptNoDocumentCreationLabel,
                summary = strings.promptNoDocumentCreationDescription,
                checked = state.noDocumentCreation,
                visible = PromptAdvancedSwitchKeys.NO_DOCUMENT_CREATION !in hiddenAdvancedKeys,
                enabled = otherEnabled,
            ),
            SettingsItem.SwitchRow(
                key = PromptAdvancedSwitchKeys.AUTO_INCLUDE_DOCUMENTS,
                title = strings.promptAutoIncludeDocumentsLabel,
                summary = strings.promptAutoIncludeDocumentsDescription,
                checked = state.autoIncludeDocuments,
                visible = PromptAdvancedSwitchKeys.AUTO_INCLUDE_DOCUMENTS !in hiddenAdvancedKeys,
                enabled = otherEnabled,
            ),
            SettingsItem.SwitchRow(
                key = PromptAdvancedSwitchKeys.AUTO_INCLUDE_COMMENTARIES,
                title = strings.promptAutoIncludeCommentariesLabel,
                summary = strings.promptAutoIncludeCommentariesDescription,
                checked = state.autoIncludeCommentaries,
                visible = PromptAdvancedSwitchKeys.AUTO_INCLUDE_COMMENTARIES !in hiddenAdvancedKeys,
                enabled = otherEnabled,
            ),
        ),
    )

    AbSettingsContent(
        state = settingsState,
        onSwitch = onSetSwitch,
        onListChoice = { key, value -> if (key == "model_override") onSetModelOverride(value.ifEmpty { null }) },
        onTextInput = { key, value -> if (key == "max_iterations") onSetMaxIterations(value.trim().toIntOrNull()) },
        onNavigate = {},
    )
}
