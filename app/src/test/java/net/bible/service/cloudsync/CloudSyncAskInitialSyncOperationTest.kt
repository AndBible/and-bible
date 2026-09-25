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

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Task 24 Step 3: `CloudSync.initializeSync`'s "fetch from cloud, create new, or disable?" question
 * moves from an `android.app.AlertDialog` onto `AppDialogController`, via the extracted
 * `askInitialSyncOperation` (pinned directly here so the test doesn't need a whole
 * `SyncableDatabaseAccessor`). It is followed by the existing `Dialogs.simpleQuestion` confirmation
 * (already a shim over the same controller), so a full round trip answers TWO queued dialogs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CloudSyncAskInitialSyncOperationTest {
    private val controllers = mutableListOf<ActivityController<*>>()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        dialogs.cancelAll()
    }

    private fun activity(): ActivityBase =
        Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup().get()

    @Test
    fun raisesAnUncancellableThreeWayOptionsWithTheDatabaseDescription() = runTest {
        val activity = activity()
        val answer = async {
            CloudSync.askInitialSyncOperation(activity, SyncableDatabaseDefinition.WORKSPACES)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertEquals(activity.getString(R.string.cloud_sync_title), head.title)
        assertFalse(head.asActionSheet)
        assertFalse(head.cancellable)
        assertEquals(3, head.options.size)
        assertEquals(
            listOf(
                activity.getString(R.string.cloud_fetch_and_restore_initial),
                activity.getString(R.string.cloud_create_new),
                activity.getString(R.string.cloud_disable_sync),
            ),
            head.options.map { it.label },
        )
        assertTrue(head.message!!.contains(activity.getString(SyncableDatabaseDefinition.WORKSPACES.contentDescription)))
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertNull(answer.await())
    }

    @Test
    fun fetchThenConfirmedReturnsFetchInitial() = runTest {
        val activity = activity()
        val answer = async {
            CloudSync.askInitialSyncOperation(activity, SyncableDatabaseDefinition.WORKSPACES)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[0].value))
        advanceUntilIdle()
        val confirm = dialogs.pending.value!!.request as AppDialogRequest.Confirm
        assertEquals(
            activity.getString(R.string.are_you_sure_reset_local, activity.getString(SyncableDatabaseDefinition.WORKSPACES.contentDescription)),
            confirm.message,
        )
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
        assertEquals(CloudSync.InitialOperation.FETCH_INITIAL, answer.await())
    }

    @Test
    fun fetchThenDeclinedConfirmationReturnsNull() = runTest {
        val activity = activity()
        val answer = async {
            CloudSync.askInitialSyncOperation(activity, SyncableDatabaseDefinition.WORKSPACES)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[0].value))
        advanceUntilIdle()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertNull(answer.await())
    }

    @Test
    fun createNewThenConfirmedReturnsCreateNew() = runTest {
        val activity = activity()
        val answer = async {
            CloudSync.askInitialSyncOperation(activity, SyncableDatabaseDefinition.WORKSPACES)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[1].value))
        advanceUntilIdle()
        val confirm = dialogs.pending.value!!.request as AppDialogRequest.Confirm
        assertEquals(
            activity.getString(R.string.are_you_sure_reset_cloud, activity.getString(SyncableDatabaseDefinition.WORKSPACES.contentDescription)),
            confirm.message,
        )
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
        assertEquals(CloudSync.InitialOperation.CREATE_NEW, answer.await())
    }

    @Test
    fun disableSyncSelectionReturnsNullWithNoConfirmation() = runTest {
        val activity = activity()
        val answer = async {
            CloudSync.askInitialSyncOperation(activity, SyncableDatabaseDefinition.WORKSPACES)
        }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[2].value))
        assertNull(answer.await())
    }
}
