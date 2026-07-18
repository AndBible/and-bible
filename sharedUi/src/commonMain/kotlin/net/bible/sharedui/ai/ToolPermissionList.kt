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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.ToolCategoryVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedui.components.AbInfoDialog
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
 * E-ink/monochrome: selection is shown by [SegmentedButton]'s default checkmark icon (drawn only
 * when `selected`), never by color alone, so it stays legible when [MaterialTheme.colorScheme] is
 * grayscaled by `AbTheme`'s BW/COLOR_EINK modes.
 *
 * @param categories Tool categories in display order, each paired with its tools in display order.
 * @param permissionFor Current [ToolPermission] for a given `toolId` (drives which segmented option
 *   is selected).
 * @param globalDefaultLabelFor See above — the resolved-global-default token, or `null` for GLOBAL
 *   mode (no Default option) for that tool.
 * @param onSet Invoked with the tool id and the newly selected [ToolPermission] when the user picks
 *   an option.
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
                    expanded = !isCollapsed,
                    onToggle = { collapsed[category.id] = !isCollapsed },
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

@Composable
private fun CategoryHeader(category: ToolCategoryVd, expanded: Boolean, onToggle: () -> Unit) {
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
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(tool.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (tool.description.isNotBlank()) {
                IconButton(onClick = onShowInfo) {
                    Icon(Icons.Outlined.Info, contentDescription = strings.toolDescriptionInfoContentDescription)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = current == option.permission,
                    onClick = { onSet(option.permission) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(option.label) }
            }
        }
    }
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
