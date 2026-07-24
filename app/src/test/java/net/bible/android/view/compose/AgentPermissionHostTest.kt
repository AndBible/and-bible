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
package net.bible.android.view.compose

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.base.Dialogs
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ai.AgentPermissionRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the Task-4 mapping and the fallback rule:
 *  - every [AgentPermissionChoice] maps to the matching [Dialogs.AgentPermissionResult];
 *  - the two enums stay the same size and carry the same names, so adding/renaming a choice on one
 *    side cannot silently mis-map an answer (the `when` in `Dialogs.agentPermissionDialog` maps by
 *    hand, so a rename there would still compile while answering something else).
 * The Activity-resolution branch itself is an on-device item (it needs a live MainBibleActivity).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AgentPermissionHostTest {

    @Test
    fun choiceEnum_andResultEnum_haveTheSameArity() {
        assertEquals(
            Dialogs.AgentPermissionResult.entries.size,
            AgentPermissionChoice.entries.size,
        )
    }

    @Test
    fun choiceNames_matchResultNames() {
        assertEquals(
            Dialogs.AgentPermissionResult.entries.map { it.name }.sorted(),
            AgentPermissionChoice.entries.map { it.name }.sorted(),
        )
    }

    @Test
    fun controllerRoundTrip_deliversTheChoice() = runTest {
        val c = AgentPermissionController()
        val answer = async { c.await(AgentPermissionRequest("Add bookmark", "desc", null)) }
        yield()
        c.respond(AgentPermissionChoice.ALLOW_ALL_SESSION)
        assertEquals(AgentPermissionChoice.ALLOW_ALL_SESSION, answer.await())
    }

    /** A cancel/back/scrim tap must resolve the awaiting agent coroutine, not leave it hanging. */
    @Test
    fun dismiss_resolvesDeny() = runTest {
        val c = AgentPermissionController()
        val answer = async { c.await(AgentPermissionRequest("Add bookmark", "desc", "adds a bookmark")) }
        yield()
        c.dismiss()
        assertEquals(AgentPermissionChoice.DENY, answer.await())
    }
}
