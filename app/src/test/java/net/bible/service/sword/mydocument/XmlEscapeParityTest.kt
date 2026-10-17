/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.sword.mydocument

import org.apache.commons.text.StringEscapeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/** [escapeXml11] must stay byte-identical to commons-text, which the stored/rendered MyDocument HTML used. */
class XmlEscapeParityTest {
    private fun assertParity(s: String) = assertEquals(StringEscapeUtils.escapeXml11(s), escapeXml11(s))

    @Test fun basicMarkupAndEmoji() {
        assertParity("<html>a & b</html>")
        assertParity("\"quoted\" and 'single' > <")
        assertParity("emoji 😀 pair, 👍🏽 and ääkköset אב")
        assertParity("")
    }

    @Test fun everyBmpCharacterAlone() {
        for (code in 0..0xFFFF) assertParity(code.toChar().toString())
    }

    @Test fun controlCharacterClassesInContext() {
        assertParity("a\u0000b\u0001c\u0008d\u0009e\u000Af\u000Bg\u000Ch\u000Di\u000Ej\u001Fk\u007Fl\u0084m\u0085n\u0086o\u009Fp")
        assertParity("x\uFFFEy\uFFFFz")
    }

    @Test fun surrogates() {
        assertParity("\uD83D")
        assertParity("\uDE00")
        assertParity("\uD83Dabc")
        assertParity("abc\uD83D")
        assertParity("\uDE00\uD83D")
        assertParity("\uD83D😀")
        assertParity("😀\uDE00")
    }

    @Test fun allSupplementaryPlanesSampled() {
        for (cp in 0x10000..0x10FFFF step 0x111) assertParity(String(Character.toChars(cp)))
    }
}
