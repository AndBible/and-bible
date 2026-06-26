/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndBible. If not, see <http://www.gnu.org/licenses/>.
 */

package net.bible.service.cloudsync.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DocumentSyncMetaTest {
    private val sample = DocumentSyncMeta(
        initials = "KJV",
        name = "King James Version",
        documentType = DocumentType.SWORD,
        version = "2.6",
        size = 16384000L,
        language = "en",
        sourceDevice = "device-1",
        timestamp = 1740000000000L,
        cipherKey = null,
        deleted = false,
    )

    @Test
    fun roundTripsThroughJson() {
        val restored = DocumentSyncMeta.fromJson(sample.toJson())
        assertEquals(sample, restored)
    }

    @Test
    fun unknownFieldsAreIgnoredForForwardCompatibility() {
        val json = """{"initials":"KJV","name":"King James Version","documentType":"SWORD",
            "version":"2.6","size":16384000,"language":"en","sourceDevice":"device-1",
            "timestamp":1740000000000,"cipherKey":null,"deleted":false,"futureField":"x"}"""
        val restored = DocumentSyncMeta.fromJson(json)
        assertEquals("KJV", restored.initials)
        assertFalse(restored.deleted)
    }

    @Test
    fun defaultsDeletedToFalseWhenMissing() {
        val json = """{"initials":"KJV","name":"n","documentType":"EPUB","version":"1.0",
            "size":1,"language":"en","sourceDevice":"d","timestamp":1}"""
        assertEquals(false, DocumentSyncMeta.fromJson(json).deleted)
    }

    @Test fun categoryRoundTrips() {
        val meta = DocumentSyncMeta(
            initials = "KJV", name = "King James", documentType = DocumentType.SWORD,
            version = "2.6", size = 100, language = "en", sourceDevice = "dev1",
            timestamp = 123L, category = "BIBLE",
        )
        val parsed = DocumentSyncMeta.fromJson(meta.toJson())
        assertEquals("BIBLE", parsed.category)
    }

    @Test fun categoryDefaultsToEmptyWhenMissing() {
        // Old-client JSON without a "category" key must still parse.
        val json = """{"initials":"KJV","name":"King James","documentType":"SWORD",
            "version":"2.6","size":100,"language":"en","sourceDevice":"dev1","timestamp":123}"""
        val parsed = DocumentSyncMeta.fromJson(json)
        assertEquals("", parsed.category)
    }
}
