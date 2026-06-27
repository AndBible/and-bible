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

import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem
import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSyncSummaryTest {
    private fun item(
        initials: String, localOnly: Boolean = false, cloudOnly: Boolean = false,
        update: Boolean = false, localNewer: Boolean = false, size: Long = 0,
    ) = DocumentStatusItem(
        initials = initials, name = initials, type = DocumentType.SWORD,
        cloudVersion = null, localVersion = null, cloudOnly = cloudOnly, localOnly = localOnly,
        updateAvailable = update, localNewer = localNewer, blocked = false, sizeBytes = size, category = BookCategory.BIBLE,
        canDeleteLocal = true,
    )

    @Test fun splitsUploadsAndDownloadsWithSizes() {
        val items = listOf(
            item("UP", localOnly = true, size = 100),
            item("DL", cloudOnly = true, size = 200),
            item("UPD", update = true, size = 50),
            item("SYNCED", size = 999),
        )
        val s = computeDocumentSyncSummary(items, blocked = emptySet())
        assertEquals(listOf("UP"), s.uploadInitials)
        assertEquals(listOf("DL", "UPD"), s.downloadInitials)
        assertEquals(100L, s.uploadBytes)
        assertEquals(250L, s.downloadBytes)
        assertEquals(1, s.uploadCount)
        assertEquals(2, s.downloadCount)
    }

    @Test fun localNewerCountsAsUpload() {
        // A locally-newer document is pushed as an upgrade by resolveUploads, so the enable dialog
        // must count it as an upload too (it was previously omitted, under-reporting the count).
        val items = listOf(
            item("UP", localOnly = true, size = 100),
            item("LN", localNewer = true, size = 30),
            item("DL", cloudOnly = true, size = 200),
        )
        val s = computeDocumentSyncSummary(items, blocked = emptySet())
        assertEquals(listOf("UP", "LN"), s.uploadInitials)
        assertEquals(130L, s.uploadBytes)
        assertEquals(listOf("DL"), s.downloadInitials)
    }

    @Test fun uploadAndDownloadSetsAreDisjoint() {
        // assembleStatusItems guarantees the upload-side flags (localOnly / localNewer) and the
        // download-side flags (cloudOnly / updateAvailable) are mutually exclusive per item, so the
        // summary must never place the same initials in both buckets nor double-count one.
        val items = listOf(
            item("UP", localOnly = true, size = 1),
            item("LN", localNewer = true, size = 2),
            item("DL", cloudOnly = true, size = 3),
            item("UPD", update = true, size = 4),
            item("SYNCED", size = 5),
        )
        val s = computeDocumentSyncSummary(items, blocked = emptySet())
        assertEquals(emptySet<String>(), s.uploadInitials.toSet() intersect s.downloadInitials.toSet())
        assertEquals(s.uploadCount + s.downloadCount, (s.uploadInitials + s.downloadInitials).toSet().size)
    }

    @Test fun blockedItemsAreExcluded() {
        val items = listOf(
            item("DL", cloudOnly = true, size = 200),
            item("BLK_DL", cloudOnly = true, size = 999),
            item("UP", localOnly = true, size = 100),
            item("BLK_UP", localOnly = true, size = 888),
        )
        val s = computeDocumentSyncSummary(items, blocked = setOf("BLK_DL", "BLK_UP"))
        assertEquals(listOf("DL"), s.downloadInitials)
        assertEquals(200L, s.downloadBytes)
        assertEquals(listOf("UP"), s.uploadInitials)
        assertEquals(100L, s.uploadBytes)
    }
}
