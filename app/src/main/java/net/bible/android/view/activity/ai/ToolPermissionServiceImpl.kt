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

import net.bible.service.common.CommonUtils
import net.bible.service.llm.AgentTool
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.sharedcore.ai.ToolCategoryVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.ai.ToolPermissionService
import net.bible.sharedcore.ai.ToolVd

/**
 * Android impl of [ToolPermissionService] backing [net.bible.sharedcore.ai.GlobalToolPermissionsController]
 * (the global default tool-permissions screen — `ToolPermissionListBuilder.Mode.GLOBAL`). Mirrors
 * classic [GlobalToolPermissionsActivity]/[ToolPermissionListBuilder] exactly:
 *
 * - [toolsByCategory] = [ToolRegistry.getConfigurableToolsByCategory] (same source as
 *   `PromptService.toolsByCategory`), mapped to the shared VD shape (`ToolVd.id` = `AgentTool.name`).
 * - [permissionFor] resolves one tool's current GLOBAL permission against
 *   [CommonUtils.aiSettings] `permanentlyAllowedTools`/`permanentlyDeniedTools`, exactly as classic's
 *   `configureGlobalRow` sets the radio: a **write** tool (`requiresPermission`) is
 *   [ToolPermission.ALLOW] if allowed, [ToolPermission.DENY] if denied, else the neutral
 *   [ToolPermission.ASK]; a **read** tool is [ToolPermission.DISABLED] if denied, else
 *   [ToolPermission.ENABLED].
 * - [save] writes the two sets from the working map exactly as classic's
 *   `collectAllowed`/`collectDenied` → `permanentlyAllowedTools`/`permanentlyDeniedTools`:
 *   [ToolPermission.ALLOW] → allowed set; [ToolPermission.DENY]/[ToolPermission.DISABLED] → denied
 *   set; [ToolPermission.ASK]/[ToolPermission.ENABLED] (and [ToolPermission.DEFAULT], never used in
 *   GLOBAL mode) → neither set (the neutral/default state). Only write tools ever reach ALLOW, and
 *   both read-DISABLED and write-DENY collapse to the denied set — matching classic's single
 *   `radioDeny` id.
 *
 * Registered as a Koin single (stateless — reads/writes go straight to `CommonUtils.aiSettings`,
 * which persists via `GlobalAiSettingsDao`; `allowMainThreadQueries`, as elsewhere in this layer).
 */
class ToolPermissionServiceImpl : ToolPermissionService {
    private val settings get() = CommonUtils.aiSettings

    override fun toolsByCategory(): List<Pair<ToolCategoryVd, List<ToolVd>>> =
        ToolRegistry.getConfigurableToolsByCategory().map { (category, tools) ->
            ToolCategoryVd(id = category.name, displayName = ToolRegistry.getCategoryDisplayName(category)) to
                tools.map { it.toToolVd() }
        }

    override fun permissionFor(toolId: String): ToolPermission {
        val agentTool = runCatching { AgentTool.valueOf(toolId) }.getOrNull()
        val isWrite = agentTool?.let { ToolRegistry.get(it)?.requiresPermission } ?: false
        val allowed = agentTool != null && agentTool in settings.permanentlyAllowedTools
        val denied = agentTool != null && agentTool in settings.permanentlyDeniedTools
        return if (isWrite) {
            when {
                allowed -> ToolPermission.ALLOW
                denied -> ToolPermission.DENY
                else -> ToolPermission.ASK
            }
        } else {
            if (denied) ToolPermission.DISABLED else ToolPermission.ENABLED
        }
    }

    override fun save(permissions: Map<String, ToolPermission>) {
        val allowed = mutableSetOf<AgentTool>()
        val denied = mutableSetOf<AgentTool>()
        for ((toolId, permission) in permissions) {
            val agentTool = runCatching { AgentTool.valueOf(toolId) }.getOrNull() ?: continue
            when (permission) {
                ToolPermission.ALLOW -> allowed.add(agentTool)
                ToolPermission.DENY, ToolPermission.DISABLED -> denied.add(agentTool)
                // ASK / ENABLED / DEFAULT → neither set (neutral/default), mirroring classic where
                // an enabled read tool and an "ask" write tool are in neither permanent set.
                else -> Unit
            }
        }
        settings.permanentlyAllowedTools = allowed
        settings.permanentlyDeniedTools = denied
    }

    private fun Tool.toToolVd() = ToolVd(
        id = agentTool.name,
        displayName = ToolRegistry.getDisplayName(this),
        description = description,
        requiresPermission = requiresPermission,
        categoryId = category.name,
    )
}
