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

package net.bible.service.cloudsync.documents

import java.util.concurrent.atomic.AtomicBoolean
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncServiceForegroundTest {
    /**
     * Android kills the process with [android.app.RemoteServiceException.ForegroundServiceDidNotStartInTimeException]
     * if a `startForegroundService()` call is not followed by `startForeground()` within ~5s.
     * [DocumentSyncService.start] calls `startForegroundService()` on every enqueued batch, so
     * `onStartCommand` must call `startForeground()` for every START intent — including a batch
     * that arrives while a drain from a previous batch is still running (the non-`fresh` path).
     */
    @Test
    fun startWhileDrainAlreadyRunningStillEntersForeground() {
        val context = RuntimeEnvironment.getApplication()
        // Build the START intent through the production API so we don't couple to private action/extra keys.
        DocumentSyncService.start(context, pushInitials = listOf("nonexistent-book"), downloadInitials = emptyList())
        val intent = shadowOf(context).nextStartedService

        val service = Robolectric.buildService(DocumentSyncService::class.java, intent).create().get()

        // Simulate "a drain from an earlier batch is still running": active is already true, so this
        // start is not 'fresh'. startForegroundService() was still called, so startForeground() is mandatory.
        activeFlag(service).set(true)

        service.onStartCommand(intent, 0, 1)

        assertNotNull(
            "startForeground() must be called for every START intent, even when a drain is already running",
            shadowOf(service).lastForegroundNotification
        )
    }

    private fun activeFlag(service: DocumentSyncService): AtomicBoolean =
        DocumentSyncService::class.java.getDeclaredField("active").run {
            isAccessible = true
            get(service) as AtomicBoolean
        }
}
