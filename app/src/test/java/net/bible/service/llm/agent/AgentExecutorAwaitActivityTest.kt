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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.sharedcore.event.Subscription
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

/** F126: a run cancelled while waiting for an Activity must still announce the end of the wait. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AgentExecutorAwaitActivityTest {
    private val ws = IdType()
    private val seen = mutableListOf<AgentSessionChange.PermissionWaiting>()
    private var sub: Subscription? = null
    private lateinit var savedActivities: ArrayList<ActivityBase>

    @Suppress("UNCHECKED_CAST")
    private fun holderActivities(): ArrayList<ActivityBase> =
        CurrentActivityHolder::class.java.getDeclaredField("activities").apply { isAccessible = true }
            .get(CurrentActivityHolder) as ArrayList<ActivityBase>

    @Before fun setUp() {
        savedActivities = ArrayList(holderActivities())
        holderActivities().clear() // no current Activity: awaitActivity has to wait
        sub = AgentSessionManager.changes.subscribe { if (it is AgentSessionChange.PermissionWaiting) seen += it }
    }

    @After fun tearDown() {
        sub?.cancel()
        holderActivities().clear()
        holderActivities().addAll(savedActivities)
    }

    @Test fun cancellingTheWaitAnnouncesItsEnd() = runTest {
        val fn = AgentExecutor::class.declaredFunctions.first { it.name == "awaitActivity" }.apply { isAccessible = true }
        val waiter = launch { fn.callSuspend(AgentExecutor(), ws, "Tool") }
        runCurrent()
        advanceTimeBy(1_200)
        assertEquals(listOf(AgentSessionChange.PermissionWaiting(ws, true, "Tool")), seen.toList())
        waiter.cancelAndJoin()
        assertEquals(
            listOf(AgentSessionChange.PermissionWaiting(ws, true, "Tool"), AgentSessionChange.PermissionWaiting(ws, false, null)),
            seen.toList(),
        )
    }
}
