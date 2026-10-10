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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import net.bible.android.TestBibleApplication
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
 * OAuth and remote scans are not exercised here. Store routing runs unconfined; the suspended
 * category read uses a dedicated serial UI dispatcher. All scopes are cancelled after each test,
 * and document preference writes are drained before DB reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class SyncSettingsServiceImplTest {
    private val settings: DocumentSyncSettings get() = org.koin.java.KoinJavaComponent.get(DocumentSyncSettings::class.java)
    private val scopes = mutableListOf<CoroutineScope>()
    @After fun tearDown() {
        DatabaseResetter.resetDatabase(*scopes.toTypedArray())
    }

    private fun impl(
        scope: CoroutineScope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) },
        documentSettings: DocumentSyncSettings = settings,
        categories: net.bible.service.cloudsync.SyncSettingsCategories = net.bible.service.cloudsync.SyncSettingsCategories(CommonUtils.settings),
    ): SyncSettingsServiceImpl {
        fun <T : Any> get(type: Class<T>): T = org.koin.java.KoinJavaComponent.get(type)
        val service = SyncSettingsServiceImpl(
            scope, scope, documentSettings, get(net.bible.service.cloudsync.CloudSync::class.java),
            get(net.bible.service.cloudsync.CloudAdapterFactory::class.java), categories,
            get(net.bible.service.cloudsync.DocumentSyncProvider::class.java),
            get(net.bible.sharedcore.cloud.DocumentSyncStarter::class.java),
            get(net.bible.service.cloudsync.SyncSettingsPlatform::class.java), net.bible.test.testCoreStrings(),
            get(net.bible.sharedcore.platform.AppEnvironment::class.java), {},
        )
        kotlinx.coroutines.runBlocking { settings.ensureLoaded() }
        return service
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun suspendedCategoryDaoLeavesMainScopeResponsive() {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Main).also { scopes.add(it) }
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val dao = object : net.bible.android.database.SyncDao by com.nhaarman.mockitokotlin2.mock() {
            override suspend fun getLong(keyName: String): Long? {
                assertEquals(net.bible.service.cloudsync.LAST_SYNCHRONIZED_KEY, keyName)
                entered.countDown()
                release.await()
                return 1234L
            }
        }
        CommonUtils.settings.setBoolean("sync_enable_bookmarks", true)
        try {
            val service = impl(scope, categories = net.bible.service.cloudsync.SyncSettingsCategories(CommonUtils.settings) { dao })
            assertTrue("refresh reached the category DAO", entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val mainResponsive = java.util.concurrent.CountDownLatch(1)
            scope.launch { mainResponsive.countDown() }
            assertTrue("a suspended category read must not block the UI dispatcher", mainResponsive.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(service.snapshot.value.categorySummary.isEmpty())
            release.complete(Unit)
            kotlinx.coroutines.runBlocking {
                val loaded = kotlinx.coroutines.withTimeout(5000) { service.snapshot.first { it.categorySummary.isNotEmpty() } }
                assertEquals(
                    net.bible.test.testCoreStrings().syncCategoryContents("BOOKMARKS") + ".\n\n" +
                        net.bible.test.testCoreStrings().syncLastUpdated(net.bible.android.platform.AndroidSyncSettingsPlatform(
                            androidx.test.core.app.ApplicationProvider.getApplicationContext()).formatSyncTime(1234L)),
                    loaded.categorySummary.getValue("sync_enable_bookmarks"),
                )
            }
        } finally {
            release.complete(Unit)
            scope.cancel()
            kotlinx.coroutines.Dispatchers.resetMain()
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test fun initialRefreshLoadsPersistedDocumentSyncBeforeBuild() = kotlinx.coroutines.runBlocking {
        val db = net.bible.service.db.DatabaseContainer.instance.documentSyncDb
        db.documentSyncPreferencesDao().set(net.bible.android.database.DocumentSyncPreferences(enabled = true, wifiOnly = false))
        val scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
        val launcher = net.bible.sharedcore.platform.OrderedLauncher(scope)
        val fresh = DocumentSyncSettings({ db.documentSyncPreferencesDao() }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.AppEnvironment>(net.bible.sharedcore.platform.AppEnvironment::class.java))
        val service = impl(scope, documentSettings = fresh)
        val snapshot = kotlinx.coroutines.withTimeout(10_000) { service.snapshot.first { it.documentsEnabled } }
        assertTrue(snapshot.autoTogglesVisible)
        assertFalse(snapshot.wifiOnly)
        assertTrue(fresh.enabled)
    }

    @Test fun categoryDescriptionsRetainResourceText() {
        val strings = net.bible.test.testCoreStrings()
        val service = impl()
        val snapshot = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(5000) { service.snapshot.first { it.categorySummary.isNotEmpty() } }
        }
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
