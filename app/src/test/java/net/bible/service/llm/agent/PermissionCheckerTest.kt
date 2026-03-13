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

package net.bible.service.llm.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionCheckerTest {

    private fun check(
        toolName: String = "createBookmark",
        globalMode: PermissionMode = PermissionMode.ALWAYS_ASK,
        permanentlyAllowedTools: Set<String> = emptySet(),
        permanentlyDeniedTools: Set<String> = emptySet(),
        promptAllowedTools: Set<String>? = null,
        promptDeniedTools: Set<String>? = null,
        promptPermissionMode: PermissionMode? = null,
        grantedWritePermission: Boolean = false,
        grantedAllToolsPermission: Boolean = false,
    ): PermissionCheckResult = PermissionChecker.check(
        toolName = toolName,
        settings = PermissionSettings(globalMode, permanentlyAllowedTools, permanentlyDeniedTools),
        promptAllowedTools = promptAllowedTools,
        promptDeniedTools = promptDeniedTools,
        promptPermissionMode = promptPermissionMode,
        grantedWritePermission = grantedWritePermission,
        grantedAllToolsPermission = grantedAllToolsPermission,
    )

    // ---- 1. Global DENY_ALL ----

    @Test
    fun globalDenyAll_blocksEverything() {
        val result = check(globalMode = PermissionMode.DENY_ALL)
        assertEquals(PermissionCheckResult.Denied, result)
    }

    @Test
    fun globalDenyAll_blocksEvenPromptAllowedTool() {
        val result = check(
            globalMode = PermissionMode.DENY_ALL,
            promptAllowedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    @Test
    fun globalDenyAll_blocksEvenPermanentlyAllowedTool() {
        val result = check(
            globalMode = PermissionMode.DENY_ALL,
            permanentlyAllowedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    // ---- 2. Per-tool permanently denied ----

    @Test
    fun permanentlyDeniedTool_isBlocked() {
        val result = check(
            globalMode = PermissionMode.ALLOW_ALL,
            permanentlyDeniedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    @Test
    fun permanentlyDeniedTool_blocksEvenIfPromptAllows() {
        val result = check(
            permanentlyDeniedTools = setOf("createBookmark"),
            promptAllowedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    // ---- 3. Per-tool permanently allowed ----

    @Test
    fun permanentlyAllowedTool_passes() {
        val result = check(
            globalMode = PermissionMode.ALWAYS_ASK,
            permanentlyAllowedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    @Test
    fun permanentlyAllowedTool_passesEvenIfPromptDenies() {
        val result = check(
            permanentlyAllowedTools = setOf("createBookmark"),
            promptDeniedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    @Test
    fun unknownTool_fallsThrough() {
        // Tool not in any permanent set → should fall through to later checks
        val result = check(
            globalMode = PermissionMode.ALLOW_ALL,
            permanentlyAllowedTools = setOf("otherTool"),
            permanentlyDeniedTools = setOf("anotherTool"),
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    // ---- 3.5 / 3.6 Per-prompt per-tool ----

    @Test
    fun promptDeniedTool_isBlocked() {
        val result = check(
            globalMode = PermissionMode.ALLOW_ALL,
            promptDeniedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    @Test
    fun promptAllowedTool_passes() {
        val result = check(
            globalMode = PermissionMode.ALWAYS_ASK,
            promptAllowedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    @Test
    fun nullPromptSets_fallThrough() {
        // null prompt sets should have no effect — falls through to global mode
        val result = check(
            globalMode = PermissionMode.ALLOW_ALL,
            promptAllowedTools = null,
            promptDeniedTools = null,
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    // ---- 4. Global ALLOW_ALL ----

    @Test
    fun globalAllowAll_allows() {
        val result = check(globalMode = PermissionMode.ALLOW_ALL)
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    // ---- 4.5 Session "allow all tools" ----

    @Test
    fun grantedAllToolsPermission_allows() {
        val result = check(
            globalMode = PermissionMode.ALWAYS_ASK,
            grantedAllToolsPermission = true,
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    @Test
    fun grantedAllToolsPermission_allowsDespiteAlwaysAsk() {
        val result = check(
            globalMode = PermissionMode.ALWAYS_ASK,
            grantedAllToolsPermission = true,
            grantedWritePermission = false,
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    // ---- 5. Effective mode ----

    @Test
    fun effectiveMode_denyAll_denies() {
        // Global is ASK_ONCE_PER_RUN, but prompt overrides to DENY_ALL
        val result = check(
            globalMode = PermissionMode.ASK_ONCE_PER_RUN,
            promptPermissionMode = PermissionMode.DENY_ALL,
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    @Test
    fun effectiveMode_allowAll_allows() {
        // Global is ALWAYS_ASK, but prompt overrides to ALLOW_ALL
        val result = check(
            globalMode = PermissionMode.ALWAYS_ASK,
            promptPermissionMode = PermissionMode.ALLOW_ALL,
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    @Test
    fun askOncePerRun_withSessionGrant_allows() {
        val result = check(
            globalMode = PermissionMode.ASK_ONCE_PER_RUN,
            grantedWritePermission = true,
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    @Test
    fun askOncePerRun_withoutSessionGrant_needsDialog() {
        val result = check(
            globalMode = PermissionMode.ASK_ONCE_PER_RUN,
            grantedWritePermission = false,
        )
        assertEquals(PermissionCheckResult.NeedsDialog, result)
    }

    @Test
    fun alwaysAsk_needsDialog() {
        val result = check(globalMode = PermissionMode.ALWAYS_ASK)
        assertEquals(PermissionCheckResult.NeedsDialog, result)
    }

    @Test
    fun alwaysAsk_needsDialog_evenWithSessionWritePermission() {
        val result = check(
            globalMode = PermissionMode.ALWAYS_ASK,
            grantedWritePermission = true,
        )
        assertEquals(PermissionCheckResult.NeedsDialog, result)
    }

    @Test
    fun promptPermissionMode_overridesGlobal() {
        // Global is ALLOW_ALL, but prompt overrides to ALWAYS_ASK → NeedsDialog
        // Note: prompt override is checked at level 5, but ALLOW_ALL at level 4 takes precedence
        // So we need a global mode that falls through to level 5
        val result = check(
            globalMode = PermissionMode.ASK_ONCE_PER_RUN,
            promptPermissionMode = PermissionMode.ALWAYS_ASK,
        )
        assertEquals(PermissionCheckResult.NeedsDialog, result)
    }

    @Test
    fun nullPromptPermissionMode_usesGlobal() {
        val result = check(
            globalMode = PermissionMode.ASK_ONCE_PER_RUN,
            promptPermissionMode = null,
            grantedWritePermission = true,
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    // ---- Priority ordering / interaction tests ----

    @Test
    fun globalDenyBeatsPromptAllow() {
        val result = check(
            globalMode = PermissionMode.DENY_ALL,
            promptAllowedTools = setOf("createBookmark"),
            promptPermissionMode = PermissionMode.ALLOW_ALL,
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    @Test
    fun globalAllowToolBeatsPromptDeny() {
        val result = check(
            permanentlyAllowedTools = setOf("createBookmark"),
            promptDeniedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    @Test
    fun promptDenyBeatsGlobalAllowAll() {
        val result = check(
            globalMode = PermissionMode.ALLOW_ALL,
            promptDeniedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    @Test
    fun promptAllowBeatsSessionDialogNeed() {
        val result = check(
            globalMode = PermissionMode.ALWAYS_ASK,
            promptAllowedTools = setOf("createBookmark"),
            grantedWritePermission = false,
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }

    @Test
    fun permanentDenyBeatsPermanentAllow_whenBothSet() {
        // If a tool appears in both sets, deny wins (checked first)
        val result = check(
            permanentlyDeniedTools = setOf("createBookmark"),
            permanentlyAllowedTools = setOf("createBookmark"),
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    @Test
    fun sessionAllToolsGrant_doesNotOverridePromptDeny() {
        val result = check(
            globalMode = PermissionMode.ALWAYS_ASK,
            promptDeniedTools = setOf("createBookmark"),
            grantedAllToolsPermission = true,
        )
        assertEquals(PermissionCheckResult.Denied, result)
    }

    @Test
    fun differentTool_notAffectedByOtherToolPermissions() {
        val result = check(
            toolName = "addBookmarkNote",
            globalMode = PermissionMode.ALLOW_ALL,
            permanentlyDeniedTools = setOf("createBookmark"),
            permanentlyAllowedTools = setOf("createLabel"),
        )
        assertEquals(PermissionCheckResult.Allowed, result)
    }
}
