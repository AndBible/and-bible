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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncSettingsTest {
    @Before
    fun reset() {
        DatabaseContainer.instance.documentSyncDb.apply {
            documentSyncPreferencesDao().clear()
            cloudListingStateDao().clear()
            cloudDocumentSyncTimestampDao().clear()
        }
    }

    @Test
    fun defaults() {
        assertFalse(DocumentSyncSettings.enabled)
        assertTrue(DocumentSyncSettings.wifiOnly)
        assertTrue(DocumentSyncSettings.autoDownload)
        assertFalse(DocumentSyncSettings.showRemovedDocuments)
        assertEquals(0L, DocumentSyncSettings.watermark)
        assertTrue(DocumentSyncSettings.blockList.all().isEmpty())
        assertNull(DocumentSyncSettings.syncTimestamp("KJV"))
    }

    @Test
    fun booleansRoundTripIndependently() {
        DocumentSyncSettings.enabled = true
        DocumentSyncSettings.autoUpload = false
        assertTrue(DocumentSyncSettings.enabled)
        assertFalse(DocumentSyncSettings.autoUpload)
        // Other prefs keep their defaults (independent columns of one row).
        assertTrue(DocumentSyncSettings.wifiOnly)
    }

    @Test
    fun watermarkAndTimestampsAndBlockListRoundTrip() {
        DocumentSyncSettings.watermark = 12345L
        assertEquals(12345L, DocumentSyncSettings.watermark)

        DocumentSyncSettings.setSyncTimestamp("KJV", 99L)
        assertEquals(99L, DocumentSyncSettings.syncTimestamp("KJV"))

        DocumentSyncSettings.blockList.block("ESV")
        assertTrue(DocumentSyncSettings.blockList.all().contains("ESV"))
    }
}
