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

package net.bible.service.llm.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.llm.AgentTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ToolRegistryTest {

    @Test
    fun allToolsRegistered() {
        // Every AgentTool enum value must have a registered implementation
        assertEquals(AgentTool.entries.size, ToolRegistry.count)
    }

    @Test
    fun getReadTools() {
        val readToolNames = listOf(
            "getVerseContent", "searchBible", "getCommentaries",
            "getDictionaryEntry", "getBookmarksForVerse", "getBookmarksWithLabel",
            "getAllLabels", "getStudyPadContent", "searchStudyPads",
            "getInstalledDocuments"
        )
        for (name in readToolNames) {
            assertNotNull("Tool '$name' should be registered", ToolRegistry.get(name))
            assertFalse("Tool '$name' should not require permission", ToolRegistry.get(name)!!.requiresPermission)
        }
    }

    @Test
    fun getWriteTools() {
        val writeToolNames = listOf(
            "createBookmark", "addBookmarkNote", "updateBookmarkNote",
            "createLabel", "addLabelToBookmark", "addStudyPadEntry",
            "setDocumentTitle", "finishWithStudyPad", "finishWithoutDocument"
        )
        for (name in writeToolNames) {
            assertNotNull("Tool '$name' should be registered", ToolRegistry.get(name))
        }
    }

    @Test
    fun getUnknownTool_returnsNull() {
        assertNull(ToolRegistry.get("nonExistentTool"))
    }

    @Test
    fun hasTool() {
        assertTrue(ToolRegistry.has("getVerseContent"))
        assertTrue(ToolRegistry.has("createBookmark"))
        assertFalse(ToolRegistry.has("nonExistentTool"))
    }

    @Test
    fun getToolDefinitions_all() {
        val defs = ToolRegistry.getToolDefinitions()
        assertEquals(ToolRegistry.count, defs.size)
        val def = defs.first { it.name == "getVerseContent" }
        assertTrue(def.description.isNotBlank())
        assertTrue(def.parametersSchema.containsKey("type"))
    }

    @Test
    fun getToolDefinitions_excludesSpecifiedTools() {
        val excluded = setOf(AgentTool.SEARCH_BIBLE, AgentTool.GET_COMMENTARIES)
        val defs = ToolRegistry.getToolDefinitions(excludedTools = excluded)
        assertEquals(ToolRegistry.count - excluded.size, defs.size)
        assertFalse("SEARCH_BIBLE should be excluded", defs.any { it.tool == AgentTool.SEARCH_BIBLE })
        assertFalse("GET_COMMENTARIES should be excluded", defs.any { it.tool == AgentTool.GET_COMMENTARIES })
        assertTrue("GET_VERSE_CONTENT should still be included", defs.any { it.tool == AgentTool.GET_VERSE_CONTENT })
    }

    @Test
    fun getToolDefinitions_structuralToolsNeverExcluded() {
        val excluded = setOf(
            AgentTool.SET_DOCUMENT_TITLE,
            AgentTool.FINISH_WITH_STUDY_PAD,
            AgentTool.FINISH_WITHOUT_DOCUMENT,
            AgentTool.SEARCH_BIBLE
        )
        val defs = ToolRegistry.getToolDefinitions(excludedTools = excluded)
        // Only SEARCH_BIBLE is actually excluded; structural tools are kept
        assertEquals(ToolRegistry.count - 1, defs.size)
        assertTrue("SET_DOCUMENT_TITLE must be kept", defs.any { it.tool == AgentTool.SET_DOCUMENT_TITLE })
        assertTrue("FINISH_WITH_STUDY_PAD must be kept", defs.any { it.tool == AgentTool.FINISH_WITH_STUDY_PAD })
        assertTrue("FINISH_WITHOUT_DOCUMENT must be kept", defs.any { it.tool == AgentTool.FINISH_WITHOUT_DOCUMENT })
        assertFalse("SEARCH_BIBLE should be excluded", defs.any { it.tool == AgentTool.SEARCH_BIBLE })
    }

    @Test
    fun getConfigurableTools_excludesStructuralTools() {
        val configurable = ToolRegistry.getConfigurableTools()
        for (structural in ToolRegistry.STRUCTURAL_TOOLS) {
            assertFalse(
                "${structural.camelCaseName} should not be configurable",
                configurable.any { it.agentTool == structural }
            )
        }
        assertEquals(ToolRegistry.count - ToolRegistry.STRUCTURAL_TOOLS.size, configurable.size)
    }

    @Test
    fun getConfigurableTools_readToolsBeforeWriteTools() {
        val configurable = ToolRegistry.getConfigurableTools()
        val firstWriteIndex = configurable.indexOfFirst { it.requiresPermission }
        val lastReadIndex = configurable.indexOfLast { !it.requiresPermission }
        if (firstWriteIndex >= 0 && lastReadIndex >= 0) {
            assertTrue("Read tools should come before write tools", lastReadIndex < firstWriteIndex)
        }
    }

    @Test
    fun getPermissionTools() {
        val permTools = ToolRegistry.getPermissionTools()
        assertTrue(permTools.isNotEmpty())
        for (tool in permTools) {
            assertTrue("${tool.agentTool.camelCaseName} should require permission", tool.requiresPermission)
        }
        val writeToolsWithPermission = listOf(
            "createBookmark", "addBookmarkNote", "updateBookmarkNote",
            "createLabel", "addLabelToBookmark", "addStudyPadEntry"
        )
        for (name in writeToolsWithPermission) {
            assertTrue("$name should be in permission tools",
                permTools.any { it.agentTool.camelCaseName == name })
        }
    }

    @Test
    fun toolsHaveUniqueNames() {
        val defs = ToolRegistry.getToolDefinitions()
        val names = defs.map { it.name }
        assertEquals("Tool names should be unique", names.size, names.toSet().size)
    }

    @Test
    fun toolsHaveNonEmptyDescriptions() {
        val defs = ToolRegistry.getToolDefinitions()
        for (def in defs) {
            assertTrue("${def.name} should have a non-empty description", def.description.isNotBlank())
        }
    }

    @Test
    fun nonStructuralToolsHaveTaskCompleteProperties() {
        val defs = ToolRegistry.getToolDefinitions()
        for (def in defs) {
            val properties = def.parametersSchema["properties"] as? JsonObject ?: continue
            if (def.tool in ToolRegistry.STRUCTURAL_TOOLS) {
                assertFalse(
                    "${def.name} (structural) should NOT have taskComplete",
                    properties.containsKey("taskComplete")
                )
            } else {
                assertTrue(
                    "${def.name} should have taskComplete property",
                    properties.containsKey("taskComplete")
                )
                assertTrue(
                    "${def.name} should have taskCompleteMessage property",
                    properties.containsKey("taskCompleteMessage")
                )
            }
        }
    }

    @Test
    fun toolsHaveValidParameterSchemas() {
        val defs = ToolRegistry.getToolDefinitions()
        for (def in defs) {
            assertEquals("${def.name} schema should have type 'object'",
                "object", def.parametersSchema["type"]?.jsonPrimitive?.content)
            assertTrue("${def.name} schema should have 'properties'",
                def.parametersSchema.containsKey("properties"))
        }
    }
}
