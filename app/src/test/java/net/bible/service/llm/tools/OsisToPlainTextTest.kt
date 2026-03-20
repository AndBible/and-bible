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

package net.bible.service.llm.tools

import org.jdom2.input.SAXBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class OsisToPlainTextTest {

    private fun parse(xml: String): org.jdom2.Element {
        return SAXBuilder().build(StringReader(xml)).rootElement
    }

    @Test
    fun basicVerseText() {
        val xml = """<div><verse osisID="Gen.1.1"><w lemma="strong:H07225">In</w> <w lemma="strong:H01254">the</w> beginning</verse></div>"""
        assertEquals("1. In the beginning", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun title() {
        val xml = """<div><title>The Beatitudes</title></div>"""
        assertEquals("## The Beatitudes", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun footnote() {
        val xml = """<div>text<note>Some manuscripts read X</note>more</div>"""
        assertEquals("text [Footnote: Some manuscripts read X]more", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun transChange() {
        val xml = """<div><transChange type="added">was</transChange></div>"""
        assertEquals("*was*", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun quoteWithMarker() {
        val xml = """<div><q marker="&#x2018;">word</q></div>"""
        assertEquals("\u2018word", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun milestoneSkipped() {
        val xml = """<div>before<milestone type="x-strongsMarkup"/>after</div>"""
        assertEquals("beforeafter", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun poetry() {
        val xml = """<div><l>line 1</l><l>line 2</l></div>"""
        assertEquals("line 1\nline 2", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun nestedWElements() {
        val xml = """<div><verse osisID="Gen.1.1"><w lemma="strong:H0430" morph="strongMorph:TH8804">God</w> <w lemma="strong:H01254">created</w></verse></div>"""
        assertEquals("1. God created", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun emptyInput() {
        val xml = """<div/>"""
        assertEquals("", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun hiElementBold() {
        val xml = """<div><hi type="bold">important</hi></div>"""
        assertEquals("**important**", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun hiElementItalic() {
        val xml = """<div><hi type="italic">emphasis</hi></div>"""
        assertEquals("*emphasis*", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun paragraphs() {
        val xml = """<div><p>First paragraph.</p><p>Second paragraph.</p></div>"""
        assertEquals("First paragraph.\n\nSecond paragraph.", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun referenceTextPreserved() {
        val xml = """<div>see <reference osisRef="Matt.5.3">Matt 5:3</reference></div>"""
        assertEquals("see Matt 5:3", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun divineNamePreserved() {
        val xml = """<div><divineName>LORD</divineName></div>"""
        assertEquals("LORD", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun chapterSkipped() {
        val xml = """<div><chapter osisID="Gen.1"/>text</div>"""
        assertEquals("text", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun multipleConsecutiveNewlinesCollapsed() {
        val xml = """<div><p>text1</p><p></p><p>text2</p></div>"""
        val result = OsisToPlainText.convert(parse(xml))
        // Verify no more than 2 consecutive newlines
        assert(!result.contains("\n\n\n")) { "Found 3+ consecutive newlines: $result" }
    }

    @Test
    fun xPrefixedElementsSkipped() {
        val xml = """<div>before<x-custom>hidden</x-custom>after</div>"""
        assertEquals("beforeafter", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun titleWithVersesIntegration() {
        val xml = """<div><title>Psalm 1</title><verse osisID="Ps.1.1">Blessed is the man</verse></div>"""
        assertEquals("## Psalm 1\n1. Blessed is the man", OsisToPlainText.convert(parse(xml)))
    }
}
