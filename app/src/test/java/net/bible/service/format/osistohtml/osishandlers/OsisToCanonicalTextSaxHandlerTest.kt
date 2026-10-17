/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.format.osistohtml.osishandlers

import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class OsisToCanonicalTextSaxHandlerTest {
    private var osisToCanonicalTextSaxHandler: OsisToCanonicalTextSaxHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToCanonicalTextSaxHandler = OsisToCanonicalTextSaxHandler(textPolicy = net.bible.service.format.SaxTextPolicy.MODERN)
        osisToCanonicalTextSaxHandler!!.startDocument()
    }

    @Test
    @Throws(Exception::class)
    fun testTopLevelReferenceIsWritten() {
        osisToCanonicalTextSaxHandler!!.startElement("", "", "reference", fi.sykero.kdom.sax.AttributesImpl())
        osisToCanonicalTextSaxHandler!!.characters("something".toCharArray(), 0, 9)
        osisToCanonicalTextSaxHandler!!.endElement("", "", "reference")
        Assert.assertThat(osisToCanonicalTextSaxHandler!!.writer.html, CoreMatchers.equalTo("something"))
    }

    @Test
    @Throws(Exception::class)
    fun testReferenceInNoteIsNotWritten() {
        osisToCanonicalTextSaxHandler!!.startElement("", "", "note", fi.sykero.kdom.sax.AttributesImpl())
        osisToCanonicalTextSaxHandler!!.startElement("", "", "reference", fi.sykero.kdom.sax.AttributesImpl())
        osisToCanonicalTextSaxHandler!!.characters("something".toCharArray(), 0, 9)
        osisToCanonicalTextSaxHandler!!.endElement("", "", "reference")
        osisToCanonicalTextSaxHandler!!.endElement("", "", "note")
        Assert.assertThat(osisToCanonicalTextSaxHandler!!.writer.html, Matchers.isEmptyString())
    }

    @Test
    @Throws(Exception::class)
    fun testExtraSpacesAreRemoved() {
        osisToCanonicalTextSaxHandler!!.startElement("", "", "verse", fi.sykero.kdom.sax.AttributesImpl())
        osisToCanonicalTextSaxHandler!!.characters("  ".toCharArray(), 0, 2)
        osisToCanonicalTextSaxHandler!!.characters(" ".toCharArray(), 0, 1)
        osisToCanonicalTextSaxHandler!!.characters("Be exalted,".toCharArray(), 0, 11)
        osisToCanonicalTextSaxHandler!!.characters(" ".toCharArray(), 0, 1)
        osisToCanonicalTextSaxHandler!!.characters("  ".toCharArray(), 0, 2)
        osisToCanonicalTextSaxHandler!!.characters("O God".toCharArray(), 0, 5)
        osisToCanonicalTextSaxHandler!!.endElement("", "", "verse")

        // the last verse tag just add an extra space at the end but that is required to separate verses and is easier to leave.
        Assert.assertThat(osisToCanonicalTextSaxHandler!!.writer.html, CoreMatchers.equalTo("Be exalted, O God "))
    }

    /** commons-lang3 `isWhitespace` (java Character.isWhitespace) is false for NBSP, so a lone NBSP node is written verbatim. */
    @Test
    fun testNonBreakingSpaceOnlyNodeIsWrittenVerbatim() {
        osisToCanonicalTextSaxHandler!!.startElement("", "", "reference", fi.sykero.kdom.sax.AttributesImpl())
        osisToCanonicalTextSaxHandler!!.characters("\u00a0".toCharArray(), 0, 1)
        osisToCanonicalTextSaxHandler!!.endElement("", "", "reference")
        Assert.assertThat(osisToCanonicalTextSaxHandler!!.writer.html, CoreMatchers.equalTo("\u00a0"))
    }
}
