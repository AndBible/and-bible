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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
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
        assertEquals(19, ToolRegistry.count)
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
        val defs = ToolRegistry.getToolDefinitions(includeWriteTools = true)
        assertEquals(19, defs.size)
        // Check definition structure
        val def = defs.first { it.name == "getVerseContent" }
        assertTrue(def.description.isNotBlank())
        assertTrue(def.parametersSchema.has("type"))
    }

    @Test
    fun getToolDefinitions_readOnly() {
        val defs = ToolRegistry.getToolDefinitions(includeWriteTools = false)
        // 10 read tools + 3 write tools that don't require permission (setDocumentTitle, finishWithStudyPad, finishWithoutDocument)
        assertEquals(13, defs.size)
        // Verify none require permission
        for (def in defs) {
            val tool = ToolRegistry.get(def.name)!!
            assertFalse("${def.name} should not require permission", tool.requiresPermission)
        }
    }

    @Test
    fun getPermissionTools() {
        val permTools = ToolRegistry.getPermissionTools()
        assertTrue(permTools.isNotEmpty())
        for (tool in permTools) {
            assertTrue("${tool.name} should require permission", tool.requiresPermission)
        }
        // setDocumentTitle and finishWithoutDocument are write tools but not in permissionTools
        // Actually all write tools have requiresPermission = false for finish tools?
        // Let's just verify the count: write tools that have requiresPermission = true
        val writeToolsWithPermission = listOf(
            "createBookmark", "addBookmarkNote", "updateBookmarkNote",
            "createLabel", "addLabelToBookmark", "addStudyPadEntry"
        )
        for (name in writeToolsWithPermission) {
            assertTrue("$name should be in permission tools",
                permTools.any { it.name == name })
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
    fun toolsHaveValidParameterSchemas() {
        val defs = ToolRegistry.getToolDefinitions()
        for (def in defs) {
            assertEquals("${def.name} schema should have type 'object'",
                "object", def.parametersSchema.getString("type"))
            assertTrue("${def.name} schema should have 'properties'",
                def.parametersSchema.has("properties"))
        }
    }
}
