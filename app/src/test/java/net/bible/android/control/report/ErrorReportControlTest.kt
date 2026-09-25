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
package net.bible.android.control.report

import android.app.Activity
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.seconds
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.ASYNC_REQUEST_CODE_START
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Task 24 Step 4: `ErrorReportControl.showErrorDialog`'s `android.app.AlertDialog` (whose two
 * `setPositiveButton` calls meant "OK" was always silently overridden by "Send Report", and whose
 * `isCancelable && !report` arm was dead -- no caller ever passed `report = false`) moves to an
 * `AppDialogController` action sheet with exactly two actions plus Skip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ErrorReportControlTest {
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

    /** [ErrorReportControl.showErrorDialog] hops via `withContext(Dispatchers.Main)`; see
     *  `DialogsShimTest.runOnTestMain` for why plain `runTest` would deadlock here. */
    private fun <T> runOnTestMain(block: suspend kotlinx.coroutines.test.TestScope.() -> T) = runTest(timeout = 10.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun raisesAnUncancellableByDefaultActionSheetWithSkipAsDismiss() = runOnTestMain {
        val activity = activity()
        val answer = async { ErrorReportControl.showErrorDialog(activity, "boom <b>x</b>") }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertTrue(head.asActionSheet)
        assertFalse(head.cancellable)
        assertEquals("boom <b>x</b>", head.message)
        assertEquals(
            listOf(activity.getString(R.string.report_error), activity.getString(R.string.backup_and_restore)),
            head.options.map { it.label },
        )
        assertEquals(activity.getString(R.string.error_skip), head.dismissText)
        // Skip (Cancel / dismiss) ends the loop without reporting or opening backup.
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        answer.await()
        assertNull(dialogs.pending.value)
    }

    @Test
    fun selectingReportStartsBugReportReportBug() = runOnTestMain {
        val activity = activity()
        val answer = async { ErrorReportControl.showErrorDialog(activity, "boom") }
        advanceUntilIdle()
        val head = dialogs.pending.value!!.request as AppDialogRequest.Options
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(head.options[0].value))
        advanceUntilIdle()
        // BugReport.reportBug's first observable action (before its real file IO / delay) is
        // Hourglass.show() -- proof the REPORT branch really launched it, without driving the rest
        // of that (separately tested / out of scope) reporting flow.
        val progress = dialogs.progress.value!!.request as AppDialogRequest.Progress
        assertEquals(activity.getString(R.string.please_wait), progress.message)
        answer.cancel()
    }

    @Test
    fun selectingBackupRunsBackupThenAsksAgain() = runOnTestMain {
        val activity = activity()
        val answer = async { ErrorReportControl.showErrorDialog(activity, "boom") }
        advanceUntilIdle()
        val firstHead = dialogs.pending.value!!.request as AppDialogRequest.Options
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Selected(firstHead.options[1].value))
        advanceUntilIdle()
        // BackupControl.backupPopup(activity) -- not a NavHostComposeActivity here, so it opens
        // Backup via activity.awaitIntent(...) (BackupControl.awaitBackupFromAnotherActivity).
        // Simulate the user leaving that screen (first awaitIntent on a fresh activity -> request
        // code ASYNC_REQUEST_CODE_START + 0), so the loop's askAgain = true re-shows the sheet.
        shadowOf(activity).callOnActivityResult(ASYNC_REQUEST_CODE_START, Activity.RESULT_CANCELED, null)
        advanceUntilIdle()
        val secondHead = dialogs.pending.value!!.request as AppDialogRequest.Options
        assertEquals(firstHead.options.map { it.value }, secondHead.options.map { it.value })
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        answer.await()
    }
}
