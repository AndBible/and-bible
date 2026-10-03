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

package net.bible.android.database

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncEntitiesTest {
    private val db get() = DatabaseContainer.instance.documentSyncDb

    @Test
    fun preferencesDefaultWhenEmptyAndRoundTrip() {
        db.documentSyncPreferencesDao().clear()
        assertNull("no row before any write", db.documentSyncPreferencesDao().get())

        db.documentSyncPreferencesDao().set(
            DocumentSyncPreferences(enabled = true, wifiOnly = false, blockList = setOf("KJV", "ESV"))
        )
        val read = db.documentSyncPreferencesDao().get()!!
        assertTrue(read.enabled)
        assertEquals(false, read.wifiOnly)
        assertEquals(setOf("KJV", "ESV"), read.blockList)
        // set() REPLACEs the single fixed-PK row (no second row accumulates).
        db.documentSyncPreferencesDao().set(read.copy(enabled = false))
        assertEquals(false, db.documentSyncPreferencesDao().get()!!.enabled)
    }

    @Test
    fun listingStateRoundTrip() {
        db.cloudListingStateDao().clear()
        assertNull(db.cloudListingStateDao().get())
        db.cloudListingStateDao().set(CloudListingState(watermark = 1740000000000L))
        assertEquals(1740000000000L, db.cloudListingStateDao().get()!!.watermark)
    }

    @Test
    fun timestampPerInitialsRoundTrip() {
        db.cloudDocumentSyncTimestampDao().clear()
        assertNull(db.cloudDocumentSyncTimestampDao().get("KJV"))
        db.cloudDocumentSyncTimestampDao().set(CloudDocumentSyncTimestamp("KJV", 42L))
        db.cloudDocumentSyncTimestampDao().set(CloudDocumentSyncTimestamp("ESV", 7L))
        assertEquals(42L, db.cloudDocumentSyncTimestampDao().get("KJV"))
        assertEquals(7L, db.cloudDocumentSyncTimestampDao().get("ESV"))
        db.cloudDocumentSyncTimestampDao().clear()
        assertNull(db.cloudDocumentSyncTimestampDao().get("KJV"))
    }
}
