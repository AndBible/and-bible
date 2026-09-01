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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.CategoryToggleState
import net.bible.sharedcore.ai.ToolCategoryVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.ai.categoryToggleState
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * A single selectable option in a tool's permission control: the [ToolPermission] it sets and its
 * (already-localized) display label.
 */
private data class ToolPermissionOption(val permission: ToolPermission, val label: String)

/**
 * Categorized, collapsible tool-permission list shared by the classic-parity GLOBAL screen
 * (Batch 9d's GlobalToolPermissions, mirrors [net.bible.android.view.activity.ai.GlobalToolPermissionsActivity])
 * and the per-prompt PROMPT screen (Task 6's PromptEdit Permissions tab). Mode is inferred per-tool
 * from [globalDefaultLabelFor] rather than passed as an explicit enum, so a caller could even mix
 * modes per tool (not expected in practice, but keeps the contract simple).
 *
 * **Option sets** (mirrors [net.bible.android.view.activity.ai.ToolPermissionListBuilder]):
 * - Read tools (`!requiresPermission`): [ToolPermission.ENABLED] / [ToolPermission.DISABLED], plus
 *   [ToolPermission.DEFAULT] in PROMPT mode.
 * - Write tools (`requiresPermission`), GLOBAL mode: **3-way** — [ToolPermission.ASK] /
 *   [ToolPermission.ALLOW] / [ToolPermission.DENY] ([ASK][ToolPermission.ASK] is the neutral
 *   "ask every time" default; classic GLOBAL write tools are Ask/Always-allow/Always-deny, never
 *   just a 2-way allow/deny).
 * - Write tools, PROMPT mode: [ToolPermission.DEFAULT] / [ToolPermission.ALLOW] / [ToolPermission.DENY]
 *   (no separate ASK option — [DEFAULT][ToolPermission.DEFAULT] itself may resolve to a global "ask").
 *
 * **PROMPT vs. GLOBAL mode** is decided per-tool by [globalDefaultLabelFor]: a non-null result adds
 * the leading `Default (X)` option (PROMPT mode for that tool); `null` omits it (GLOBAL mode — write
 * tools get the 3-way Ask/Allow/Deny set above instead). [globalDefaultLabelFor] must return the
 * *token* naming the resolved global default, not a pre-formatted label: `"ENABLED"` / `"DISABLED"`
 * ([ToolPermission.ENABLED]/[ToolPermission.DISABLED] `.name`) for a read tool, `"ALLOW"` / `"DENY"`
 * / `"ASK"` ([ToolPermission.ALLOW]/[ToolPermission.DENY]/[ToolPermission.ASK] `.name`) for a write
 * tool. The component maps that token to one of the existing precomposed `tool_option_default_*`
 * strings (`Default (enabled)` / `Default (disabled)` / `Default (allowed)` / `Default (denied)`) —
 * or, for the `ASK` token, the existing classic "Ask (default)" string
 * ([Strings.toolOptionAsk], `R.string.permission_status_default`) — no new resource strings, and
 * callers never format the label themselves.
 *
 * Collapse/expand state is component-local ([remember], keyed by [ToolCategoryVd.id]) — callers
 * don't need to thread it through. All categories start expanded.
 *
 * **Tool description (F34/F37).** Each row shows only the tool's [ToolVd.displayName] plus (when
 * [ToolVd.description] is non-blank) a trailing info [IconButton] — the long description text used
 * to render inline as a `bodySmall` line under the name, which made every row multi-line and pushed
 * the segmented permission control down inconsistently row-to-row. Tapping the icon opens the
 * shared [AbInfoDialog] (title = [ToolVd.displayName], body = [ToolVd.description]). Which tool's
 * dialog is showing is hoisted **here** (component-local [remember], like [collapsed]) rather than
 * threaded through [GlobalToolPermissionsScreen]/`PromptEditScreen`'s Permissions tab — neither
 * caller needs to know or drive this state.
 *
 * **Permission control (F39, Task E3).** The per-tool permission is a compact status icon, not a
 * text control -- the old [androidx.compose.material3.SingleChoiceSegmentedButtonRow] of text
 * labels overflowed once the "Default (enabled)" PROMPT-mode option was added. Read tools
 * ([ToolVd.requiresPermission] `== false`) get a single tappable [IconButton] that cycles through
 * their (2- or 3-item, see [toolOptions]) option list on each tap -- 2-way ENABLED/DISABLED in
 * GLOBAL mode, 3-way DEFAULT/ENABLED/DISABLED in PROMPT mode. Write tools get an [IconButton] that
 * opens a [DropdownMenu] listing every option (icon + full text label, so the "Default (allowed)"/
 * "Default (denied)" hint from [globalDefaultLabelFor] stays reachable as menu-item text once
 * expanded) for direct selection -- see [ReadToolPermissionToggle]/[WriteToolPermissionControl].
 * [permissionIcon] maps each [ToolPermission] to a shape/fill distinguishable icon (never color
 * alone), so it stays legible when [MaterialTheme.colorScheme] is grayscaled by `AbTheme`'s
 * BW/COLOR_EINK modes; each icon's `contentDescription` names the state.
 *
 * @param categories Tool categories in display order, each paired with its tools in display order.
 * @param permissionFor Current [ToolPermission] for a given `toolId` (drives which option is shown
 *   selected in the status icon control).
 * @param globalDefaultLabelFor See above — the resolved-global-default token, or `null` for GLOBAL
 *   mode (no Default option) for that tool.
 * @param onSet Invoked with the tool id and the newly selected [ToolPermission] when the user picks
 *   an option.
 * @param onSetCategoryRead E2/F35/F37: bulk read-tool toggle for a category header, forwarded 1:1 to
 *   `GlobalToolPermissionsController.setCategoryRead`/`PromptEditController.setCategoryRead`. The
 *   control is hidden (mirrors classic's `View.GONE`) when the category has no read tools -- see
 *   [categoryToggleState].
 * @param onSetCategoryWrite E2/F35/F37: bulk write-tool toggle, same shape as [onSetCategoryRead] for
 *   write tools (`setCategoryWrite`).
 * @param initiallyShownToolInfo Test-only hook (mirrors `initiallyHelpDialogOpen` elsewhere in this
 *   package): seeds the info-dialog state so a golden test can capture it open without simulating a
 *   click. Not used by either production caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPermissionList(
    categories: List<Pair<ToolCategoryVd, List<ToolVd>>>,
    permissionFor: (toolId: String) -> ToolPermission,
    globalDefaultLabelFor: (toolId: String) -> String?,
    onSet: (toolId: String, ToolPermission) -> Unit,
    onSetCategoryRead: (categoryId: String, enabled: Boolean) -> Unit,
    onSetCategoryWrite: (categoryId: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initiallyShownToolInfo: ToolVd? = null,
) {
    val strings = LocalStrings.current
    // Keyed by category id; absent = expanded (default-open, matches the classic builder's
    // "collapse only when everything in it is off" heuristic being unnecessary here since callers
    // decide initial visibility via the data they pass in).
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    var infoTool by remember { mutableStateOf(initiallyShownToolInfo) }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        categories.forEachIndexed { index, (category, tools) ->
            if (index > 0) {
                item(key = "divider-${category.id}") { HorizontalDivider() }
            }
            item(key = "header-${category.id}") {
                val isCollapsed = collapsed[category.id] == true
                CategoryHeader(
                    category = category,
                    tools = tools,
                    expanded = !isCollapsed,
                    onToggle = { collapsed[category.id] = !isCollapsed },
                    permissionFor = permissionFor,
                    onSetCategoryRead = onSetCategoryRead,
                    onSetCategoryWrite = onSetCategoryWrite,
                    strings = strings,
                )
            }
            if (collapsed[category.id] != true) {
                items(tools, key = { it.id }) { tool ->
                    ToolPermissionRow(
                        tool = tool,
                        current = permissionFor(tool.id),
                        defaultToken = globalDefaultLabelFor(tool.id),
                        onSet = { onSet(tool.id, it) },
                        onShowInfo = { infoTool = tool },
                        strings = strings,
                    )
                }
            }
        }
    }

    infoTool?.let { tool ->
        AbInfoDialog(
            title = tool.displayName,
            body = tool.description,
            onDismiss = { infoTool = null },
        )
    }
}

/**
 * Category header row: name, then (E2/F35/F37) the bulk read/write toggles -- each hidden when the
 * category has no tools of that kind (mirrors classic `ToolPermissionListBuilder`'s `View.GONE` rule,
 * see [categoryToggleState]) -- then the expand/collapse chevron. [CategoryBulkToggle] renders its
 * own `Modifier.clickable`, which (like classic's separate `CheckBox` child views) consumes its own
 * taps before they reach this row's outer `clickable(onToggle)`, so tapping a bulk toggle does not
 * also expand/collapse the category.
 */
@Composable
private fun CategoryHeader(
    category: ToolCategoryVd,
    tools: List<ToolVd>,
    expanded: Boolean,
    onToggle: () -> Unit,
    permissionFor: (toolId: String) -> ToolPermission,
    onSetCategoryRead: (categoryId: String, enabled: Boolean) -> Unit,
    onSetCategoryWrite: (categoryId: String, enabled: Boolean) -> Unit,
    strings: Strings,
) {
    val readTools = remember(tools) { tools.filterNot { it.requiresPermission } }
    val writeTools = remember(tools) { tools.filter { it.requiresPermission } }
    val readState = categoryToggleState(readTools, permissionFor)
    val writeState = categoryToggleState(writeTools, permissionFor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            category.displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (readState != null) {
            CategoryBulkToggle(
                label = strings.toolCategoryReadLabel,
                state = readState,
                onToggle = { enabled -> onSetCategoryRead(category.id, enabled) },
            )
        }
        if (writeState != null) {
            CategoryBulkToggle(
                label = strings.toolCategoryWriteLabel,
                state = writeState,
                onToggle = { enabled -> onSetCategoryWrite(category.id, enabled) },
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
    }
}

/**
 * One bulk read/write toggle: a [TriStateCheckbox] (so [CategoryToggleState.MIXED] renders as a
 * genuine indeterminate dash, not collapsed to "off" like classic's plain `CheckBox`) plus its label.
 * Tapping either the checkbox or the label toggles ALL rows of that kind: mirrors classic's checkbox
 * semantics -- [CategoryToggleState.ALL_ON] flips OFF, anything else ([CategoryToggleState.MIXED] or
 * [CategoryToggleState.ALL_OFF]) flips ON.
 */
@Composable
private fun CategoryBulkToggle(label: String, state: CategoryToggleState, onToggle: (enabled: Boolean) -> Unit) {
    val toggleableState = when (state) {
        CategoryToggleState.ALL_ON -> ToggleableState.On
        CategoryToggleState.MIXED -> ToggleableState.Indeterminate
        CategoryToggleState.ALL_OFF -> ToggleableState.Off
    }
    val toggle = { onToggle(state != CategoryToggleState.ALL_ON) }
    Row(
        modifier = Modifier.clickable(onClick = toggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = toggleableState, onClick = toggle)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolPermissionRow(
    tool: ToolVd,
    current: ToolPermission,
    defaultToken: String?,
    onSet: (ToolPermission) -> Unit,
    onShowInfo: () -> Unit,
    strings: Strings,
) {
    val options = toolOptions(tool, defaultToken, strings)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    ) {
        // 17f: the choosing affordance sits on the LEADING edge, like a checkbox, and the info
        // button is alone on the trailing edge instead of crowded against the control.
        if (tool.requiresPermission) {
            WriteToolPermissionControl(options = options, current = current, onSet = onSet)
        } else {
            ReadToolPermissionToggle(options = options, current = current, onSet = onSet)
        }
        Text(tool.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (tool.description.isNotBlank()) {
            IconButton(onClick = onShowInfo) {
                Icon(Icons.Outlined.Info, contentDescription = strings.toolDescriptionInfoContentDescription)
            }
        }
    }
}

/**
 * Read-tool control (F39): a single tappable status [IconButton]. Each tap advances to the NEXT
 * option in [options] (wrapping), so this is a genuine ENABLED&#8646;DISABLED toggle in GLOBAL mode
 * (2 options) and a DEFAULT&#8594;ENABLED&#8594;DISABLED&#8594;DEFAULT cycle in PROMPT mode (3
 * options, [options] order from [toolOptions]) -- same option set/order the old segmented row
 * offered, only the presentation (icon vs. text) changed. `contentDescription` names the current
 * state (its already-localized [ToolPermissionOption.label], e.g. "Enabled" or "Default (enabled)").
 */
@Composable
private fun ReadToolPermissionToggle(
    options: List<ToolPermissionOption>,
    current: ToolPermission,
    onSet: (ToolPermission) -> Unit,
) {
    val currentIndex = options.indexOfFirst { it.permission == current }.coerceAtLeast(0)
    val currentOption = options[currentIndex]
    IconButton(onClick = { onSet(options[(currentIndex + 1) % options.size].permission) }) {
        Icon(imageVector = permissionIcon(currentOption.permission), contentDescription = currentOption.label)
    }
}

/**
 * Write-tool control (F39): an [IconButton] showing the current state's icon (`contentDescription`
 * = its label) that opens a [DropdownMenu] listing every option in [options] (icon + full text
 * label) for direct selection -- same 3-way option set [toolOptions] always builds for write tools
 * (ASK/ALLOW/DENY in GLOBAL mode, DEFAULT/ALLOW/DENY in PROMPT mode). This is where the
 * [globalDefaultLabelFor] "Default (allowed)"/"Default (denied)" hint stays reachable as visible
 * menu-item text once the menu is expanded, since the closed-state icon alone can't encode it.
 */
@Composable
private fun WriteToolPermissionControl(
    options: List<ToolPermissionOption>,
    current: ToolPermission,
    onSet: (ToolPermission) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentOption = options.firstOrNull { it.permission == current } ?: options.first()
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = permissionIcon(currentOption.permission), contentDescription = currentOption.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                AbMenuItem(
                    text = option.label,
                    onClick = { onSet(option.permission); expanded = false },
                    icon = { Icon(permissionIcon(option.permission), contentDescription = null) },
                    checkable = true,
                    checked = option.permission == current,
                )
            }
        }
    }
}

/**
 * Maps a [ToolPermission] to a shape/fill distinguishable icon (F39) -- never color alone, so it
 * stays legible in BW/e-ink monochrome: [ToolPermission.ENABLED]/[ToolPermission.ALLOW] = filled
 * check circle (the two "on" states -- read vs. write tools never share an option list, so reusing
 * one icon for both is unambiguous); [ToolPermission.DISABLED] and [ToolPermission.DENY] both map
 * to a blocked/no-entry circle (17f: these are the same user-facing concept — "always block" — on
 * two kinds of tool, and read/write tools never share an option list, so one icon is unambiguous);
 * [ToolPermission.ASK] = a question mark (GLOBAL mode's neutral "ask every time" write default);
 * [ToolPermission.DEFAULT] = a globe ("inherits the global default" -- its specific resolved value
 * is carried in the option's text label, not the icon, per [globalDefaultLabelFor]).
 */
private fun permissionIcon(permission: ToolPermission): ImageVector = when (permission) {
    ToolPermission.ENABLED, ToolPermission.ALLOW -> Icons.Filled.CheckCircle
    ToolPermission.DISABLED, ToolPermission.DENY -> Icons.Filled.Block
    ToolPermission.ASK -> Icons.AutoMirrored.Filled.HelpOutline
    ToolPermission.DEFAULT -> Icons.Filled.Public
}

/**
 * Read tools: [ToolPermission.ENABLED]/[ToolPermission.DISABLED]; PROMPT mode ([defaultToken]
 * non-null) prepends [ToolPermission.DEFAULT] with a label resolved from the token (see
 * [ToolPermissionList] kdoc). Write tools:
 * - PROMPT mode ([defaultToken] non-null): [ToolPermission.DEFAULT] / [ToolPermission.ALLOW] /
 *   [ToolPermission.DENY].
 * - GLOBAL mode ([defaultToken] `== null`): [ToolPermission.ASK] / [ToolPermission.ALLOW] /
 *   [ToolPermission.DENY] (3-way, no Default option — [ASK][ToolPermission.ASK] itself is the
 *   neutral default here).
 */
private fun toolOptions(tool: ToolVd, defaultToken: String?, strings: Strings): List<ToolPermissionOption> =
    buildList {
        if (tool.requiresPermission) {
            if (defaultToken != null) {
                add(ToolPermissionOption(ToolPermission.DEFAULT, defaultOptionLabel(tool.requiresPermission, defaultToken, strings)))
            } else {
                add(ToolPermissionOption(ToolPermission.ASK, strings.toolOptionAsk))
            }
            add(ToolPermissionOption(ToolPermission.ALLOW, strings.toolOptionAllow))
            add(ToolPermissionOption(ToolPermission.DENY, strings.toolOptionDeny))
        } else {
            if (defaultToken != null) {
                add(ToolPermissionOption(ToolPermission.DEFAULT, defaultOptionLabel(tool.requiresPermission, defaultToken, strings)))
            }
            add(ToolPermissionOption(ToolPermission.ENABLED, strings.toolOptionEnabled))
            add(ToolPermissionOption(ToolPermission.DISABLED, strings.toolOptionDisabled))
        }
    }

private fun defaultOptionLabel(requiresPermission: Boolean, token: String, strings: Strings): String =
    if (requiresPermission) {
        when (token) {
            ToolPermission.DENY.name -> strings.toolOptionDefaultDenied
            ToolPermission.ASK.name -> strings.toolOptionAsk
            else -> strings.toolOptionDefaultAllowed
        }
    } else {
        if (token == ToolPermission.DISABLED.name) strings.toolOptionDefaultDisabled else strings.toolOptionDefaultEnabled
    }
