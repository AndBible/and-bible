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
    /**
     * This test's own instance, on this test's [net.bible.sharedcore.platform.OrderedLauncher]. The Koin
     * singleton is built once per JVM and keeps the launcher of whichever test created it (the fixture swaps
     * the launcher per test and cancels the old one's scope), so joining this test's launcher did not wait for
     * its writes, and its already-loaded state could come from an earlier test's database.
     */
    private val settings: DocumentSyncSettings by lazy {
        fun <T : Any> get(type: Class<T>): T = org.koin.java.KoinJavaComponent.get(type)
        DocumentSyncSettings(
            { net.bible.service.db.DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao() },
            { net.bible.service.db.DatabaseContainer.instance.documentSyncDb.cloudListingStateDao() },
            { net.bible.service.db.DatabaseContainer.instance.documentSyncDb.cloudDocumentSyncTimestampDao() },
            get(net.bible.sharedcore.platform.OrderedLauncher::class.java),
            get(net.bible.sharedcore.platform.AppEnvironment::class.java),
        )
    }
    private val scopes = mutableListOf<CoroutineScope>()
    private val schedulers = mutableListOf<kotlinx.coroutines.test.TestCoroutineScheduler>()
    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        schedulers.forEach { it.runCurrent() }
        DatabaseResetter.resetDatabase(*scopes.toTypedArray())
    }

    private fun impl(
        scope: CoroutineScope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) },
        documentSettings: DocumentSyncSettings = settings,
        categories: net.bible.service.cloudsync.SyncSettingsCategories = net.bible.service.cloudsync.SyncSettingsCategories(CommonUtils.settings),
        writeScope: CoroutineScope = scope,
    ): SyncSettingsServiceImpl {
        fun <T : Any> get(type: Class<T>): T = org.koin.java.KoinJavaComponent.get(type)
        val service = SyncSettingsServiceImpl(
            scope, writeScope, documentSettings, get(net.bible.service.cloudsync.CloudSync::class.java),
            get(net.bible.service.cloudsync.CloudAdapterFactory::class.java), categories,
            get(net.bible.service.cloudsync.DocumentSyncProvider::class.java),
            get(net.bible.sharedcore.cloud.DocumentSyncStarter::class.java),
            get(net.bible.service.cloudsync.SyncSettingsPlatform::class.java), net.bible.test.testCoreStrings(),
            get(net.bible.sharedcore.platform.AppEnvironment::class.java), {},
        )
        kotlinx.coroutines.runBlocking { settings.ensureLoaded() }
        return service
    }

    @Test fun controllerToggleRefreshSeesLoadedMutationBeforeWorkerRuns() = kotlinx.coroutines.runBlocking {
        settings.ensureLoaded()
        settings.enabled = true
        val ui = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
        val worker = kotlinx.coroutines.test.StandardTestDispatcher().also { schedulers.add(it.scheduler) }
        val writes = CoroutineScope(Job() + worker).also { scopes.add(it) }
        val service = impl(ui, writeScope = writes)
        val controller = net.bible.sharedcore.settings.SyncSettingsController(service, ui,
            net.bible.sharedcore.settings.SyncSettingsLabels.forTest(), {})
        controller.onSwitch("sync_documents_auto_download", false)
        assertFalse("loaded toggle must mutate before the controller's refresh", settings.autoDownload)
        val row = controller.state.value.screen.items.filterIsInstance<net.bible.sharedcore.settings.SettingsItem.SwitchRow>()
            .first { it.key == "sync_documents_auto_download" }
        assertFalse("controller must not retain the old checked value", row.checked)
        controller.onSwitch("sync_enable_documents", false)
        assertFalse(service.snapshot.value.documentsEnabled)
        worker.scheduler.runCurrent()
        assertFalse(settings.enabled)
    }

    @Test fun coldTogglesWaitForSnapshotThenPublishInSubmissionOrder() = kotlinx.coroutines.runBlocking {
        val db = net.bible.service.db.DatabaseContainer.instance.documentSyncDb
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val prefsDao = object : net.bible.android.database.DocumentSyncPreferencesDao by db.documentSyncPreferencesDao() {
            override suspend fun get(): net.bible.android.database.DocumentSyncPreferences? {
                entered.complete(Unit)
                release.await()
                return net.bible.android.database.DocumentSyncPreferences(autoDownload = true, wifiOnly = false)
            }
        }
        val ui = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
        val worker = kotlinx.coroutines.test.StandardTestDispatcher().also { schedulers.add(it.scheduler) }
        val writes = CoroutineScope(Job() + worker).also { scopes.add(it) }
        val launcher = net.bible.sharedcore.platform.OrderedLauncher(ui)
        val fresh = DocumentSyncSettings({ prefsDao }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java))
        val service = impl(ui, fresh, writeScope = writes)
        entered.await()
        service.setDocumentSyncToggle("sync_documents_auto_download", false)
        service.setDocumentSyncToggle("sync_documents_auto_download", true)
        service.setDocumentSyncToggle("sync_documents_auto_download", false)
        release.complete(Unit)
        worker.scheduler.advanceUntilIdle()
        launcher.join("document-sync-settings")
        assertFalse(fresh.autoDownload)
        assertFalse("cold load must retain unrelated persisted preference", fresh.wifiOnly)
        assertFalse("mutation must publish after loading even without controller refresh", service.snapshot.value.autoDownload)
        assertFalse(db.documentSyncPreferencesDao().get()!!.autoDownload)
    }

    @Test fun rapidLoadedTogglesKeepSubmissionOrderWithDelayedWorker() = kotlinx.coroutines.runBlocking {
        settings.ensureLoaded()
        val ui = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
        val worker = kotlinx.coroutines.test.StandardTestDispatcher().also { schedulers.add(it.scheduler) }
        val writes = CoroutineScope(Job() + worker).also { scopes.add(it) }
        val service = impl(ui, writeScope = writes)
        service.setDocumentSyncToggle("sync_documents_wifi_only", false)
        service.setDocumentSyncToggle("sync_documents_wifi_only", true)
        service.setDocumentSyncToggle("sync_documents_wifi_only", false)
        assertFalse("last submitted write is visible without scheduling worker", settings.wifiOnly)
        worker.scheduler.runCurrent()
        org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.OrderedLauncher>(
            net.bible.sharedcore.platform.OrderedLauncher::class.java).join("document-sync-settings")
        assertFalse(net.bible.service.db.DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao().get()!!.wifiOnly)
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
