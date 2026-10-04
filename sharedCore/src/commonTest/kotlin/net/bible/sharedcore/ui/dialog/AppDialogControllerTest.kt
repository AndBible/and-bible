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

package net.bible.sharedcore.ui.dialog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppDialogControllerTest {

    private fun msg(text: String) = AppDialogRequest.Message(title = null, message = text, confirmText = "OK")
    private val confirm = AppDialogRequest.Confirm(title = "Sure?", message = null, confirmText = "OK", dismissText = "Cancel")

    @Test fun pendingStartsNull() {
        assertNull(AppDialogController().pending.value)
    }

    @Test fun awaitPublishesThenResolvesWithTheResponse() = runTest {
        val c = AppDialogController()
        val answer = async { c.await(confirm) }
        yield()
        val shown = c.pending.value!!
        assertEquals(confirm, shown.request)
        c.respond(shown.id, AppDialogResult.Ok)
        assertEquals(AppDialogResult.Ok, answer.await())
        assertNull(c.pending.value)
    }

    @Test fun requestsQueueInFifoOrder() = runTest {
        val c = AppDialogController()
        val seen = mutableListOf<String>()
        c.post(msg("first")) { seen += "first" }
        c.post(msg("second")) { seen += "second" }
        assertEquals("first", (c.pending.value!!.request as AppDialogRequest.Message).message)
        c.respond(c.pending.value!!.id, AppDialogResult.Ok)
        assertEquals("second", (c.pending.value!!.request as AppDialogRequest.Message).message)
        c.respond(c.pending.value!!.id, AppDialogResult.Ok)
        assertEquals(listOf("first", "second"), seen)
        assertNull(c.pending.value)
    }

    @Test fun aSecondRequestDoesNotReplaceTheOneShowing() = runTest {
        val c = AppDialogController()
        val first = async { c.await(confirm) }
        yield()
        val firstId = c.pending.value!!.id
        c.post(msg("error while confirm is open"))
        assertEquals(firstId, c.pending.value!!.id)
        c.respond(firstId, AppDialogResult.Cancel)
        assertEquals(AppDialogResult.Cancel, first.await())
        assertEquals("error while confirm is open", (c.pending.value!!.request as AppDialogRequest.Message).message)
    }

    @Test fun staleAndDuplicateRespondAreNoOps() = runTest {
        val c = AppDialogController()
        var calls = 0
        val id = c.post(msg("x")) { calls++ }
        c.respond(id, AppDialogResult.Ok)
        c.respond(id, AppDialogResult.Ok)       // duplicate
        c.respond(id + 999, AppDialogResult.Ok) // never existed
        assertEquals(1, calls)
    }

    @Test fun cancellingTheAwaitingCoroutineRemovesItsRequest() = runTest {
        val c = AppDialogController()
        val job = launch { c.await(confirm) }
        yield()
        assertTrue(c.pending.value != null)
        job.cancel()
        yield()
        assertNull(c.pending.value)
    }

    @Test fun cancelAllResumesEveryQueuedCallerWithCancel() = runTest {
        val c = AppDialogController()
        val a = async { c.await(confirm) }
        val b = async { c.await(msg("queued")) }
        yield()
        c.cancelAll()
        assertEquals(listOf(AppDialogResult.Cancel, AppDialogResult.Cancel), listOf(a, b).awaitAll())
        assertNull(c.pending.value)
    }

    @Test fun aProgressDoesNotBlockAQuestionPostedBehindIt() = runTest {
        val c = AppDialogController()
        val p = c.show(AppDialogRequest.Progress(title = null, message = "Please wait"))
        c.post(msg("after"))
        assertEquals(p, c.progress.value!!.id)
        assertEquals("after", (c.pending.value!!.request as AppDialogRequest.Message).message)
        c.respond(p, AppDialogResult.Ok)       // a progress cannot be answered
        assertEquals(p, c.progress.value!!.id)
        c.dismiss(p)
        assertNull(c.progress.value)
        assertEquals("after", (c.pending.value!!.request as AppDialogRequest.Message).message)
    }

    @Test fun theNewestProgressIsTheOneShown() {
        val c = AppDialogController()
        val first = c.show(AppDialogRequest.Progress(title = null, message = "first"))
        val second = c.show(AppDialogRequest.Progress(title = null, message = "second"))
        assertEquals(second, c.progress.value!!.id)
        c.dismiss(second)
        assertEquals(first, c.progress.value!!.id)
    }

    @Test fun dismissOfAnAlreadyRemovedIdIsANoOp() {
        val c = AppDialogController()
        val p = c.show(AppDialogRequest.Progress(title = null, message = "wait"))
        c.dismiss(p)
        c.dismiss(p)
        assertNull(c.pending.value)
    }

    @Test fun concurrentPostsFromManyThreadsAreAllKept() = runTest {
        val c = AppDialogController()
        withContext(Dispatchers.Default) {
            (1..200).map { n -> async { c.post(msg("m$n")) } }.awaitAll()
        }
        var count = 0
        while (c.pending.value != null) { c.respond(c.pending.value!!.id, AppDialogResult.Ok); count++ }
        assertEquals(200, count)
    }

    private fun unlock(key: String? = "unlock:X") = AppDialogRequest.TextInput(
        title = "t", message = null, initial = "", confirmText = "ok", dismissText = "no", draftKey = key,
    )

    @Test fun aDraftIsReadBackForTheSameRequest() {
        val c = AppDialogController()
        val id = c.post(unlock())
        c.saveDraft(id, "halfpass")
        assertEquals("halfpass", c.draft(id))
    }

    @Test fun anAnswerClearsTheDraft() {
        val c = AppDialogController()
        val id = c.post(unlock())
        c.saveDraft(id, "halfpass")
        c.respond(id, AppDialogResult.Cancel)
        val next = c.post(unlock())
        assertNull(c.draft(next))
    }

    /** Review Focus 4a: the caller's cancellation (Activity recreate) keeps it for the NEW request with the same key. */
    @Test fun aWithdrawnRequestsDraftCarriesToTheNextRequestWithTheSameKey() = runTest {
        val c = AppDialogController()
        val job = launch { c.await(unlock()) }
        testScheduler.advanceUntilIdle()
        c.saveDraft(c.pending.value!!.id, "halfpass")
        job.cancel(); testScheduler.advanceUntilIdle()     // recreate: the old coroutine is cancelled

        val next = c.post(unlock())
        assertEquals("halfpass", c.draft(next))
    }

    /** Review Focus 4b. */
    @Test fun cancelAllClearsEveryDraft() {
        val c = AppDialogController()
        val id = c.post(unlock())
        c.saveDraft(id, "halfpass")
        c.cancelAll()
        assertNull(c.draft(c.post(unlock())))
    }

    @Test fun withoutADraftKeyTwoRequestsNeverShare() {
        val c = AppDialogController()
        val a = c.post(unlock(key = null))
        c.saveDraft(a, "x")
        c.respond(a, AppDialogResult.Cancel)
        assertNull(c.draft(c.post(unlock(key = null))))
    }
}
