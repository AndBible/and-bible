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

import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.platform.AndroidLlmStrings
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.AgentTool
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.PermissionMode

/**
 * CR-7: "Copy to customize" of a prompt that no longer exists must report failure (null), not hand
 * back the source id -- AiNavGraph treats any non-null id as a successful copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class PromptServiceImplCopyTest {
    @After
    fun tearDown() = DatabaseResetter.resetDatabase()

    @Test
    fun copyingAPromptThatDoesNotExistReturnsNull() = runBlocking {
        assertNull(PromptServiceImpl(AndroidLlmStrings(BibleApplication.application)).copyPrompt(IdType().toString()))
    }

    /**
     * #4108: the read-only editor of a built-in shows what a run does: its write tools are not "Allow" (they
     * ask, so they show the default), its read tools stay enabled. Its "Copy to customize" result matches.
     */
    @Test
    fun builtInEditorAndCopyShowWriteToolsAsAsking() = runBlocking {
        val service = PromptServiceImpl(AndroidLlmStrings(BibleApplication.application))
        val id = BuiltInPrompts.BOOKMARK_ANNOTATE_ID.toString()
        val shown = assertNotNull(service.prompt(id))
        assertTrue(shown.isReadOnly)
        assertFalse(AgentTool.CREATE_BOOKMARK.name in shown.allowedTools, "built-in shows CREATE_BOOKMARK as Allow")
        assertFalse(AgentTool.CREATE_BOOKMARK.name in shown.deniedTools)
        assertTrue(AgentTool.GET_VERSE_CONTENT.name in shown.allowedTools)

        val copy = assertNotNull(service.prompt(assertNotNull(service.copyPrompt(id))))
        assertFalse(copy.isReadOnly)
        assertFalse(AgentTool.CREATE_BOOKMARK.name in copy.allowedTools)
        assertTrue(AgentTool.GET_VERSE_CONTENT.name in copy.allowedTools)
    }

    /** A user prompt's editor still shows the user's own grants. */
    @Test
    fun userPromptEditorShowsItsOwnGrants() = runBlocking {
        val service = PromptServiceImpl(AndroidLlmStrings(BibleApplication.application))
        val mine = AgentPrompt(name = "Mine", permissionMode = PermissionMode.ALLOW_ALL,
            allowedTools = setOf(AgentTool.CREATE_BOOKMARK), deniedTools = emptySet())
        PromptRepository.insertPrompt(mine)
        val shown = assertNotNull(service.prompt(mine.id.toString()))
        assertEquals(PermissionMode.ALLOW_ALL.name, shown.permissionMode)
        assertEquals(setOf(AgentTool.CREATE_BOOKMARK.name), shown.allowedTools)
    }
}
