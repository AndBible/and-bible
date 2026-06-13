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

import net.bible.service.sword.OsisToPlainText
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
    fun referenceWithOsisRef() {
        val xml = """<div>see <reference osisRef="Matt.5.3">Matt 5:3</reference></div>"""
        assertEquals("see [Matt 5:3](sword:///Matt.5.3)", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun referenceWithoutOsisRef() {
        val xml = """<div>see <reference>Matt 5:3</reference></div>"""
        assertEquals("see Matt 5:3", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun referenceModuleQualified() {
        val xml = """<div><reference osisRef="MHC:Matt.5.3">Matt 5:3</reference></div>"""
        assertEquals("[Matt 5:3](sword://MHC/Matt.5.3)", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun referenceModuleWithSpacesEncoded() {
        val xml = """<div><reference osisRef="My Commentary:Matt.5.3">Matt 5:3</reference></div>"""
        assertEquals("[Matt 5:3](sword://My%20Commentary/Matt.5.3)", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun referenceVerseRange() {
        val xml = """<div><reference osisRef="Matt.5.3-Matt.5.12">Matt 5:3-12</reference></div>"""
        assertEquals("[Matt 5:3-12](sword:///Matt.5.3-Matt.5.12)", OsisToPlainText.convert(parse(xml)))
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

    @Test
    fun nestedDivsParagraphBreaks() {
        val xml = """<root><div>First section.</div><div>Second section.</div></root>"""
        assertEquals("First section.\n\nSecond section.", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun listAndItems() {
        val xml = """<div><list><item>Apple</item><item>Banana</item><item>Cherry</item></list></div>"""
        assertEquals("- Apple\n- Banana\n- Cherry", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun tableRowsAndCells() {
        val xml = """<div><table><row><cell>Name</cell><cell>Value</cell></row><row><cell>A</cell><cell>1</cell></row></table></div>"""
        // Rows are separated by newlines; cells within a row are space-separated
        assertEquals("Name Value\n\nA 1", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun lineGroupPreservesStructure() {
        val xml = """<div><lg><l>Line one</l><l>Line two</l></lg></div>"""
        assertEquals("Line one\nLine two", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun commentaryWithDivParagraphs() {
        val xml = """<div><div><p>Commentary intro paragraph.</p><p>Detailed analysis here.</p></div><div><p>Another section with conclusions.</p></div></div>"""
        assertEquals("Commentary intro paragraph.\n\nDetailed analysis here.\n\nAnother section with conclusions.", OsisToPlainText.convert(parse(xml)))
    }

    // --- Anchor injection tests ---

    @Test
    fun anchorInjectionDisabledByDefault() {
        val xml = """<div><p><BVA ordinal="0">First sentence.</BVA></p></div>"""
        assertEquals("First sentence.", OsisToPlainText.convert(parse(xml)))
    }

    @Test
    fun anchorInjectionEveryBva() {
        val xml = """<div><p><BVA ordinal="0">First sentence.</BVA><BVA ordinal="1">Second sentence.</BVA></p><p><BVA ordinal="2">Third sentence.</BVA></p></div>"""
        val result = OsisToPlainText.convert(parse(xml), injectAnchors = true)
        assertEquals("[§0] First sentence.[§1] Second sentence.\n\n[§2] Third sentence.", result)
    }

    @Test
    fun anchorInjectionAtTitle() {
        val xml = """<div><title><BVA ordinal="0">Commentary Title</BVA></title><p><BVA ordinal="1">Text here.</BVA></p></div>"""
        val result = OsisToPlainText.convert(parse(xml), injectAnchors = true)
        assertEquals("## [§0] Commentary Title\n\n[§1] Text here.", result)
    }

    @Test
    fun anchorInjectionAllBvasInParagraph() {
        val xml = """<div><p><BVA ordinal="0">Sentence one.</BVA><BVA ordinal="1">Sentence two.</BVA><BVA ordinal="2">Sentence three.</BVA></p></div>"""
        val result = OsisToPlainText.convert(parse(xml), injectAnchors = true)
        assertEquals("[§0] Sentence one.[§1] Sentence two.[§2] Sentence three.", result)
    }

    @Test
    fun anchorInjectionBvaInRoot() {
        val xml = """<div><BVA ordinal="0">Text directly in root.</BVA></div>"""
        val result = OsisToPlainText.convert(parse(xml), injectAnchors = true)
        assertEquals("[§0] Text directly in root.", result)
    }

    @Test
    fun anchorInjectionNestedDivs() {
        val xml = """<div><div><BVA ordinal="0">Section one.</BVA></div><div><BVA ordinal="3">Section two.</BVA></div></div>"""
        val result = OsisToPlainText.convert(parse(xml), injectAnchors = true)
        assertEquals("[§0] Section one.\n\n[§3] Section two.", result)
    }

    @Test
    fun anchorInjectionWithExistingTestsUnchanged() {
        // Existing behavior must not change when injectAnchors=false
        val xml = """<div><verse osisID="Gen.1.1"><w lemma="strong:H07225">In</w> the beginning</verse></div>"""
        assertEquals("1. In the beginning", OsisToPlainText.convert(parse(xml), injectAnchors = false))
    }
}
