/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.llm.agent

/**
 * Result of permission check.
 */
sealed class PermissionCheckResult {
    /** Tool is allowed to execute */
    object Allowed : PermissionCheckResult()
    /** Tool is denied */
    object Denied : PermissionCheckResult()
    /** Need to show permission dialog to the user */
    object NeedsDialog : PermissionCheckResult()
}

/**
 * Input data for permission checking — avoids coupling to CommonUtils.settings.
 */
data class PermissionSettings(
    val globalMode: PermissionMode,
    val permanentlyAllowedTools: Set<String>,
    val permanentlyDeniedTools: Set<String>,
)

/**
 * Pure permission decision logic, decoupled from Android UI and singletons.
 *
 * Priority order:
 * 1. Global DENY_ALL → deny (absolute safety switch)
 * 2. Per-tool permanently denied → deny
 * 3. Per-tool permanently allowed → allow
 * 3.5. Per-prompt per-tool denied → deny
 * 3.6. Per-prompt per-tool allowed → allow
 * 4. Global ALLOW_ALL → allow
 * 4.5. Session "allow all tools" → allow
 * 5. Effective mode (per-prompt override or global):
 *    - DENY_ALL → deny
 *    - ALLOW_ALL → allow
 *    - ASK_ONCE_PER_RUN → allow if session-granted, else dialog
 *    - ALWAYS_ASK → dialog
 */
object PermissionChecker {

    fun check(
        toolName: String,
        settings: PermissionSettings,
        promptAllowedTools: Set<String>?,
        promptDeniedTools: Set<String>?,
        promptPermissionMode: PermissionMode?,
        grantedWritePermission: Boolean,
        grantedAllToolsPermission: Boolean,
    ): PermissionCheckResult {
        // 1. Global DENY_ALL is absolute
        if (settings.globalMode == PermissionMode.DENY_ALL) {
            return PermissionCheckResult.Denied
        }

        // 2. Per-tool permanently denied
        if (toolName in settings.permanentlyDeniedTools) {
            return PermissionCheckResult.Denied
        }

        // 3. Per-tool permanently allowed
        if (toolName in settings.permanentlyAllowedTools) {
            return PermissionCheckResult.Allowed
        }

        // 3.5 Per-prompt per-tool denied
        if (promptDeniedTools?.contains(toolName) == true) {
            return PermissionCheckResult.Denied
        }

        // 3.6 Per-prompt per-tool allowed
        if (promptAllowedTools?.contains(toolName) == true) {
            return PermissionCheckResult.Allowed
        }

        // 4. Global ALLOW_ALL
        if (settings.globalMode == PermissionMode.ALLOW_ALL) {
            return PermissionCheckResult.Allowed
        }

        // 4.5. Session-level "allow all tools" grant
        if (grantedAllToolsPermission) {
            return PermissionCheckResult.Allowed
        }

        // 5. Effective mode: per-prompt override or global
        val effectiveMode = promptPermissionMode ?: settings.globalMode

        return when (effectiveMode) {
            PermissionMode.DENY_ALL -> PermissionCheckResult.Denied
            PermissionMode.ALLOW_ALL -> PermissionCheckResult.Allowed
            PermissionMode.ASK_ONCE_PER_RUN -> {
                if (grantedWritePermission) {
                    PermissionCheckResult.Allowed
                } else {
                    PermissionCheckResult.NeedsDialog
                }
            }
            PermissionMode.ALWAYS_ASK -> PermissionCheckResult.NeedsDialog
        }
    }
}
