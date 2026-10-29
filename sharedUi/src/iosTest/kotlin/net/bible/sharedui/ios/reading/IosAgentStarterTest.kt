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

package net.bible.sharedui.ios.reading

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.android.control.page.DocumentCategory
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.Selection
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.PromptContext
import net.bible.sharedcore.ai.reading.ReadingLlmDialog
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedcore.ai.reading.ReadingModelVd
import net.bible.sharedcore.ai.reading.ReadingPromptGroupVd
import net.bible.sharedcore.ai.reading.ReadingPromptVd
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bible.sharedui.host.reading.IosAgentStarter

/**
 * The in-reader AI start on iOS: [IosAgentStarter] runs the same `executePrompt` call as Android's foreground service inside
 * a background-task window, and the host's prompt selector hands a chosen prompt to it.
 */
class IosAgentStarterTest {
    private val workspace = IdType()
    private val promptId = IdType()
    private val selection = Selection("KJV", 5, 6, "In the beginning")

    /** Everything the starter touched, in order, so the order itself is asserted (begin, run, end). */
    private class Recorder {
        val events = mutableListOf<String>()
        var expire: (() -> Unit)? = null
        var executed: List<Any?>? = null
        var stopped = mutableListOf<IdType>()
        var regenerated: List<Any?>? = null
    }

    private fun starter(
        r: Recorder,
        scope: CoroutineScope,
        prompt: AgentPrompt?,
        currentWorkspace: () -> IdType = { workspace },
        execute: suspend (AgentPrompt, Selection, String?, IdType?) -> Boolean = { p, s, u, m ->
            r.events += "execute"; r.executed = listOf(p, s, u, m); true
        },
    ) = IosAgentStarter(
        scope = scope,
        windowRepositoryId = currentWorkspace,
        regenerate = { page, target, text, keep, fresh, model ->
            r.events += "regenerate"; r.regenerated = listOf(page, target, text, keep, fresh, model); true
        },
        promptById = { id -> r.events += "lookup"; if (id == promptId) prompt else null },
        execute = execute,
        stopAgent = { r.stopped += it; r.events += "stop" },
        beginBackgroundTask = { onExpire -> r.events += "begin"; r.expire = onExpire; 7UL },
        endBackgroundTask = { id -> r.events += "end:$id" },
        runContext = EmptyCoroutineContext,
    )

    private fun unconfined() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun startRunsExecutePromptWithTheWorkspace() {
        val r = Recorder()
        val scope = unconfined()
        val prompt = AgentPrompt(id = promptId, name = "Explain")
        val model = IdType()
        starter(r, scope, prompt).start(promptId, selection, "focus on grace", model)

        // The same arguments as AgentForegroundService's run, inside the background task.
        assertEquals(listOf<Any?>(prompt, selection, "focus on grace", model), r.executed)
        assertEquals(listOf("begin", "lookup", "execute", "end:7"), r.events)
        // A run that already ended has nothing left to stop when iOS later fires the expiry handler.
        r.expire!!.invoke()
        assertEquals<List<IdType>>(emptyList(), r.stopped)
        scope.cancel()
    }

    @Test
    fun startRegenerateRunsRegenerateInsideTheBackgroundTask() {
        val r = Recorder()
        val scope = unconfined()
        val page = IdType(); val target = IdType(); val model = IdType()
        starter(r, scope, prompt = null).startRegenerate(page, target, "shorter", true, false, model)
        assertEquals(listOf<Any?>(page, target, "shorter", true, false, model), r.regenerated)
        assertEquals(listOf("begin", "regenerate", "end:7"), r.events)
        scope.cancel()
    }

    /** The workspace is read when the run starts, not when the starter was built: expiry stops the one current then. */
    @Test
    fun expiryStopsTheWorkspaceThatWasCurrentAtStart() {
        val r = Recorder()
        val scope = unconfined()
        val first = IdType(); val second = IdType()
        var current = first
        val gate = CompletableDeferred<Unit>()
        val s = starter(r, scope, AgentPrompt(id = promptId), { current }) { _, _, _, _ -> gate.await(); true }
        current = second
        s.start(promptId, selection, null, null)
        current = IdType() // the user switches workspace while the run is going
        r.expire!!.invoke()
        assertEquals(listOf(second), r.stopped)
        scope.cancel()
    }

    @Test
    fun anUnknownPromptRunsNothingAndStillEndsTheBackgroundTask() {
        val r = Recorder()
        val scope = unconfined()
        starter(r, scope, prompt = null).start(promptId, selection, null, null)
        assertNull(r.executed)
        assertEquals(listOf("begin", "lookup", "end:7"), r.events)
        scope.cancel()
    }

    @Test
    fun aFailingRunDoesNotEscapeAndEndsTheBackgroundTask() {
        val r = Recorder()
        val scope = unconfined()
        starter(r, scope, AgentPrompt(id = promptId)) { _, _, _, _ -> error("model unreachable") }
            .start(promptId, selection, null, null)
        assertEquals(listOf("begin", "lookup", "end:7"), r.events, "the failure is logged, the task still ends")
        scope.cancel()
    }

    @Test
    fun expiryCancelsTheRunningRunStopsTheAgentAndEndsTheTask() {
        val r = Recorder()
        val scope = unconfined()
        val gate = CompletableDeferred<Unit>()
        starter(r, scope, AgentPrompt(id = promptId)) { _, _, _, _ ->
            r.events += "execute"; gate.await(); r.events += "finished"; true
        }.start(promptId, selection, null, null)
        assertEquals(listOf("begin", "lookup", "execute"), r.events, "the run is parked inside executePrompt")

        r.expire!!.invoke() // iOS is about to suspend the app

        assertEquals(listOf(workspace), r.stopped, "the agent session of the run's workspace is stopped")
        assertEquals(listOf("begin", "lookup", "execute", "stop", "end:7"), r.events, "cancelled, not finished; the task ended")
        scope.cancel()
    }

    /** A prompt list for the selector: one uncategorised prompt. */
    private class FakeService(private val prompt: ReadingPromptVd) : ReadingLlmService {
        val requested = mutableListOf<Pair<String, String?>>()
        override suspend fun promptGroupsFor(contextId: String, docCategoryId: String?): List<ReadingPromptGroupVd> {
            requested += contextId to docCategoryId
            return listOf(ReadingPromptGroupVd("Prompts", null, false, false, listOf(prompt)))
        }
        override suspend fun toggleFavorite(promptId: String) = Unit
        override suspend fun configuredModels(): List<ReadingModelVd> = emptyList()
        override fun askModelBeforeRun() = false
        override suspend fun promptRequiresModelChoice(promptId: String) = false
        override suspend fun setPromptModelDefault(promptId: String, modelId: String) = Unit
        override suspend fun regenerateRequiresModelChoice(pageId: String) = false
        override fun isCategoryCollapsed(contextId: String, categoryId: String?) = false
        override fun setCategoryCollapsed(contextId: String, categoryId: String?, collapsed: Boolean) = Unit
    }

    /**
     * The whole chain through the host: the hook opens the prompt selector for the context and document category it was given,
     * choosing a prompt starts the agent with the selection the hook got and closes the dialog. A wiring that dropped the
     * `onExecute` callback, or that started the agent before a prompt was chosen, fails here.
     */
    @Test
    fun promptSelectorExecuteStartsTheAgent() {
        val id = IdType().toString()
        val service = FakeService(ReadingPromptVd(id, "Explain", "", isFavorite = false, specifyBeforeRun = false))
        val started = mutableListOf<List<Any?>>()
        withIosReadingHost(
            scope = unconfined(),
            llmService = { service },
            startAgent = { p, s, u, m -> started += listOf(p, s, u, m) },
        ) { host, deps ->
            host.hooks.openPromptSelector(selection, PromptContext.VERSE_SELECTION, DocumentCategory.BIBLE)

            assertEquals<List<Pair<String, String?>>>(listOf("VERSE_SELECTION" to "BIBLE"), service.requested)
            assertTrue(deps.llmDialog.value.dialog is ReadingLlmDialog.PromptSelector, "the selector is showing")
            assertEquals<List<List<Any?>>>(emptyList(), started, "nothing starts before a prompt is chosen")

            deps.onLlmPromptChosen(id)

            assertEquals(listOf(listOf<Any?>(IdType(id), selection, null, null)), started)
            assertEquals(ReadingLlmDialog.None, deps.llmDialog.value.dialog, "the dialog closes after the run was handed over")
        }
    }

    /** The regenerate control: the hook opens the confirmation, confirming shows the notice in the ASKING window and starts the run. */
    @Test
    fun regenerateHookConfirmsThenNoticesTheAskingWindowAndStartsTheRun() {
        val service = FakeService(ReadingPromptVd(IdType().toString(), "Explain", "", isFavorite = false, specifyBeforeRun = false))
        val notices = mutableListOf<Pair<String, String>>()
        val runs = mutableListOf<List<Any?>>()
        val page = IdType(); val asking = IdType()
        withIosReadingHost(
            scope = unconfined(),
            llmService = { service },
            startRegenerate = { p, w, i, k, f, m -> runs += listOf(p, w, i, k, f, m) },
            showNotice = { w, t -> notices += w to t },
        ) { host, deps ->
            host.hooks.openRegenerate(page, asking)
            assertEquals(ReadingLlmDialog.Regenerate(page.toString()), deps.llmDialog.value.dialog)
            assertEquals(emptyList(), runs, "nothing runs before the confirmation")

            deps.onLlmRegenerateConfirmed("more detail", true, false)

            assertEquals(listOf(listOf<Any?>(page, asking, "more detail", true, false, null)), runs)
            assertEquals(listOf(asking.toString() to net.bible.sharedui.strings.iosStrings("en").aiDocumentRegenerating), notices)
            assertEquals(ReadingLlmDialog.None, deps.llmDialog.value.dialog)
        }
    }

    /** The selection menu's AI action asks for VERSE_SELECTION and the category of the window the selection is in. */
    @Test
    fun selectionActionOpensTheSelectorForTheSelectionsWindow() {
        val service = FakeService(ReadingPromptVd(IdType().toString(), "Explain", "", isFavorite = false, specifyBeforeRun = false))
        withIosReadingHost(scope = unconfined(), llmService = { service }) { host, _ ->
            val window = run {
                org.koin.mp.KoinPlatform.getKoin().get<net.bible.android.control.page.window.WindowControl>().activeWindow
            }
            val book = org.crosswire.jsword.book.Books.installed().getBook("K4RawText")!!
            window.pageManager.setCurrentDocumentAndKey(book, book.getKey("Gen.1.1"))
            val category = window.pageManager.currentPage.documentCategory

            host.openSelectionPromptSelector(window.id.toString(), selection)

            assertEquals<List<Pair<String, String?>>>(listOf("VERSE_SELECTION" to category.name), service.requested)
        }
    }

    /** The production starter reads the workspace through the host's window control. */
    @Test
    fun hostWorkspaceIdIsTheWindowRepositoryId() = withIosReadingHost { host, _ ->
        assertEquals(
            org.koin.mp.KoinPlatform.getKoin().get<net.bible.android.control.page.window.WindowControl>().windowRepository.id,
            host.currentWorkspaceId(),
        )
    }
}
