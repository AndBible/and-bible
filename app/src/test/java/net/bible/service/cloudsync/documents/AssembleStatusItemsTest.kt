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

import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class AssembleStatusItemsTest {
    private fun meta(deleted: Boolean = false, version: String = "1.0") = DocumentSyncMeta(
        initials = "KJV", name = "KJV", documentType = DocumentType.SWORD, version = version,
        size = 100, language = "en", category = "BIBLE", sourceDevice = "dev", timestamp = 1L,
        deleted = deleted,
    )

    private fun local(version: String = "1.0") = LocalDoc(
        name = "KJV", version = version, category = BookCategory.BIBLE,
        type = DocumentType.SWORD, canDelete = true, installSizeBytes = null,
    )

    @Test fun excludesTombstoneByDefault() {
        val items = assembleStatusItems(listOf(meta(deleted = true)), emptyMap(), emptySet(), includeDeleted = false)
        assertEquals(emptyList<DocumentSync.DocumentStatusItem>(), items)
    }

    @Test fun includesTombstoneWhenRequested() {
        val row = assembleStatusItems(listOf(meta(deleted = true)), emptyMap(), emptySet(), includeDeleted = true).single()
        assertEquals(true, row.cloudDeleted)
        assertEquals(false, row.cloudOnly)
        assertEquals(false, row.localOnly)
        assertEquals("1.0", row.cloudVersion)
    }

    @Test fun tombstoneWithLocalCopyIsLocalOnlyAndDeleted() {
        val row = assembleStatusItems(
            listOf(meta(deleted = true)), mapOf("KJV" to local()), emptySet(), includeDeleted = true,
        ).single()
        assertEquals(true, row.cloudDeleted)
        assertEquals(true, row.localOnly)
        assertEquals(false, row.cloudOnly)
        assertEquals(false, row.updateAvailable)
        assertEquals(false, row.localNewer)
    }

    @Test fun tombstoneExcludedKeepsLocalAsLocalOnly() {
        // Toggle off: a tombstoned-but-installed document still shows as a plain local-only row.
        val row = assembleStatusItems(
            listOf(meta(deleted = true)), mapOf("KJV" to local()), emptySet(), includeDeleted = false,
        ).single()
        assertEquals(false, row.cloudDeleted)
        assertEquals(true, row.localOnly)
    }

    @Test fun liveCloudOnlyUnaffected() {
        val row = assembleStatusItems(listOf(meta()), emptyMap(), emptySet(), includeDeleted = true).single()
        assertEquals(false, row.cloudDeleted)
        assertEquals(true, row.cloudOnly)
    }
}
