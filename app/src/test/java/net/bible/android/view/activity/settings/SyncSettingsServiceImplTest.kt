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
package net.bible.android.view.activity.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [SyncSettingsServiceImpl]'s per-key routing to the correct backing store. The
 * activity-bound OAuth methods (`signIn`/`scanDocuments`) are NOT exercised here (they need a real
 * cloud sign-in); the `activityProvider` is a throwing stub, and the scope is never advanced, so the
 * fire-and-forget `CloudSync.start()` a category-enable launches is queued but not run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class SyncSettingsServiceImplTest {
    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private fun impl() = SyncSettingsServiceImpl(
        scope = CoroutineScope(Job()),
        // The store-routing methods never touch the Activity; a throwing provider proves that.
        // `error(...)` returns Nothing, which satisfies the `() -> ActivityBase` type.
        activityProvider = { error("activity not needed for store-routing tests") },
    )

    @Test fun serverUrl_valid_persistsToRealShared() {
        val service = impl()
        assertTrue(service.setText("cloud_sync_server_url", "https://example.com"))
        assertEquals("https://example.com", CommonUtils.realSharedPreferences.getString("cloud_sync_server_url", null))
    }

    @Test fun serverUrl_invalid_returnsFalse_notPersisted() {
        val service = impl()
        assertFalse(service.setText("cloud_sync_server_url", "not a url"))
        assertNull(CommonUtils.realSharedPreferences.getString("cloud_sync_server_url", null))
    }

    @Test fun username_persistsToRealShared() {
        val service = impl()
        assertTrue(service.setText("cloud_sync_username", "alice"))
        assertEquals("alice", CommonUtils.realSharedPreferences.getString("cloud_sync_username", null))
    }

    @Test fun documentToggle_persistsToDocumentSyncSettings() {
        val service = impl()
        service.setDocumentSyncToggle("sync_documents_auto_download", false)
        assertFalse(DocumentSyncSettings.autoDownload)
        service.setDocumentSyncToggle("sync_documents_wifi_only", false)
        assertFalse(DocumentSyncSettings.wifiOnly)
    }

    @Test fun categoryEnabled_persistsToSyncableDatabaseDefinition() {
        val service = impl()
        service.setCategoryEnabled("sync_enable_bookmarks", true)
        assertTrue(SyncableDatabaseDefinition.BOOKMARKS.syncEnabled)
        assertTrue(CommonUtils.settings.getBoolean("sync_enable_bookmarks", false))
    }

    @Test fun adapter_persistsViaCloudAdapters() {
        val service = impl()
        service.setAdapter("NEXT_CLOUD")
        assertEquals("NEXT_CLOUD", CommonUtils.settings.getString("sync_adapter", null))
    }
}
