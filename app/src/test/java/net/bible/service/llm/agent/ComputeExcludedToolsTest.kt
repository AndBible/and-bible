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
            promptAvailableTools = setOf(readTool),
        )
        // Prompt allow overrides global deny
        assertFalse(readTool in result)
    }

    @Test
    fun promptDenied_promptAllowed_allowedWins() {
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = setOf(readTool),
            promptAvailableTools = setOf(readTool),
        )
        // Prompt allow overrides prompt deny
        assertFalse(readTool in result)
    }

    @Test
    fun mixedTools_onlyOverriddenOnesReEnabled() {
        val result = computeExcludedTools(
            permanentlyDeniedTools = setOf(readTool, writeTool),
            promptDeniedTools = null,
            promptAvailableTools = setOf(readTool),
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
            promptAvailableTools = null,
        )
        assertTrue(readTool in result)
        assertTrue(anotherReadTool in result)
        assertEquals(2, result.size)
    }

    @Test
    fun availableTools_noLongerActsAsWhitelist() {
        // availableTools should NOT exclude tools that aren't in the list.
        // Only deniedTools controls visibility; availableTools controls permission auto-allow.
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = null,
            promptAvailableTools = setOf(readTool),
        )
        // No tools should be excluded — availableTools doesn't act as a whitelist
        assertTrue(result.isEmpty())
    }

    @Test
    fun emptyAllowedTools_doesNotExcludeAnything() {
        // Empty availableTools should not exclude anything (was the bug: treated as whitelist)
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = null,
            promptAvailableTools = emptySet(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun nullAllowedTools_allToolsAvailable() {
        // null availableTools = no filtering (backwards compatible)
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = null,
            promptAvailableTools = null,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun deniedTools_excludesListedTools() {
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = setOf(readTool, writeTool),
            promptAvailableTools = null,
        )
        assertTrue(readTool in result)
        assertTrue(writeTool in result)
        assertFalse(anotherReadTool in result)
    }

    @Test
    fun structuralTools_neverExcluded() {
        // Even if structural tools are in deniedTools, they should not be excluded
        val result = computeExcludedTools(
            permanentlyDeniedTools = ToolRegistry.STRUCTURAL_TOOLS,
            promptDeniedTools = ToolRegistry.STRUCTURAL_TOOLS,
            promptAvailableTools = null,
        )
        for (structural in ToolRegistry.STRUCTURAL_TOOLS) {
            assertFalse("Structural tool $structural should not be excluded", structural in result)
        }
    }

    @Test
    fun deniedTools_withAllowedOverride() {
        // Tool in both denied and allowed: allowed wins
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = setOf(readTool, writeTool),
            promptAvailableTools = setOf(readTool),
        )
        assertFalse(readTool in result)  // overridden by availableTools
        assertTrue(writeTool in result)  // still denied
    }
}
