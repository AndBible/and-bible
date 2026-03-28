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

package net.bible.android.control.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UriAnalyzerTest {

    @Test
    fun swordUrlWithFragment() {
        val analyzer = UriAnalyzer()
        assertTrue(analyzer.analyze("sword://MHC/Matt.5.1#o5"))
        assertEquals("MHC", analyzer.book)
        assertEquals("Matt.5.1", analyzer.key)
        assertEquals("o5", analyzer.fragment)
        assertEquals(UriAnalyzer.DocType.SPECIFIC_DOC, analyzer.docType)
    }

    @Test
    fun swordUrlWithoutFragment() {
        val analyzer = UriAnalyzer()
        assertTrue(analyzer.analyze("sword://MHC/Matt.5.1"))
        assertEquals("MHC", analyzer.book)
        assertEquals("Matt.5.1", analyzer.key)
        assertNull(analyzer.fragment)
    }

    @Test
    fun fragmentWithLargeOrdinal() {
        val analyzer = UriAnalyzer()
        assertTrue(analyzer.analyze("sword://CalvinCommentaries/Eph.1.11#o42"))
        assertEquals("CalvinCommentaries", analyzer.book)
        assertEquals("Eph.1.11", analyzer.key)
        assertEquals("o42", analyzer.fragment)
    }

    @Test
    fun osisProtocolNoFragment() {
        val analyzer = UriAnalyzer()
        assertTrue(analyzer.analyze("osis://Matt.5.3"))
        assertNull(analyzer.fragment)
        assertEquals(UriAnalyzer.DocType.BIBLE, analyzer.docType)
    }

    @Test
    fun encodedInitialsDecoded() {
        val analyzer = UriAnalyzer()
        assertTrue(analyzer.analyze("sword://My%20Commentary/Matt.5.1"))
        assertEquals("My Commentary", analyzer.book)
        assertEquals("Matt.5.1", analyzer.key)
    }

    @Test
    fun encodedKeyDecoded() {
        val analyzer = UriAnalyzer()
        assertTrue(analyzer.analyze("sword://MHC/My%20Key"))
        assertEquals("MHC", analyzer.book)
        assertEquals("My Key", analyzer.key)
    }

    @Test
    fun rangeFragment() {
        val analyzer = UriAnalyzer()
        assertTrue(analyzer.analyze("sword://MHC/Matt.5.1#o5-10"))
        assertEquals("MHC", analyzer.book)
        assertEquals("Matt.5.1", analyzer.key)
        assertEquals("o5-10", analyzer.fragment)
    }

    @Test
    fun fragmentDoesNotAffectKeyParsing() {
        val analyzer = UriAnalyzer()
        assertTrue(analyzer.analyze("sword://MHC/Rom.8.28-30#o12"))
        assertEquals("MHC", analyzer.book)
        assertEquals("Rom.8.28-30", analyzer.key)
        assertEquals("o12", analyzer.fragment)
    }
}
