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
        assertFalse(readTool in result)
    }

    @Test
    fun promptDenied_promptAllowed_allowedWins() {
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = setOf(readTool),
            promptAllowedTools = setOf(readTool),
        )
        assertFalse(readTool in result)
    }

    @Test
    fun mixedTools_onlyOverriddenOnesReEnabled() {
        val result = computeExcludedTools(
            permanentlyDeniedTools = setOf(readTool, writeTool),
            promptDeniedTools = null,
            promptAllowedTools = setOf(readTool),
        )
        assertFalse(readTool in result)
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
    fun promptAllowed_doesNotAddNewTools() {
        // promptAllowedTools should only remove from excluded, not add anything
        val result = computeExcludedTools(
            permanentlyDeniedTools = emptySet(),
            promptDeniedTools = null,
            promptAllowedTools = setOf(readTool),
        )
        assertTrue(result.isEmpty())
    }
}
