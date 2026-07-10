/*
 * Copyright (c) 2020-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.download

import net.bible.android.TEST_SDK
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent
import net.bible.sharedcore.navigation.DocInstallStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DownloadProgressBridgeTest {
    @After fun tearDown() { ABEventBus.unregisterAll() }

    @Test fun event_updates_status_keyed_by_docId() {
        val bridge = DownloadProgressBridge()
        bridge.setRepoIdentityMap(mapOf("REPO::ESV" to "ESV"))
        bridge.register()
        ABEventBus.post(DocumentDownloadEvent("REPO::ESV", DocumentInstallStatus.BEING_INSTALLED, 42))
        val s = bridge.statuses.value["ESV"]
        assertEquals(DocInstallStatus.BEING_INSTALLED, s?.status)
        assertEquals(42, s?.percentDone)
        bridge.unregister()
    }

    @Test fun unknown_repoIdentity_is_ignored() {
        val bridge = DownloadProgressBridge()
        bridge.register()
        ABEventBus.post(DocumentDownloadEvent("REPO::UNKNOWN", DocumentInstallStatus.BEING_INSTALLED, 10))
        assertEquals(emptyMap<String, RowDownloadStatus>(), bridge.statuses.value)
        bridge.unregister()
    }
}
