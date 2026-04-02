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

package net.bible.service.sword.esword

import org.crosswire.jsword.versification.BibleBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ESwordBookTest {

    // --- convertRtfToOsis tests ---

    @Test
    fun `empty string returns empty`() {
        assertEquals("", convertRtfToOsis(""))
    }

    @Test
    fun `non-RTF text passes through unchanged`() {
        assertEquals("Hello world", convertRtfToOsis("Hello world"))
    }

    @Test
    fun `plain text with XML special characters is escaped`() {
        // Input starts with '\' so it goes through RTF parser, which XML-escapes text
        val rtf = "\\cf1 a < b & c > d"
        assertEquals("a &lt; b &amp; c &gt; d", convertRtfToOsis(rtf))
    }

    @Test
    fun `typical e-Sword verse strips RTF preamble`() {
        val rtf = "\\viewkind4\\uc1\\nowidctlpar\\tx720\\tx1440\\cf1\\lang1033\\f0 In the beginning God created the heaven and the earth. \\cf0\\i0\\b0\\ulnone\\nosupersub"
        val result = convertRtfToOsis(rtf)
        assertEquals("In the beginning God created the heaven and the earth.", result)
    }

    @Test
    fun `bold formatting is converted to OSIS hi tags`() {
        val rtf = "\\b Bold text\\b0  normal"
        assertEquals("<hi type=\"bold\">Bold text</hi> normal", convertRtfToOsis(rtf))
    }

    @Test
    fun `italic formatting is converted to OSIS hi tags`() {
        val rtf = "\\i Italic text\\i0  normal"
        assertEquals("<hi type=\"italic\">Italic text</hi> normal", convertRtfToOsis(rtf))
    }

    @Test
    fun `line breaks are converted to lb tags`() {
        val rtf = "\\line "
        assertEquals("<lb/>", convertRtfToOsis(rtf))
    }

    @Test
    fun `par is converted to lb tag`() {
        val rtf = "\\par "
        assertEquals("<lb/>", convertRtfToOsis(rtf))
    }

    @Test
    fun `superscript is converted to OSIS hi super`() {
        val rtf = "\\super 1\\nosupersub  text"
        assertEquals("<hi type=\"super\">1</hi> text", convertRtfToOsis(rtf))
    }

    @Test
    fun `bold section with line break from Esther apocrypha`() {
        val rtf = "\\cf2\\b\\f0 Addition to Esther 1\\line\\b0 (Est 10:4) Then Mardocheus said"
        val result = convertRtfToOsis(rtf)
        assertEquals("<hi type=\"bold\">Addition to Esther 1<lb/></hi>(Est 10:4) Then Mardocheus said", result)
    }

    @Test
    fun `hex escape is decoded`() {
        val rtf = "\\'e9" // é
        assertEquals("\u00e9", convertRtfToOsis(rtf))
    }

    @Test
    fun `unicode escape is decoded`() {
        val rtf = "\\u8212? text" // em-dash, '?' is fallback
        assertEquals("\u2014 text", convertRtfToOsis(rtf))
    }

    @Test
    fun `negative unicode escape is decoded`() {
        val rtf = "\\u-4064? text" // negative = 65536 - 4064 = 61472
        val expected = 61472.toChar()
        assertEquals("${expected} text", convertRtfToOsis(rtf))
    }

    @Test
    fun `escaped backslash and braces`() {
        // RTF: a\\b\{c\}d → text: a\b{c}d
        // In Kotlin string literals, we need to double-escape
        val rtf = "\\cf1 a\\\\b\\{c\\}d"
        assertEquals("a\\b{c}d", convertRtfToOsis(rtf))
    }

    @Test
    fun `font table group is skipped`() {
        val rtf = "{\\fonttbl{\\f0 Times New Roman;}}Hello"
        assertEquals("Hello", convertRtfToOsis(rtf))
    }

    @Test
    fun `color table group is skipped`() {
        val rtf = "{\\colortbl;\\red0\\green0\\blue0;}Hello"
        assertEquals("Hello", convertRtfToOsis(rtf))
    }

    @Test
    fun `control words with numeric params are stripped`() {
        val rtf = "\\fs22\\sa200\\sl276 text"
        assertEquals("text", convertRtfToOsis(rtf))
    }

    @Test
    fun `multiple formatting changes in sequence`() {
        val rtf = "\\b\\i Bold italic\\i0\\b0  plain"
        assertEquals("<hi type=\"bold\"><hi type=\"italic\">Bold italic</hi></hi> plain", convertRtfToOsis(rtf))
    }

    @Test
    fun `unclosed bold is auto-closed at end`() {
        val rtf = "\\b Bold text"
        assertEquals("<hi type=\"bold\">Bold text</hi>", convertRtfToOsis(rtf))
    }

    // --- Real e-Sword data tests ---

    @Test
    fun `real bblx Gen 1-1`() {
        val rtf = "\\viewkind4\\uc1\\nowidctlpar\\tx720\\tx1440\\tx2160\\tx2880\\tx3600\\tx4320\\tx5040\\tx5760\\tx6480\\tx7200\\tx7920\\tx8640\\tx9360\\tx10080\\cf1\\lang1033\\f0 In the beginning God created the heaven and the earth. \\cf0\\i0\\b0\\ulnone\\nosupersub"
        assertEquals("In the beginning God created the heaven and the earth.", convertRtfToOsis(rtf))
    }

    @Test
    fun `real bblx John 3-16`() {
        val rtf = "\\viewkind4\\uc1\\nowidctlpar\\tx720\\tx1440\\tx2160\\tx2880\\tx3600\\tx4320\\tx5040\\tx5760\\tx6480\\tx7200\\tx7920\\tx8640\\tx9360\\tx10080\\cf1\\lang1033\\f0 For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life. \\cf0\\i0\\b0\\ulnone\\nosupersub"
        assertEquals("For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life.", convertRtfToOsis(rtf))
    }

    @Test
    fun `real bblx Esther apocrypha with bold headings and line breaks`() {
        val rtf = "\\viewkind4\\uc1\\sa200\\sl276\\slmult1\\cf1\\lang1033\\f0  For Mordecai. \\line\\line\\cf2\\b\\f0 Addition to Esther 1\\line\\b0 (Est 10:4) Then Mardocheus said."
        val result = convertRtfToOsis(rtf)
        assertEquals("For Mordecai. <lb/><lb/><hi type=\"bold\">Addition to Esther 1<lb/></hi>(Est 10:4) Then Mardocheus said.", result)
    }

    // --- Edge cases ---

    @Test
    fun `consecutive line breaks`() {
        val rtf = "\\line\\line\\line "
        assertEquals("<lb/><lb/><lb/>", convertRtfToOsis(rtf))
    }

    @Test
    fun `bold immediately after control word without space`() {
        val rtf = "\\cf2\\b\\f0 Bold"
        assertEquals("<hi type=\"bold\">Bold</hi>", convertRtfToOsis(rtf))
    }

    @Test
    fun `null-like empty scripture`() {
        assertEquals("", convertRtfToOsis(""))
    }

    @Test
    fun `nested brace groups`() {
        val rtf = "{\\fonttbl{\\f0{\\fcharset0 Times;}}}Text after"
        assertEquals("Text after", convertRtfToOsis(rtf))
    }

    @Test
    fun `truncated control word at end of string`() {
        val rtf = "\\cf1 Hello\\b"
        // \b at end with no text after — opens bold, auto-closed at end (empty tag)
        assertEquals("Hello<hi type=\"bold\"></hi>", convertRtfToOsis(rtf))
    }

    @Test
    fun `truncated backslash at end of string`() {
        val rtf = "\\cf1 Hello\\"
        assertEquals("Hello", convertRtfToOsis(rtf))
    }

    @Test
    fun `bbli plain text passes through unchanged`() {
        // .bbli format has plain text, no RTF — should pass through via non-RTF check
        val text = "In the beginning God created the heaven and the earth."
        assertEquals(text, convertRtfToOsis(text))
    }

    @Test
    fun `star group is skipped`() {
        val rtf = "{\\*\\generator Riched20 10.0.19041}Hello"
        assertEquals("Hello", convertRtfToOsis(rtf))
    }

    @Test
    fun `multiple hex escapes in sequence`() {
        val rtf = "\\'c3\\'a9" // Ã followed by ©... actually UTF-8 bytes for é
        assertEquals("\u00c3\u00a9", convertRtfToOsis(rtf))
    }

    @Test
    fun `bold toggled on and off multiple times`() {
        val rtf = "\\b first\\b0  gap \\b second\\b0  end"
        assertEquals("<hi type=\"bold\">first</hi> gap <hi type=\"bold\">second</hi> end", convertRtfToOsis(rtf))
    }

    @Test
    fun `redundant bold on when already bold`() {
        val rtf = "\\b\\b text\\b0 "
        assertEquals("<hi type=\"bold\">text</hi>", convertRtfToOsis(rtf))
    }

    @Test
    fun `redundant bold off when not bold`() {
        val rtf = "\\b0 text"
        assertEquals("text", convertRtfToOsis(rtf))
    }

    // --- Book map tests ---

    @Test
    fun `canonical books 1-66 are all mapped`() {
        for (i in 1..66) {
            assertTrue("Book $i should be mapped", eSwordIntToBibleBook.containsKey(i))
        }
    }

    @Test
    fun `Genesis is book 1`() {
        assertEquals(BibleBook.GEN, eSwordIntToBibleBook[1])
    }

    @Test
    fun `Revelation is book 66`() {
        assertEquals(BibleBook.REV, eSwordIntToBibleBook[66])
    }

    @Test
    fun `apocrypha books are mapped`() {
        assertEquals(BibleBook.TOB, eSwordIntToBibleBook[67])
        assertEquals(BibleBook.JDT, eSwordIntToBibleBook[68])
        assertEquals(BibleBook.WIS, eSwordIntToBibleBook[69])
        assertEquals(BibleBook.SIR, eSwordIntToBibleBook[70])
        assertEquals(BibleBook.BAR, eSwordIntToBibleBook[71])
        assertEquals(BibleBook.MACC1, eSwordIntToBibleBook[72])
        assertEquals(BibleBook.MACC2, eSwordIntToBibleBook[73])
        assertEquals(BibleBook.ESD1, eSwordIntToBibleBook[74])
        assertEquals(BibleBook.ESD2, eSwordIntToBibleBook[75])
        assertEquals(BibleBook.PR_MAN, eSwordIntToBibleBook[78])
    }

    @Test
    fun `books 76 and 77 are not mapped`() {
        assertNull(eSwordIntToBibleBook[76])
        assertNull(eSwordIntToBibleBook[77])
    }

    @Test
    fun `reverse map contains all canonical books`() {
        assertEquals(1, bibleBookToESwordInt[BibleBook.GEN])
        assertEquals(66, bibleBookToESwordInt[BibleBook.REV])
        assertEquals(67, bibleBookToESwordInt[BibleBook.TOB])
        assertEquals(78, bibleBookToESwordInt[BibleBook.PR_MAN])
    }
}
