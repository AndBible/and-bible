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

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSyncStorageTest {
    private fun meta(initials: String, size: Long, deleted: Boolean = false) = DocumentSyncMeta(
        initials = initials, name = initials, documentType = DocumentType.SWORD, version = "1.0",
        size = size, language = "en", sourceDevice = "dev", timestamp = 0L, deleted = deleted,
    )

    @Test fun sumsNonDeletedArchiveSizes() {
        val metas = listOf(meta("A", 100), meta("B", 250))
        assertEquals(350L, sumCloudBytes(metas))
    }

    @Test fun excludesTombstones() {
        val metas = listOf(meta("A", 100), meta("GONE", 999, deleted = true), meta("B", 50))
        assertEquals(150L, sumCloudBytes(metas))
    }

    @Test fun emptyListIsZero() {
        assertEquals(0L, sumCloudBytes(emptyList()))
    }
}
