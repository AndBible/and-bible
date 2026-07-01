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

package net.bible.android.view.activity.cloud

import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem
import net.bible.service.cloudsync.documents.DocumentType
import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDocumentsFilterTest {
    private fun item(
        initials: String,
        name: String = initials,
        cloudOnly: Boolean = false,
        localOnly: Boolean = false,
        updateAvailable: Boolean = false,
        blocked: Boolean = false,
        category: BookCategory? = BookCategory.BIBLE,
        cloudDeleted: Boolean = false,
    ) = DocumentStatusItem(
        initials = initials, name = name, type = DocumentType.SWORD,
        cloudVersion = "1.0", localVersion = "1.0",
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = updateAvailable,
        localNewer = false, blocked = blocked, sizeBytes = 0, category = category,
        canDeleteLocal = true, cloudDeleted = cloudDeleted,
    )

    private val items = listOf(
        item("KJV", name = "King James", localOnly = true, category = BookCategory.BIBLE),
        item("ESV", name = "English Standard", cloudOnly = true, category = BookCategory.BIBLE),
        item("MHC", name = "Matthew Henry", updateAvailable = true, category = BookCategory.COMMENTARY),
        item("STRONGS", name = "Strongs", blocked = true, category = BookCategory.DICTIONARY),
        item("NOCAT", name = "Unknown", cloudOnly = true, category = null),
        item("GONE", name = "Removed Book", cloudDeleted = true, category = BookCategory.BIBLE),
    )

    @Test fun allStatusNoQueryReturnsEverything() {
        assertEquals(6, filterCloudDocuments(items, CloudDocFilter.ALL, "", null).size)
    }

    @Test fun installedExcludesCloudOnly() {
        val r = filterCloudDocuments(items, CloudDocFilter.INSTALLED, "", null)
        assertEquals(listOf("KJV", "MHC", "STRONGS"), r.map { it.initials })
    }

    @Test fun cloudExcludesLocalOnly() {
        val r = filterCloudDocuments(items, CloudDocFilter.CLOUD, "", null)
        assertEquals(listOf("ESV", "MHC", "STRONGS", "NOCAT"), r.map { it.initials })
    }

    @Test fun updatesAndBlocked() {
        assertEquals(listOf("MHC"), filterCloudDocuments(items, CloudDocFilter.UPDATES, "", null).map { it.initials })
        assertEquals(listOf("STRONGS"), filterCloudDocuments(items, CloudDocFilter.BLOCKED, "", null).map { it.initials })
    }

    @Test fun nameQueryIsCaseInsensitiveSubstring() {
        assertEquals(listOf("KJV"), filterCloudDocuments(items, CloudDocFilter.ALL, "james", null).map { it.initials })
    }

    @Test fun categoryFilterMatchesExactCategory() {
        assertEquals(listOf("KJV", "ESV", "GONE"), filterCloudDocuments(items, CloudDocFilter.ALL, "", BookCategory.BIBLE).map { it.initials })
    }

    @Test fun nullCategoryItemMatchesOnlyAllCategory() {
        // Filtering by a concrete category never includes the null-category item.
        assertEquals(emptyList<String>(), filterCloudDocuments(items, CloudDocFilter.ALL, "", BookCategory.MAPS).map { it.initials })
    }

    @Test fun combinesStatusNameAndCategory() {
        val r = filterCloudDocuments(items, CloudDocFilter.CLOUD, "english", BookCategory.BIBLE)
        assertEquals(listOf("ESV"), r.map { it.initials })
    }

    @Test fun removedKeepsOnlyTombstones() {
        assertEquals(listOf("GONE"), filterCloudDocuments(items, CloudDocFilter.REMOVED, "", null).map { it.initials })
    }

    @Test fun deviceOnlyKeepsOnlyLocalOnlyDocuments() {
        // Only KJV is installed on this device and absent from the cloud.
        assertEquals(listOf("KJV"), filterCloudDocuments(items, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials })
    }

    @Test fun deviceOnlyExcludesTombstones() {
        // A still-installed tombstone is local-only in the data model but must not appear here —
        // it belongs under REMOVED, not "Only on this device".
        val ghost = listOf(item("GHOST", localOnly = true, cloudDeleted = true))
        assertEquals(emptyList<String>(), filterCloudDocuments(ghost, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials })
    }

    @Test fun deviceOnlyDiffersFromInstalled() {
        // INSTALLED includes the synced/cloud-backed local copies; DEVICE_ONLY is the strict subset.
        val both = listOf(
            item("LOCALONLY", localOnly = true),
            item("SYNCED"),                 // installed AND in cloud (neither localOnly nor cloudOnly)
        )
        assertEquals(listOf("LOCALONLY", "SYNCED"), filterCloudDocuments(both, CloudDocFilter.INSTALLED, "", null).map { it.initials })
        assertEquals(listOf("LOCALONLY"), filterCloudDocuments(both, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials })
    }

    @Test fun cloudOnlyKeepsOnlyCloudOnlyDocuments() {
        // ESV and NOCAT exist only in the cloud and are not installed on this device.
        assertEquals(listOf("ESV", "NOCAT"), filterCloudDocuments(items, CloudDocFilter.CLOUD_ONLY, "", null).map { it.initials })
    }

    @Test fun cloudOnlyExcludesTombstones() {
        // A cloud-only tombstone belongs under REMOVED, not "In cloud only".
        val ghost = listOf(item("GHOST", cloudOnly = true, cloudDeleted = true))
        assertEquals(emptyList<String>(), filterCloudDocuments(ghost, CloudDocFilter.CLOUD_ONLY, "", null).map { it.initials })
    }

    @Test fun cloudOnlyDiffersFromCloud() {
        // CLOUD includes the synced/local-backed cloud copies; CLOUD_ONLY is the strict subset.
        val both = listOf(
            item("CLOUDONLY", cloudOnly = true),
            item("SYNCED"),                 // installed AND in cloud (neither localOnly nor cloudOnly)
        )
        assertEquals(listOf("CLOUDONLY", "SYNCED"), filterCloudDocuments(both, CloudDocFilter.CLOUD, "", null).map { it.initials })
        assertEquals(listOf("CLOUDONLY"), filterCloudDocuments(both, CloudDocFilter.CLOUD_ONLY, "", null).map { it.initials })
    }

    @Test fun deviceOnlyAndCloudOnlyAreMutuallyExclusive() {
        // The two opposite filters never both match the same document, and together exclude synced items.
        val deviceOnly = filterCloudDocuments(items, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials }.toSet()
        val cloudOnly = filterCloudDocuments(items, CloudDocFilter.CLOUD_ONLY, "", null).map { it.initials }.toSet()
        assertEquals(emptySet<String>(), deviceOnly intersect cloudOnly)
    }

    @Test fun tombstoneExcludedFromAllNonRemovedStatusFilters() {
        // A removed (tombstone) document may still carry last-known blocked / update-available flags,
        // but it must surface only under ALL and REMOVED — never INSTALLED/CLOUD/UPDATES/BLOCKED.
        val ghost = listOf(item("GHOST", blocked = true, updateAvailable = true, cloudDeleted = true))
        assertEquals(listOf("GHOST"), filterCloudDocuments(ghost, CloudDocFilter.ALL, "", null).map { it.initials })
        assertEquals(listOf("GHOST"), filterCloudDocuments(ghost, CloudDocFilter.REMOVED, "", null).map { it.initials })
        for (status in listOf(CloudDocFilter.INSTALLED, CloudDocFilter.CLOUD, CloudDocFilter.UPDATES, CloudDocFilter.BLOCKED)) {
            assertEquals("$status must exclude tombstones",
                emptyList<String>(), filterCloudDocuments(ghost, status, "", null).map { it.initials })
        }
    }
}
