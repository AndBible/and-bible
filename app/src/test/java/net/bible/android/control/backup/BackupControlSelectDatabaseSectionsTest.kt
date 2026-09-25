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
package net.bible.android.control.backup

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Task 19 Step 1: [BackupControl.selectDatabaseSections] (spec §3.2 finding 9's second
 * hand-written select-all/none copy) is now [net.bible.android.view.activity.base.Dialogs.multiselect]
 * — a [AppDialogRequest.MultiChoice] on the app-wide [AppDialogController].
 *
 * **No Activity is built here, on purpose.** Neither [BackupControl.selectDatabaseSections] nor
 * [net.bible.android.view.activity.base.Dialogs.multiselect] touches `CurrentActivityHolder` — unlike
 * `Dialogs.showErrorMsg`'s no-Activity Toast fallback, `multiselect` just posts unconditionally. A
 * real `CalculatorComposeActivity`/`NavHostComposeActivity` mounts `AppDialogOverlay`, and idling the
 * REAL main Looper (`shadowOf(Looper.getMainLooper()).idle()`) while a sheet-shaped request
 * (`MultiChoice`/`SingleChoice`/`Options(asActionSheet)`) is pending hangs forever — the sheet's
 * `ModalBottomSheet`/`Popup` re-posts a frame task via `PopupLayout.pollForLocationOnScreenChange`
 * that a paused Robolectric Looper never finishes draining. `Dialogs.respond`/`.pending` are plain
 * Kotlin objects; driving them only through `yield()`/`advanceUntilIdle()` on this `runTest`'s own
 * `TestScope` (never the real Looper) sidesteps the whole class of hang, the same way
 * `DialogsShimTest`'s `multiselectReturnsTheChosenItemsInOrder`/`multiselectCancelIsEmpty` already do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackupControlSelectDatabaseSectionsTest {
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    @After
    fun tearDown() {
        dialogs.cancelAll()
    }

    @Test
    fun postsAMultiChoiceWithEveryFilePreselected() = runTest(timeout = 30.seconds) {
        val context = RuntimeEnvironment.getApplication()
        val available = listOf(
            net.bible.android.database.BookmarkDatabase.dbFileName,
            net.bible.android.database.ReadingPlanDatabase.dbFileName,
        )
        val result = async { BackupControl.selectDatabaseSections(context, available) }
        yield()

        val request = dialogs.pending.value!!.request as AppDialogRequest.MultiChoice
        assertEquals(context.getString(R.string.restore_backup_sections), request.title)
        assertEquals(listOf("0", "1"), request.selectedIds.sorted())
        assertEquals(listOf("0", "1"), request.options.map { it.value })
        assertEquals(context.getString(R.string.okay), request.confirmText)
        assertEquals(context.getString(R.string.cancel), request.dismissText)

        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("0", "1")))
        assertEquals(available, result.await())
    }

    @Test
    fun cancelReturnsAnEmptyList() = runTest(timeout = 30.seconds) {
        val context = RuntimeEnvironment.getApplication()
        val available = listOf(net.bible.android.database.BookmarkDatabase.dbFileName)
        val result = async { BackupControl.selectDatabaseSections(context, available) }
        yield()

        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertTrue(result.await().isEmpty())
    }

    @Test
    fun selectingASubsetReturnsOnlyThoseFiles() = runTest(timeout = 30.seconds) {
        val context = RuntimeEnvironment.getApplication()
        val available = listOf(
            net.bible.android.database.BookmarkDatabase.dbFileName,
            net.bible.android.database.ReadingPlanDatabase.dbFileName,
        )
        val result = async { BackupControl.selectDatabaseSections(context, available) }
        yield()

        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("1")))
        assertEquals(listOf(net.bible.android.database.ReadingPlanDatabase.dbFileName), result.await())
    }
}
