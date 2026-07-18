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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings

/**
 * The read-only "available AI tools" reference screen (mirrors classic
 * [net.bible.android.view.activity.ai.ToolInfoActivity]). Unlike [GlobalToolPermissionsScreen] and
 * [PromptEditScreen]'s Permissions tab, this screen has NO controller: it is a pure function of
 * host-supplied data. The host reads `net.bible.service.llm.tools.ToolRegistry.getAllTools()`
 * directly, splits it once by [ToolVd.requiresPermission] (`false` -> [readTools], `true` ->
 * [writeTools] — mirrors classic's own `allTools.filter { !it.requiresPermission }` /
 * `filter { it.requiresPermission }`), and passes the two lists straight in. No permission
 * state, no edits, no dirty-tracking.
 *
 * A categorized `List<Pair<ToolCategoryVd, List<ToolVd>>>` variant was considered (matching
 * [ToolPermissionList]'s shape) but classic's `ToolInfoActivity` groups only by read/write, not by
 * [net.bible.sharedcore.ai.ToolCategoryVd] — so the simpler flat two-section split is the more
 * faithful port and avoids composing sections-within-sections for no behavioural gain.
 *
 * **Top bar.** Title: [net.bible.sharedui.strings.Strings.viewToolsMenuLabel] (`R.string.ai_available_tools`
 * — the same resource classic's activity sets as its own title). Only a "Help" overflow item
 * ([onHelp]); no save/reset actions exist (nothing here is editable).
 *
 * @param readTools Tools that never require permission (`!requiresPermission`), in display order.
 * @param writeTools Tools gated by the permission system (`requiresPermission`), in display order.
 * @param onUp Up-navigation; called immediately (no dirty state to guard, unlike [GlobalToolPermissionsScreen]).
 * @param onHelp Host callback to show the tool-info help dialog.
 */
@Composable
fun ToolInfoScreen(
    readTools: List<ToolVd>,
    writeTools: List<ToolVd>,
    onUp: () -> Unit,
    onHelp: () -> Unit,
) {
    val strings = LocalStrings.current

    AbScaffold(
        title = strings.viewToolsMenuLabel,
        onNavigateUp = onUp,
        actions = {
            AbOverflowMenu(contentDescription = null) { close ->
                DropdownMenuItem(text = { Text(strings.helpLabel) }, onClick = { close(); onHelp() })
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
            toolInfoSection(strings.aiReadToolsLabel, readTools)
            if (readTools.isNotEmpty() && writeTools.isNotEmpty()) {
                item(key = "divider-read-write") { HorizontalDivider() }
            }
            toolInfoSection(strings.aiWriteToolsLabel, writeTools)
        }
    }
}

private fun LazyListScope.toolInfoSection(heading: String, tools: List<ToolVd>) {
    if (tools.isEmpty()) return
    item(key = "header-$heading") { ToolInfoSectionHeader(heading) }
    items(tools, key = { it.id }) { tool -> ToolInfoRow(tool) }
}

@Composable
private fun ToolInfoSectionHeader(heading: String) {
    Text(
        heading,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ToolInfoRow(tool: ToolVd) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(tool.displayName, style = MaterialTheme.typography.bodyLarge)
        if (tool.description.isNotBlank()) {
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
