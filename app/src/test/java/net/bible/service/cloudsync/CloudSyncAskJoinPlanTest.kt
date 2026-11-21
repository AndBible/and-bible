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
package net.bible.service.cloudsync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Before
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.service.cloudsync.CloudSync.InitialOperation
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * `CloudSync.askJoinPlan`: the one collected "the cloud already has data" dialog that replaces the per-category
 * options dialog and its three untitled confirmations. Pinned directly with [JoinProbe]s so the test needs no
 * whole `SyncableDatabaseAccessor`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CloudSyncAskJoinPlanTest {
    private val controllers = mutableListOf<ActivityController<*>>()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    @get:Rule val dialogReset = AppDialogControllerResetRule()

    /** `askJoinPlan` does `withContext(Dispatchers.Main)`; under runTest the Robolectric main thread is blocked, so Main must be a test dispatcher (else the run hangs at 0% CPU). */
    @Before
    fun mainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        dialogs.cancelAll()
        // Do not leave a DatabaseContainer open in this test's Robolectric data dir for the next class.
        DatabaseResetter.resetDatabase()
    }

    private fun activity() {
        Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup()
    }

    private fun cloud() = KoinJavaComponent.get<CloudSync>(CloudSync::class.java)

    private fun probe(category: SyncableDatabaseDefinition, state: JoinState) =
        JoinProbe(category, state, resetNamespace = false, dropStaleState = false)

    private fun decision(category: SyncableDatabaseDefinition) = probe(category, JoinState.NeedsDecision("folder-${category.name}"))

    @Test
    fun oneRequestForTwoDecisionCategories() = runTest {
        activity()
        val answer = async {
            cloud().askJoinPlan(listOf(decision(SyncableDatabaseDefinition.BOOKMARKS), decision(SyncableDatabaseDefinition.WORKSPACES)))
        }
        advanceUntilIdle()
        val plan = dialogs.pending.value!!.request as AppDialogRequest.SyncJoinPlan
        assertEquals(2, plan.rows.size)
        assertEquals(setOf("BOOKMARKS", "WORKSPACES"), plan.rows.map { it.key }.toSet())
        assertEquals("merge", plan.defaultChoice)
        assertEquals(listOf("merge", "fetch", "create", "disable"), plan.choices.map { it.value })
        assertEquals(listOf(false, true, true, false), plan.choices.map { it.destructive })
        assertEquals("The cloud already has data", plan.title)
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertNull(answer.await())
        assertNull("no second dialog", dialogs.pending.value)
    }

    @Test
    fun choicesMapToOperations() = runTest {
        activity()
        val answer = async {
            cloud().askJoinPlan(listOf(decision(SyncableDatabaseDefinition.BOOKMARKS), decision(SyncableDatabaseDefinition.WORKSPACES)))
        }
        advanceUntilIdle()
        dialogs.respond(
            dialogs.pending.value!!.id,
            AppDialogResult.SyncJoinChoices(mapOf("BOOKMARKS" to "fetch", "WORKSPACES" to "disable")),
        )
        assertEquals(
            mapOf(
                SyncableDatabaseDefinition.BOOKMARKS to InitialOperation.FETCH_INITIAL,
                SyncableDatabaseDefinition.WORKSPACES to null,
            ),
            answer.await(),
        )
    }

    @Test
    fun cancelInConfirmationExecutesNothing() = runTest {
        activity()
        val answer = async { cloud().askJoinPlan(listOf(decision(SyncableDatabaseDefinition.WORKSPACES))) }
        advanceUntilIdle()
        assertNotNull(dialogs.pending.value)
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertNull(answer.await())
    }

    @Test
    fun createOnlyAsksNothing() = runTest {
        activity()
        val result = cloud().askJoinPlan(
            listOf(probe(SyncableDatabaseDefinition.BOOKMARKS, JoinState.NeedsCreate), probe(SyncableDatabaseDefinition.WORKSPACES, JoinState.NeedsCreate)),
        )
        assertNull("no request raised", dialogs.pending.value)
        assertEquals(
            mapOf(
                SyncableDatabaseDefinition.BOOKMARKS to InitialOperation.CREATE_NEW,
                SyncableDatabaseDefinition.WORKSPACES to InitialOperation.CREATE_NEW,
            ),
            result,
        )
    }

    @Test
    fun uploadNotesListCreateCategories() = runTest {
        activity()
        val answer = async {
            cloud().askJoinPlan(
                listOf(decision(SyncableDatabaseDefinition.WORKSPACES), probe(SyncableDatabaseDefinition.BOOKMARKS, JoinState.NeedsCreate)),
            )
        }
        advanceUntilIdle()
        val plan = dialogs.pending.value!!.request as AppDialogRequest.SyncJoinPlan
        assertEquals(listOf("WORKSPACES"), plan.rows.map { it.key })
        assertEquals(1, plan.uploadNotes.size)
        assertEquals(1, plan.createOutcomes.size)
        assertTrue(plan.uploadNotes.single().contains("uploaded from this device"))
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SyncJoinChoices(mapOf("WORKSPACES" to "merge")))
        assertEquals(
            mapOf(
                SyncableDatabaseDefinition.WORKSPACES to InitialOperation.MERGE,
                SyncableDatabaseDefinition.BOOKMARKS to InitialOperation.CREATE_NEW,
            ),
            answer.await(),
        )
    }
}
