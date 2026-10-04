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
package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentDocumentsTest {
    @Test fun aNewDocumentGoesToTheFront() {
        assertEquals(listOf("ESV", "KJV"), RecentDocuments.touched(listOf("KJV"), "ESV"))
    }

    @Test fun aKnownDocumentMovesToTheFrontWithoutDuplicating() {
        assertEquals(
            listOf("KJV", "ESV", "NET"),
            RecentDocuments.touched(listOf("ESV", "KJV", "NET"), "KJV"),
        )
    }

    @Test fun touchingTheFrontDocumentChangesNothing() {
        val list = listOf("ESV", "KJV")
        assertEquals(list, RecentDocuments.touched(list, "ESV"))
    }

    @Test fun theListIsCappedAtMaxAndDropsTheOldest() {
        val full = (1..RecentDocuments.MAX).map { "D$it" }
        val out = RecentDocuments.touched(full, "NEW")
        assertEquals(RecentDocuments.MAX, out.size)
        assertEquals("NEW", out.first())
        assertEquals("D${RecentDocuments.MAX - 1}", out.last(), "the oldest entry is dropped")
    }

    @Test fun blankInitialsAreIgnored() {
        val list = listOf("ESV")
        assertEquals(list, RecentDocuments.touched(list, ""))
        assertEquals(list, RecentDocuments.touched(list, "   "))
    }
}
