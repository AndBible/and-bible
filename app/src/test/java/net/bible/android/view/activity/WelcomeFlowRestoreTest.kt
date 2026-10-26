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
package net.bible.android.view.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.FileNotFoundException
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WelcomeFlowRestoreTest {
    @get:Rule val dialogReset = AppDialogControllerResetRule()

    @After fun resetDatabase() {
        net.bible.test.DatabaseResetter.resetDatabase()
    }

    @Test fun unreadableBackupShowsErrorAndWelcomeCanRetry() = assertRestoreFailure {
        throw FileNotFoundException("Backup provider is unavailable")
    }

    @Test fun deniedBackupPermissionShowsErrorAndWelcomeCanRetry() = assertRestoreFailure {
        throw SecurityException("Backup permission denied")
    }

    @Test fun truncatedZipShowsErrorAndWelcomeCanRetry() = assertRestoreFailure {
        // A ZIP local-header signature without the required header fields.
        byteArrayOf(0x50, 0x4b, 0x03, 0x04).inputStream()
    }

    private fun assertRestoreFailure(openInput: () -> java.io.InputStream) {
        resetComposeUiDispatcher()
        val context = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val uri = Uri.parse("content://welcome-restore-test/backup.abdb.zip")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri, openInput)
        val dialogs = KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java)
        val previousFirstTime = firstTime
        firstTime = false // Do not arm the unrelated delayed night-mode recreation.
        val controller = Robolectric.buildActivity(NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(context, NavRoutes.WELCOME))
            .apply { create().start().resume().visible() }
        try {
            val activity = controller.get()
            shadowOf(Looper.getMainLooper()).idle()
            activity.welcomeFlow.onRestore()
            shadowOf(Looper.getMainLooper()).idle()
            val picker = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_GET_CONTENT, picker.intent.action)
            shadowOf(activity).receiveResult(picker.intent, Activity.RESULT_OK, Intent().setData(uri))

            val deadline = System.nanoTime() + 10_000_000_000L
            while (dialogs.pending.value == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).runOneTask()
                Thread.sleep(10)
            }
            val error = requireNotNull(dialogs.pending.value) { "Restore failure must show an error dialog" }
            assertTrue(error.request is AppDialogRequest.Options)
            assertTrue((error.request as AppDialogRequest.Options).message!!.contains("Failed to process backup file"))
            assertNull(dialogs.progress.value)
            dialogs.respond(error.id, AppDialogResult.Cancel)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(NavRoutes.WELCOME, activity.currentRouteForTest())
            assertFalse(activity.isFinishing)
            activity.welcomeFlow.onRestore()
            shadowOf(Looper.getMainLooper()).idle()
            val retry = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_GET_CONTENT, retry.intent.action)
            shadowOf(activity).receiveResult(retry.intent, Activity.RESULT_CANCELED, null)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(dialogs.pending.value)
            assertEquals(NavRoutes.WELCOME, activity.currentRouteForTest())
        } finally {
            dialogs.cancelAll()
            controller.close()
            firstTime = previousFirstTime
        }
    }
}
