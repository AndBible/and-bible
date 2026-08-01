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
package net.bible.android.view.activity.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.ai.ToolInfoScreen

/**
 * Compose host for the read-only "available AI tools" reference screen — the new-path twin of classic
 * [ToolInfoActivity]. Stateless: it has no controller/service. It reads
 * [ToolRegistry.getAllTools] directly, splits it by [Tool.requiresPermission] (`false` → read tools,
 * `true` → write tools — exactly as classic's `allTools.filter { !it.requiresPermission }` /
 * `filter { it.requiresPermission }`), maps each to a [ToolVd], and hands the two lists to
 * [ToolInfoScreen]. Only Help and Up (`finish()`) are interactive. The help dialog (F30) is owned by
 * [ToolInfoScreen] itself as an `AbInfoDialog` — this host only supplies the Android-resource-backed
 * help body text and the full "Read more" docs URL.
 */
class ToolInfoComposeActivity : ActivityBase() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AbAppTheme {
                    val allTools = remember { ToolRegistry.getAllTools() }
                    val readTools = remember(allTools) { allTools.filter { !it.requiresPermission }.map { it.toToolVd() } }
                    val writeTools = remember(allTools) { allTools.filter { it.requiresPermission }.map { it.toToolVd() } }

                    ToolInfoScreen(
                        readTools = readTools,
                        writeTools = writeTools,
                        onUp = { finish() },
                        helpBody = getString(R.string.help_tool_info_text),
                        helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#ai-tools",
                    )
            }
        }
    }

    private fun Tool.toToolVd() = ToolVd(
        id = agentTool.name,
        displayName = ToolRegistry.getDisplayName(this),
        description = description,
        requiresPermission = requiresPermission,
        categoryId = category.name,
    )
}
