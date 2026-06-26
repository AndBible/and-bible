/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.navigation

import net.bible.android.database.SwordDocumentInfo
import org.junit.Test
import org.junit.Assert.*

/**
 * Test custom document ordering logic
 */
class DocumentOrderingTest {
    
    @Test
    fun testSwordDocumentInfoCustomOrder() {
        val doc1 = SwordDocumentInfo(
            initials = "KJV",
            name = "King James Version",
            abbreviation = "KJV",
            language = "en",
            repository = "CrossWire",
            customOrder = 1
        )
        
        val doc2 = SwordDocumentInfo(
            initials = "ESV",
            name = "English Standard Version", 
            abbreviation = "ESV",
            language = "en",
            repository = "CrossWire",
            customOrder = 2
        )
        
        assertEquals(1, doc1.customOrder)
        assertEquals(2, doc2.customOrder)
        assertNotNull(doc1.customOrder)
        assertNotNull(doc2.customOrder)
    }
    
    @Test
    fun testCustomOrderComparison() {
        val docs = listOf(
            SwordDocumentInfo("C", "C Bible", "C", "en", "repo", customOrder = 3),
            SwordDocumentInfo("A", "A Bible", "A", "en", "repo", customOrder = 1),
            SwordDocumentInfo("B", "B Bible", "B", "en", "repo", customOrder = 2),
            SwordDocumentInfo("D", "D Bible", "D", "en", "repo", customOrder = null) // Unordered
        )
        
        // Sort by custom order, nulls last
        val sorted = docs.sortedBy { it.customOrder ?: Int.MAX_VALUE }
        
        assertEquals("A", sorted[0].initials)
        assertEquals("B", sorted[1].initials) 
        assertEquals("C", sorted[2].initials)
        assertEquals("D", sorted[3].initials) // Null customOrder should be last
    }
}