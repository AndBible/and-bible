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

package net.bible.sharedcore.ai

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentPermissionControllerTest {

    private val request = AgentPermissionRequest(
        toolDisplayName = "Add bookmark",
        toolDescription = "Adds a bookmark to the current verse",
        actionDescription = "Bookmark John 3:16",
    )

    @Test
    fun pending_startsNull() {
        assertNull(AgentPermissionController().pending.value)
    }

    @Test
    fun await_publishesRequestThenResolvesWithRespondedChoice() = runTest {
        val c = AgentPermissionController()
        val answer = async { c.await(request) }
        yield()
        assertEquals(request, c.pending.value)
        c.respond(AgentPermissionChoice.ALLOW_FOR_SESSION)
        assertEquals(AgentPermissionChoice.ALLOW_FOR_SESSION, answer.await())
        assertNull(c.pending.value)
    }

    @Test
    fun await_resolvesEveryChoice() = runTest {
        for (choice in AgentPermissionChoice.entries) {
            val c = AgentPermissionController()
            val answer = async { c.await(request) }
            yield()
            c.respond(choice)
            assertEquals(choice, answer.await())
        }
    }

    @Test
    fun dismiss_resolvesAsDeny() = runTest {
        val c = AgentPermissionController()
        val answer = async { c.await(request) }
        yield()
        c.dismiss()
        assertEquals(AgentPermissionChoice.DENY, answer.await())
        assertNull(c.pending.value)
    }

    @Test
    fun secondRequestWhileOnePending_isRejectedAndKeepsTheFirst() = runTest {
        val c = AgentPermissionController()
        val first = async { c.await(request) }
        yield()
        val second = request.copy(toolDisplayName = "Delete bookmark")
        val secondAnswer = async { c.await(second) }
        yield()
        // The first request stays on screen; the intruder is denied immediately rather than
        // clobbering it. AgentExecutor awaits each tool call before issuing the next, so this
        // path is defensive, and denying is the safe answer for an unshown prompt.
        assertEquals(request, c.pending.value)
        assertEquals(AgentPermissionChoice.DENY, secondAnswer.await())
        c.respond(AgentPermissionChoice.ALLOW)
        assertEquals(AgentPermissionChoice.ALLOW, first.await())
    }

    @Test
    fun respondWithNothingPending_isANoOp() {
        val c = AgentPermissionController()
        c.respond(AgentPermissionChoice.ALLOW)
        assertNull(c.pending.value)
    }
}
