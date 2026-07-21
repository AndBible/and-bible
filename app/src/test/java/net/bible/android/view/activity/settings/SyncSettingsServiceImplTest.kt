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

import net.bible.android.TestBibleApplication
import net.bible.service.cloudsync.CloudAdapters
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class SyncSettingsServiceImplTest {
    @After fun tearDown() = DatabaseResetter.resetDatabase()

    // --- defaults match sync_settings.xml ---

    @Test fun defaults_matchClassicXml() {
        val service = SyncSettingsServiceImpl()
        val s = service.snapshot.value

        // sync_enable_* default false
        assertFalse(s.enableBookmarks)
        assertFalse(s.enableWorkspaces)
        assertFalse(s.enableReadingPlans)
        assertFalse(s.enableMyDocuments)
        assertFalse(s.enableAiSettings)
        assertFalse(s.enableProgress)
        assertFalse(s.enableDocuments)
        // sync_documents_* default true
        assertTrue(s.autoDownload)
        assertTrue(s.autoUpload)
        assertTrue(s.autoDelete)
        assertTrue(s.wifiOnly)
        // credential strings default to empty
        assertEquals("", s.serverUrl)
        assertEquals("", s.username)
        assertEquals("", s.password)
        assertEquals("", s.folderPath)
    }

    @Test fun syncAdapter_defaultsToFirstEnabledAdapter_whenUnset() {
        val service = SyncSettingsServiceImpl()
        assertEquals(CloudAdapters.allEnabled.first().name, service.snapshot.value.syncAdapter)
    }

    @Test fun syncAdapterChoices_mirrorClassicAdapterList() {
        val service = SyncSettingsServiceImpl()
        val choices = service.snapshot.value.syncAdapterChoices

        assertEquals(CloudAdapters.allEnabled.map { it.name }, choices.map { it.value })
        assertEquals(CloudAdapters.allEnabled.map { it.displayName }, choices.map { it.label })
    }

    // --- writes persist to CommonUtils.settings and re-emit the snapshot ---

    @Test fun setBool_enableDocuments_writesToSettingsDb_andSnapshotUpdates() {
        val service = SyncSettingsServiceImpl()

        service.setBool("sync_enable_documents", true)

        assertTrue(CommonUtils.settings.getBoolean("sync_enable_documents", false))
        assertTrue(service.snapshot.value.enableDocuments)
    }

    @Test fun setString_serverUrl_roundTrips() {
        val service = SyncSettingsServiceImpl()

        service.setString("cloud_sync_server_url", "https://x")

        assertEquals("https://x", CommonUtils.settings.getString("cloud_sync_server_url", ""))
        assertEquals("https://x", service.snapshot.value.serverUrl)
    }

    @Test fun setBool_autoDownload_canBeTurnedOff() {
        val service = SyncSettingsServiceImpl()

        service.setBool("sync_documents_auto_download", false)

        assertFalse(CommonUtils.settings.getBoolean("sync_documents_auto_download", true))
        assertFalse(service.snapshot.value.autoDownload)
    }

    @Test fun newServiceInstance_picksUpPreviouslyPersistedValues() {
        val service1 = SyncSettingsServiceImpl()
        service1.setBool("sync_enable_bookmarks", true)
        service1.setString("cloud_sync_username", "alice")

        val service2 = SyncSettingsServiceImpl()

        assertTrue(service2.snapshot.value.enableBookmarks)
        assertEquals("alice", service2.snapshot.value.username)
    }
}
