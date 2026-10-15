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

import org.junit.Before
import org.junit.Test

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import fi.sykero.kdom.sax.AttributesImpl

class OsisToCopyTextSaxHandlerTest {

    lateinit var osisToCopyTextSaxHandlerVerseNumbersOff: OsisToCopyTextSaxHandler
    lateinit var osisToCopyTextSaxHandlerVerseNumbersOn: OsisToCopyTextSaxHandler

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToCopyTextSaxHandlerVerseNumbersOff = OsisToCopyTextSaxHandler(false, net.bible.service.format.SaxTextPolicy.MODERN)
        osisToCopyTextSaxHandlerVerseNumbersOn = OsisToCopyTextSaxHandler(true, net.bible.service.format.SaxTextPolicy.MODERN)

        osisToCopyTextSaxHandlerVerseNumbersOff.startDocument()
        osisToCopyTextSaxHandlerVerseNumbersOn.startDocument()
    }

    @Test
    @Throws(Exception::class)
    fun testSingleVerse() {
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement("", "", "chapter",
            LegacyAttributes().apply { addAttribute("","osisID","chapter",null,"Rom.8") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement("", "", "verse",
            LegacyAttributes().apply { addAttribute("","osisID","verse",null,"Rom.8.28") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.characters("And we know that all things...".toCharArray(), 0, 30)
        osisToCopyTextSaxHandlerVerseNumbersOff.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerVerseNumbersOff.endElement("", "", "verse")

        assertThat(osisToCopyTextSaxHandlerVerseNumbersOff.writer.html, equalTo("And we know that all things... "))
    }

    @Test
    fun missingOsisIdWithVerseNumbersStillCompletesDocument() {
        val handler = osisToCopyTextSaxHandlerVerseNumbersOn
        handler.startElement("", "", "verse", AttributesImpl())
        handler.characters("text".toCharArray(), 0, 4)
        handler.endElement("", "", "verse")
        // A subsequent ordinary verse must still infer its chapter and keep normal numbering.
        handler.startElement("", "", "verse", LegacyAttributes().apply {
            addAttribute("", "osisID", "osisID", "CDATA", "Rom.8.28")
        })
        handler.characters("ordinary".toCharArray(), 0, 8)
        handler.endElement("", "", "verse")
        handler.endDocument()

        assertThat(handler.writer.html, equalTo("0. text 28. ordinary "))
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleVerses() {
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement("", "", "chapter",
            LegacyAttributes().apply { addAttribute("","osisID","chapter",null,"Rom.8") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement("", "", "verse",
            LegacyAttributes().apply { addAttribute("","osisID","verse",null,"Rom.8.28") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("And we know that all things...".toCharArray(), 0, 30)
        osisToCopyTextSaxHandlerVerseNumbersOn.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement("", "", "verse")
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement("", "", "reference", fi.sykero.kdom.sax.AttributesImpl())
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement("", "", "verse",
            LegacyAttributes().apply { addAttribute("","osisID","verse",null,"Rom.8.29") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("For whom he did foreknow".toCharArray(), 0, 24)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement("", "", "verse")
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement("", "", "verse",
            LegacyAttributes().apply { addAttribute("","osisID","verse",null,"Rom.8.30") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("Moreover whom he did predestinate".toCharArray(), 0, 33)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement("", "", "verse")

        assertThat(osisToCopyTextSaxHandlerVerseNumbersOn.writer.html, equalTo("28. And we know that all things... 29. For whom he did foreknow 30. Moreover whom he did predestinate "))
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleVersesWithVerseNumbersOff() {
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement("", "", "chapter",
            LegacyAttributes().apply { addAttribute("","osisID","chapter",null,"Rom.8") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement("", "", "verse",
            LegacyAttributes().apply { addAttribute("","osisID","verse",null,"Rom.8.28") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.characters("And we know that all things...".toCharArray(), 0, 30)
        osisToCopyTextSaxHandlerVerseNumbersOff.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerVerseNumbersOff.endElement("", "", "verse")
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement("", "", "reference", fi.sykero.kdom.sax.AttributesImpl())
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement("", "", "verse",
            LegacyAttributes().apply { addAttribute("","osisID","verse",null,"Rom.8.29") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.characters("For whom he did foreknow".toCharArray(), 0, 24)
        osisToCopyTextSaxHandlerVerseNumbersOff.endElement("", "", "verse")
        osisToCopyTextSaxHandlerVerseNumbersOff.startElement("", "", "verse",
            LegacyAttributes().apply { addAttribute("","osisID","verse",null,"Rom.8.30") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOff.characters("Moreover whom he did predestinate".toCharArray(), 0, 33)
        osisToCopyTextSaxHandlerVerseNumbersOff.endElement("", "", "verse")

        assertThat(osisToCopyTextSaxHandlerVerseNumbersOff.writer.html, equalTo("And we know that all things... For whom he did foreknow Moreover whom he did predestinate "))
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleVersesAcrossChapter() {
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement("", "", "verse",
            LegacyAttributes().apply { addAttribute("","osisID","verse",null,"John.3.36") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("  ".toCharArray(), 0, 2)
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("He that believeth on the Son hath everlasting life: and".toCharArray(), 0, 55)
        osisToCopyTextSaxHandlerVerseNumbersOn.characters(" ".toCharArray(), 0, 1)
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("he that believeth not the Son shall not see life; but the wrath of God abideth on him.".toCharArray(), 0, 86)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement("", "", "verse")
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement("", "", "chapter")
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement("", "", "chapter",
            LegacyAttributes().apply { addAttribute("","osisID","chapter",null,"John.4") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.startElement("", "", "verse",
            LegacyAttributes().apply { addAttribute("","osisID","verse",null,"John.4.1") }
        )
        osisToCopyTextSaxHandlerVerseNumbersOn.characters("When therefore the Lord knew...".toCharArray(), 0, 31)
        osisToCopyTextSaxHandlerVerseNumbersOn.endElement("", "", "verse")

        assertThat(osisToCopyTextSaxHandlerVerseNumbersOn.writer.html, equalTo("36. He that believeth on the Son hath everlasting life: and he that believeth not the Son shall not see life; but the wrath of God abideth on him. 4:1. When therefore the Lord knew... "))
    }

}

/** Preserve synthetic nullable SAX type values without widening the production kdom builder. */
private class LegacyAttributes(private val delegate: AttributesImpl = AttributesImpl()) : fi.sykero.kdom.sax.Attributes by delegate {
    private val nullTypes = mutableSetOf<Int>()
    fun addAttribute(uri: String, local: String, qualified: String, type: String?, value: String) {
        if (type == null) nullTypes.add(delegate.length)
        delegate.addAttribute(uri, local, qualified, type ?: "CDATA", value)
    }
    override fun getType(index: Int): String? = if (index in nullTypes) null else delegate.getType(index)
}
