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
package net.bible.android.view.compose

import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.CachedCloudDocument
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.cloud.CloudDocumentsController
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.download.CloudDocumentsHost
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #4180 at the HOST level: the real [CloudDocumentsHost] with the real [DocumentSync] and listing cache.
 * Pins the wiring the controller/unit tests cannot see: the live refresh while a sync runs
 * (`deps().liveRefresh`), the filter-change cache reload (`onFilterChange =`), and that an optimistic
 * Remove stays removed across those reloads until the sync ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CloudDocumentsLiveRefreshHostTest {
    private companion object { const val P = "Z4180_" }
    private val activities = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private val sync get() = get<DocumentSync>(DocumentSync::class.java)
    private val cache get() = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()

    @Before fun loadSettings() = kotlinx.coroutines.runBlocking {
        get<net.bible.service.cloudsync.documents.DocumentSyncSettings>(
            net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java,
        ).ensureLoaded()
    }

    @After fun tearDown() {
        sync.notifyRunning(false)
        val jobs = activities.map { requireNotNull(it.get().lifecycleScope.coroutineContext[Job]) }
        activities.asReversed().forEach { it.destroy() }
        jobs.forEach { it.cancel() }
        DatabaseResetter.runResetBlocking(timeoutMs = 10_000) { jobs.forEach { it.join() } }
        DatabaseResetter.runResetBlocking(timeoutMs = 10_000) {
            get<net.bible.sharedcore.platform.OrderedLauncher>(net.bible.sharedcore.platform.OrderedLauncher::class.java)
                .join("document-sync-settings")
        }
        DatabaseResetter.resetDatabase()
    }

    private fun activity(): NavHostComposeActivity {
        val c = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.cloudDocuments()),
        )
        activities += c
        return c.create().get()
    }

    private fun CloudDocumentsHost.newController(): CloudDocumentsController = deps().controllerFor()

    private fun cached(initials: String) =
        CachedCloudDocument(P + initials, "Name $initials", "SWORD", "1.0", 10, "en", "BIBLE", "dev", 1L, null, false)

    private fun putCache(vararg initials: String) =
        kotlinx.coroutines.runBlocking { cache.replaceAll(initials.map { cached(it) }) }

    // Only this test's own rows: the installed test modules are listed too.
    private fun initialsOf(c: CloudDocumentsController) =
        c.items.value.map { it.initials }.filter { it.startsWith(P) }.map { it.removePrefix(P) }.sorted()

    /** Pumps the main looper (the host scope is the Activity's, on Main) until [condition] holds. */
    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for: $what" }
            ShadowLooper.idleMainLooper(250, java.util.concurrent.TimeUnit.MILLISECONDS) // advances delay() too
            Thread.sleep(25)
        }
    }

    @Test fun listFollowsTheCacheWhileASyncRunsAndStopsAfterwards() {
        val activity = activity()
        val host = activity.appHosts.cloudDocuments
        val controller = host.newController()
        val live = activity.lifecycleScope.launch { host.deps().liveRefresh(controller) }
        putCache("A")
        sync.notifyRunning(true)
        await("A to appear while the sync runs") { initialsOf(controller) == listOf("A") }
        putCache("A", "B")
        await("B to appear while the sync runs") { initialsOf(controller) == listOf("A", "B") }

        sync.notifyRunning(false)
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS); Thread.sleep(300)
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS)
        putCache("A", "B", "C")
        repeat(10) { ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS); Thread.sleep(50) }
        assertEquals(listOf("A", "B"), initialsOf(controller), "no cache polling once the sync has ended")
        live.cancel()
    }

    @Test fun aFilterChangeReloadsTheCacheInsteadOfFilteringAStaleList() {
        val activity = activity()
        val controller = activity.appHosts.cloudDocuments.newController()
        assertEquals(emptyList(), initialsOf(controller))
        putCache("A")
        controller.setStatusFilter(CloudDocFilter.CLOUD_ONLY)
        await("the filter change to re-read the cache") { initialsOf(controller) == listOf("A") }
    }

    @Test fun aRemovedRowStaysRemovedAcrossLiveReloadsUntilTheSyncEnds() {
        val activity = activity()
        val host = activity.appHosts.cloudDocuments
        val controller = host.newController()
        putCache("A", "B")
        controller.setItems(kotlinx.coroutines.runBlocking { host.deps().seedItems() })
        assertEquals(listOf("A", "B"), initialsOf(controller))

        sync.notifyRunning(true)
        val live = activity.lifecycleScope.launch { host.deps().liveRefresh(controller) }
        controller.applyRemoval(P + "A") // the queued removal has not touched the cache yet: A is still cached
        putCache("A", "B", "C")
        await("a live reload after the removal (C appears)") { "C" in initialsOf(controller) }
        assertEquals(listOf("B", "C"), initialsOf(controller), "the removed row must not come back mid-sync")

        controller.onSyncEnded()
        sync.notifyRunning(false)
        controller.setItems(kotlinx.coroutines.runBlocking { host.deps().seedItems() })
        assertTrue("A" in initialsOf(controller), "after the sync the (unchanged) cache is authoritative again")
        live.cancel()
    }
}
