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

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.page.Selection
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.AgentTool
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.PromptRepository
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #4108: the permission fields of the [AgentContext] a run starts with come from the user's grants only.
 * Goes through [AgentSessionManager.buildAgentContext] so a regression in its wiring (passing the prompt's own
 * `allowedTools`/`permissionMode` instead of [PromptRepository.autoAllowedTools] /
 * [PromptRepository.effectivePermissionMode]) goes red. Workspace-level selection, so no Bible module is
 * needed; the context build hops to Main, so it runs on IO while this thread idles the main looper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BuildAgentContextGrantsTest {
    private val workspaceSelection =
        Selection(bookInitials = null, startOrdinal = -1, startOffset = null, endOrdinal = -1, endOffset = null, bookmarks = emptyList())

    @Before fun setUp() {
        AgentSessionManager.windowControl.windowRepository.initializeAtEdge()
    }

    @After fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
        DatabaseResetter.resetDatabase()
    }

    private fun <T> onIoIdlingMain(block: suspend () -> T): T {
        val d = CoroutineScope(Dispatchers.IO).async { block() }
        val deadline = System.currentTimeMillis() + 30_000
        while (!d.isCompleted) {
            check(System.currentTimeMillis() < deadline) { "did not return in 30 s" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        return d.getCompleted()
    }

    private fun contextFor(prompt: AgentPrompt) =
        onIoIdlingMain { AgentSessionManager.buildAgentContext(prompt, workspaceSelection) }

    @Test fun builtInWriteToolsAreAvailableButNotAutoAllowed() {
        val builtIn = BuiltInPrompts.promptById(BuiltInPrompts.BOOKMARK_ANNOTATE_ID)!!
        val context = contextFor(builtIn)
        assertTrue(AgentTool.CREATE_BOOKMARK in context.promptAvailableTools.orEmpty())
        assertNull(context.promptAllowedTools)
    }

    @Test fun addonAllowAllAndWriteToolsAreNotGrants() {
        val addon = AgentPrompt(
            name = "Addon",
            permissionMode = PermissionMode.ALLOW_ALL,
            allowedTools = setOf(AgentTool.DELETE_BOOKMARK),
            deniedTools = emptySet(),
        ).also { it.sourceModule = "AddonMod" }
        val context = contextFor(addon)
        assertNull(context.promptPermissionMode)
        assertNull(context.promptAllowedTools)
        assertEquals(setOf(AgentTool.DELETE_BOOKMARK), context.promptAvailableTools)
    }

    @Test fun storedUserPromptGrantsApply() {
        val mine = AgentPrompt(
            name = "Mine",
            permissionMode = PermissionMode.ALLOW_ALL,
            allowedTools = setOf(AgentTool.CREATE_BOOKMARK),
            deniedTools = emptySet(),
        )
        onIoIdlingMain { PromptRepository.insertPrompt(mine) }
        val stored = onIoIdlingMain { PromptRepository.promptById(mine.id) }!!
        val context = contextFor(stored)
        assertEquals(PermissionMode.ALLOW_ALL, context.promptPermissionMode)
        assertEquals(setOf(AgentTool.CREATE_BOOKMARK), context.promptAllowedTools)
    }
}
