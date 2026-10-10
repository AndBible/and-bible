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
import kotlinx.coroutines.flow.first
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
 * cloud sign-in); the `activityProvider` is a throwing stub, and the scope runs unconfined, and
 * its jobs are cancelled after each test. Document preference writes are drained before DB reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class SyncSettingsServiceImplTest {
    private val settings: DocumentSyncSettings get() = org.koin.java.KoinJavaComponent.get(DocumentSyncSettings::class.java)
    private val scopes = mutableListOf<CoroutineScope>()
    @After fun tearDown() {
        kotlinx.coroutines.runBlocking {
            org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.OrderedLauncher>(net.bible.sharedcore.platform.OrderedLauncher::class.java).join("document-sync-settings")
        }
        scopes.forEach { it.coroutineContext[Job]!!.cancel() }
        DatabaseResetter.resetDatabase()
    }

    private fun impl(): SyncSettingsServiceImpl {
        val service = SyncSettingsServiceImpl(
        scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) },
        // The store-routing methods never touch the Activity; a throwing provider proves that.
        // `error(...)` returns Nothing, which satisfies the `() -> ActivityBase` type.
        activityProvider = { error("activity not needed for store-routing tests") },
    )
        kotlinx.coroutines.runBlocking { settings.ensureLoaded() }
        return service
    }

    @Test fun initialRefreshLoadsPersistedDocumentSyncBeforeBuild() = kotlinx.coroutines.runBlocking {
        val db = net.bible.service.db.DatabaseContainer.instance.documentSyncDb
        db.documentSyncPreferencesDao().set(net.bible.android.database.DocumentSyncPreferences(enabled = true, wifiOnly = false))
        val scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
        val launcher = net.bible.sharedcore.platform.OrderedLauncher(scope)
        val fresh = DocumentSyncSettings({ db.documentSyncPreferencesDao() }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.AppEnvironment>(net.bible.sharedcore.platform.AppEnvironment::class.java))
        val service = SyncSettingsServiceImpl(scope, { error("activity not needed") }, fresh)
        val snapshot = kotlinx.coroutines.withTimeout(10_000) { service.snapshot.first { it.documentsEnabled } }
        assertTrue(snapshot.autoTogglesVisible)
        assertFalse(snapshot.wifiOnly)
        assertTrue(fresh.enabled)
    }

    @Test fun categoryDescriptionsRetainResourceText() {
        val strings = net.bible.test.testCoreStrings()
        val snapshot = impl().snapshot.value
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val ids = mapOf(
            "BOOKMARKS" to net.bible.android.activity.R.string.bookmarks_contents,
            "WORKSPACES" to net.bible.android.activity.R.string.workspaces_contents,
            "READINGPLANS" to net.bible.android.activity.R.string.reading_plans_content,
            "MYDOCUMENTS" to net.bible.android.activity.R.string.my_documents_contents,
            "AI_SETTINGS" to net.bible.android.activity.R.string.ai_settings_sync_contents,
            "PROGRESS" to net.bible.android.activity.R.string.progress_sync_contents,
        )
        SyncableDatabaseDefinition.entries.forEach { category ->
            assertEquals(context.getString(ids.getValue(category.name)), strings.syncCategoryContents(category.name))
            snapshot.categorySummary["sync_enable_${category.name.lowercase()}"]?.let {
                assertEquals(strings.syncCategoryContents(category.name), it)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class) fun unknownCategoryRejected() {
        net.bible.test.testCoreStrings().syncCategoryContents("unknown")
    }

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
        assertFalse(settings.autoDownload)
        service.setDocumentSyncToggle("sync_documents_wifi_only", false)
        assertFalse(settings.wifiOnly)
    }

    @Test fun categoryEnabled_persistsToSyncableDatabaseDefinition() {
        val service = impl()
        service.setCategoryEnabled("sync_enable_bookmarks", true)
        assertTrue(SyncableDatabaseDefinition.BOOKMARKS.isSyncEnabled(CommonUtils.settings))
        assertTrue(CommonUtils.settings.getBoolean("sync_enable_bookmarks", false))
    }

    @Test fun adapter_persistsViaCloudAdapters() {
        val service = impl()
        service.setAdapter("NEXT_CLOUD")
        assertEquals("NEXT_CLOUD", CommonUtils.settings.getString("sync_adapter", null))
    }
}
