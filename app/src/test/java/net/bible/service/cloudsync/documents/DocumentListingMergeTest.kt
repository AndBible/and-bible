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

class DocumentListingMergeTest {
    private fun meta(initials: String, version: String = "1.0", deleted: Boolean = false) =
        DocumentSyncMeta(
            initials = initials, name = initials, documentType = DocumentType.SWORD,
            version = version, size = 1, language = "en", sourceDevice = "dev",
            timestamp = 1L, deleted = deleted,
        )

    @Test
    fun upsertsChangedAndKeepsUnchanged() {
        val old = listOf(meta("KJV", "1.0"), meta("ESV", "1.0"))
        val result = mergeCloudListing(
            oldCache = old,
            changed = listOf(meta("ESV", "2.0")),          // ESV changed; KJV unchanged
            currentInitials = setOf("KJV", "ESV"),
            oldWatermark = 100L,
            matchedCreatedTimes = listOf(200L),
            failedCreatedTimes = emptyList(),
        )
        val byInitials = result.cache.associateBy { it.initials }
        assertEquals("1.0", byInitials["KJV"]!!.version)   // kept
        assertEquals("2.0", byInitials["ESV"]!!.version)   // upserted
        assertEquals(2, result.cache.size)
        assertEquals(200L, result.watermark)
    }

    @Test
    fun addsNewDocument() {
        val result = mergeCloudListing(
            oldCache = listOf(meta("KJV")),
            changed = listOf(meta("NIV")),
            currentInitials = setOf("KJV", "NIV"),
            oldWatermark = 0L,
            matchedCreatedTimes = listOf(50L),
            failedCreatedTimes = emptyList(),
        )
        assertEquals(setOf("KJV", "NIV"), result.cache.map { it.initials }.toSet())
    }

    @Test
    fun purgesVanishedFolder() {
        val result = mergeCloudListing(
            oldCache = listOf(meta("KJV"), meta("ESV")),
            changed = emptyList(),
            currentInitials = setOf("KJV"),                // ESV folder gone
            oldWatermark = 100L,
            matchedCreatedTimes = emptyList(),
            failedCreatedTimes = emptyList(),
        )
        assertEquals(listOf("KJV"), result.cache.map { it.initials })
        assertEquals(100L, result.watermark)               // nothing matched ⇒ unchanged
    }

    @Test
    fun coldStartIngestsAll() {
        val result = mergeCloudListing(
            oldCache = emptyList(),
            changed = listOf(meta("KJV"), meta("ESV")),
            currentInitials = setOf("KJV", "ESV"),
            oldWatermark = 0L,
            matchedCreatedTimes = listOf(10L, 30L),
            failedCreatedTimes = emptyList(),
        )
        assertEquals(setOf("KJV", "ESV"), result.cache.map { it.initials }.toSet())
        assertEquals(30L, result.watermark)
    }

    @Test
    fun tombstoneInChangedIsKept() {
        val result = mergeCloudListing(
            oldCache = listOf(meta("KJV", "1.0")),
            changed = listOf(meta("KJV", "1.0", deleted = true)),
            currentInitials = setOf("KJV"),                 // tombstone keeps the folder
            oldWatermark = 0L,
            matchedCreatedTimes = listOf(70L),
            failedCreatedTimes = emptyList(),
        )
        assertEquals(true, result.cache.single().deleted)
        assertEquals(70L, result.watermark)
    }

    @Test
    fun watermarkStopsBelowEarliestFailure() {
        val result = mergeCloudListing(
            oldCache = emptyList(),
            changed = listOf(meta("KJV")),                  // KJV parsed ok (createdTime 100)
            currentInitials = setOf("KJV", "ESV"),
            oldWatermark = 50L,
            matchedCreatedTimes = listOf(100L, 300L),       // ESV (300) failed
            failedCreatedTimes = listOf(300L),
        )
        assertEquals(listOf("KJV"), result.cache.map { it.initials })
        assertEquals(299L, result.watermark)               // min(failed) - 1
    }

    @Test
    fun watermarkNeverGoesBackwardOnFailure() {
        val result = mergeCloudListing(
            oldCache = emptyList(),
            changed = emptyList(),
            currentInitials = setOf("KJV"),
            oldWatermark = 1000L,
            matchedCreatedTimes = listOf(400L),             // a failure inside the margin window
            failedCreatedTimes = listOf(400L),
        )
        assertEquals(1000L, result.watermark)               // max(oldWatermark, 399) = 1000
    }
}
