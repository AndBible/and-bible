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
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class OsisToSpeakTextSaxHandlerTest {
    private var osisToSpeakTextSaxHandler: OsisToSpeakTextSaxHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToSpeakTextSaxHandler = OsisToSpeakTextSaxHandler(true)
    }

    @Test
    @Throws(Exception::class)
    fun testTopLevelReferenceIsWritten() {
        osisToSpeakTextSaxHandler!!.startDocument()
        osisToSpeakTextSaxHandler!!.startElement(null, null, "reference", null)
        osisToSpeakTextSaxHandler!!.characters("something".toCharArray(), 0, 9)
        osisToSpeakTextSaxHandler!!.endElement(null, null, "reference")
        osisToSpeakTextSaxHandler!!.endDocument()
        Assert.assertThat(osisToSpeakTextSaxHandler!!.writer.html, CoreMatchers.equalTo("something"))
    }

    @Test
    @Throws(Exception::class)
    fun testNoteWithReferenceIsAlsoWritten() {
        osisToSpeakTextSaxHandler!!.startDocument()
        osisToSpeakTextSaxHandler!!.startElement(null, null, "note", null)
        osisToSpeakTextSaxHandler!!.characters("inNoteBeforeRef".toCharArray(), 0, 15)
        osisToSpeakTextSaxHandler!!.startElement(null, null, "reference", null)
        osisToSpeakTextSaxHandler!!.characters("something".toCharArray(), 0, 9)
        osisToSpeakTextSaxHandler!!.endElement(null, null, "reference")
        osisToSpeakTextSaxHandler!!.characters("inNoteAfterRef".toCharArray(), 0, 14)
        osisToSpeakTextSaxHandler!!.endElement(null, null, "note")
        osisToSpeakTextSaxHandler!!.endDocument()
        Assert.assertThat(osisToSpeakTextSaxHandler!!.writer.html, CoreMatchers.equalTo("something"))
    }
}
