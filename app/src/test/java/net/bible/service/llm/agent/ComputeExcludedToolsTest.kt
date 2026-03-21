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

import net.bible.service.llm.AgentTool
import net.bible.service.llm.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeExcludedToolsTest {

    private val readTool = AgentTool.GET_VERSE_CONTENT
    private val writeTool = AgentTool.CREATE_BOOKMARK
    private val anotherReadTool = AgentTool.SEARCH_BIBLE

    @Test
    fun emptyInputs_returnsEmpty() {
        val result = computeExcludedTools(emptySet(), null, null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun globallyDenied_isExcluded() {
        val result = computeExcludedTools(setOf(readTool), null, null)
        assertTrue(readTool in result)
    }

    @Test
    fun promptDenied_isExcluded() {
        val result = computeExcludedTools(emptySet(), setOf(readTool), null)
        assertTrue(readTool in result)
    }

    @Test
    fun globallyDenied_promptAllowed_overridesToNotExcluded() {
        val result = computeExcludedTools(
            permanentlyDeniedTools = setOf(readTool),
            promptDeniedTools = null,
            promptAllowedTools = setOf(readTool),
        )
        // Prompt allowlist overrides global deny for tools in the allowlist
        assertFalse(readTool in result)
    }

    @Test
    fun promptDenied_promptAllowed_allowedWins() {
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = setOf(readTool),
            promptAllowedTools = setOf(readTool),
        )
        // Prompt allowlist overrides prompt deny
        assertFalse(readTool in result)
    }

    @Test
    fun mixedTools_onlyOverriddenOnesReEnabled() {
        val result = computeExcludedTools(
            permanentlyDeniedTools = setOf(readTool, writeTool),
            promptDeniedTools = null,
            promptAllowedTools = setOf(readTool),
        )
        // readTool is in allowlist so it overrides global deny
        assertFalse(readTool in result)
        // writeTool is globally denied AND not in allowlist, so it stays excluded
        assertTrue(writeTool in result)
    }

    @Test
    fun globalAndPromptDenied_combined() {
        val result = computeExcludedTools(
            permanentlyDeniedTools = setOf(readTool),
            promptDeniedTools = setOf(anotherReadTool),
            promptAllowedTools = null,
        )
        assertTrue(readTool in result)
        assertTrue(anotherReadTool in result)
        assertEquals(2, result.size)
    }

    @Test
    fun allowlist_excludesEverythingNotInList() {
        // When allowedTools is set, all tools NOT in the list are excluded
        // (except structural tools which are never excluded)
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = null,
            promptAllowedTools = setOf(readTool),
        )
        // readTool should NOT be excluded (it's in the allowlist)
        assertFalse(readTool in result)
        // Other non-structural tools SHOULD be excluded
        assertTrue(writeTool in result)
        assertTrue(anotherReadTool in result)
        // Structural tools should NOT be excluded
        for (structural in ToolRegistry.STRUCTURAL_TOOLS) {
            assertFalse("Structural tool $structural should not be excluded", structural in result)
        }
    }

    @Test
    fun allowlist_empty_excludesAllNonStructural() {
        // Empty allowlist = no tools allowed (except structural)
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = null,
            promptAllowedTools = emptySet(),
        )
        val nonStructural = AgentTool.entries.toSet() - ToolRegistry.STRUCTURAL_TOOLS
        for (tool in nonStructural) {
            assertTrue("Non-structural tool $tool should be excluded", tool in result)
        }
        for (structural in ToolRegistry.STRUCTURAL_TOOLS) {
            assertFalse("Structural tool $structural should not be excluded", structural in result)
        }
    }

    @Test
    fun nullAllowedTools_allToolsAvailable() {
        // null allowedTools = no allowlist filtering (backwards compatible)
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = null,
            promptAllowedTools = null,
        )
        assertTrue(result.isEmpty())
    }
}
