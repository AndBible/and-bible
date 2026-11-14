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
import net.bible.service.llm.AiSettings
import net.bible.service.llm.AgentTool
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.PromptRepository
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform
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
    /**
     * #4108 review: an add-on whose id also exists as a DB row (a user prompt imported before the pack was
     * installed) still gets no grants: the add-on object is not that stored row.
     */
    @Test fun addonWhoseIdIsAlsoStoredGetsNoGrants() {
        val row = AgentPrompt(
            name = "Mine, same id",
            permissionMode = PermissionMode.ALLOW_ALL,
            allowedTools = setOf(AgentTool.DELETE_BOOKMARK),
            deniedTools = emptySet(),
        )
        onIoIdlingMain { PromptRepository.insertPrompt(row) }
        val addon = row.copy(name = "Addon").also { it.sourceModule = "AddonMod" }
        val context = contextFor(addon)
        assertNull(context.promptPermissionMode)
        assertNull(context.promptAllowedTools)
    }

    /**
     * #4108, the original bug path: a stored copy of a built-in runs without the built-in's write tools as
     * grants (they ask) and without ALLOW_ALL.
     */
    @Test fun storedCopyOfBuiltInDoesNotInheritWriteGrants() {
        val copyId = onIoIdlingMain { PromptRepository.copyPrompt(BuiltInPrompts.BOOKMARK_ANNOTATE_ID) }!!
        val copy = onIoIdlingMain { PromptRepository.promptById(copyId) }!!
        val context = contextFor(copy)
        val excluded = computeExcludedTools(emptySet(), context.promptDeniedTools, context.promptAvailableTools)
        for (tool in listOf(AgentTool.CREATE_BOOKMARK, AgentTool.ADD_BOOKMARK_NOTE)) {
            assertFalse("$tool granted", tool in context.promptAllowedTools.orEmpty())
            assertFalse("$tool excluded: the copy can still use it (it asks)", tool in excluded)
        }
        assertTrue(AgentTool.GET_VERSE_CONTENT in context.promptAllowedTools.orEmpty())
        assertNotEquals(PermissionMode.ALLOW_ALL, context.promptPermissionMode)
    }

    /**
     * #4108 review: one approval grants write permission for all write tools of the run, so a non-user
     * prompt's ASK_ONCE_PER_RUN must not loosen a global ALWAYS_ASK. A stored user prompt keeps its own mode.
     */
    @Test fun nonUserPromptCannotLoosenTheGlobalMode() {
        val settings = KoinPlatform.getKoin().get<AiSettings>()
        val previous = settings.agentPermissionMode
        try {
            settings.agentPermissionMode = PermissionMode.ALWAYS_ASK
            val builtIn = BuiltInPrompts.promptById(BuiltInPrompts.BOOKMARK_ANNOTATE_ID)!!
            assertEquals(PermissionMode.ASK_ONCE_PER_RUN, builtIn.permissionMode)
            assertEquals(PermissionMode.ALWAYS_ASK, contextFor(builtIn).promptPermissionMode)

            val addon = AgentPrompt(
                name = "Addon once",
                permissionMode = PermissionMode.ASK_ONCE_PER_RUN,
                allowedTools = setOf(AgentTool.CREATE_BOOKMARK, AgentTool.DELETE_BOOKMARK),
                deniedTools = emptySet(),
            ).also { it.sourceModule = "AddonMod" }
            assertEquals(PermissionMode.ALWAYS_ASK, contextFor(addon).promptPermissionMode)

            val mine = addon.copy(name = "Mine once")
            onIoIdlingMain { PromptRepository.insertPrompt(mine) }
            val stored = onIoIdlingMain { PromptRepository.promptById(mine.id) }!!
            assertEquals(PermissionMode.ASK_ONCE_PER_RUN, contextFor(stored).promptPermissionMode)
        } finally {
            settings.agentPermissionMode = previous
        }
    }
}
