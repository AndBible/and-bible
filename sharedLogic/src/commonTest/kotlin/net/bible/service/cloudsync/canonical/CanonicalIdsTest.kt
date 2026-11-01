/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync.canonical

import net.bible.android.database.IdType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CanonicalIdsTest {
    @Test fun sameInputSameId() {
        assertEquals(canonicalReadingPlanId("y1ot"), canonicalReadingPlanId("y1ot"))
    }

    @Test fun tableIsPartOfTheId() {
        assertNotEquals(canonicalId("ReadingPlan", "x"), canonicalId("MyDocument", "x"))
    }

    @Test fun keyPartsAreUnambiguous() {
        assertNotEquals(canonicalId("T", "a", "bc"), canonicalId("T", "ab", "c"))
    }

    @Test fun versionAndVariantBits() {
        val b = canonicalReadingPlanId("y1ot").toByteArray()!!
        assertEquals(0x80, b[6].toInt() and 0xf0)
        assertEquals(0x80, b[8].toInt() and 0xc0)
    }

    /** Pinned forever: a change here means already-synced ids no longer match. */
    @Test fun goldenIds() {
        val actual = listOf(
            canonicalReadingPlanId("y1ot"),
            canonicalReadingPlanStatusId("y1ot", 3),
            canonicalMyDocumentId("AIDocuments"),
            canonicalMemorizedVerseId(23145),
            canonicalLlmConfiguredModelId(IdType("00000000-0000-0000-0000-000000000001"), "gpt-x"),
        ).map { it.toString() }
        assertEquals(
            listOf(
                "4a39cf2f-3115-83bb-b428-c7aa6e65ed88",
                "8340b00b-4b48-8cc3-9d7a-65f4ece677e0",
                "29193812-897b-8943-a934-d0edfbefb600",
                "1e136052-849c-8f23-b2cc-2c46a29345e2",
                "7bb66443-e4d6-886f-a259-5c85fd8b580a",
            ),
            actual,
        )
    }

    /** The length prefix counts UTF-16 units and the hash input is UTF-8, so a non-ASCII key is pinned too. */
    @Test fun goldenIdOfANonAsciiKey() {
        assertEquals("0207c44c-4988-8e45-912a-d33548b54753", canonicalReadingPlanId("ääkkönen✓").toString())
    }
}
