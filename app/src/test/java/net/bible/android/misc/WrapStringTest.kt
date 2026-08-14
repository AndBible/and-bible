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

package net.bible.android.misc

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`is`
import org.junit.Test

/**
 * [wrapString] embeds arbitrary document content (Bible text, EPUB and MyDocument HTML, notes,
 * document names) into a JavaScript template literal that is then evaluated in the WebView.
 * Anything the content can do to that literal - substitutions, escape sequences, closing the
 * literal early - breaks the whole injected script, so every escapable character must be escaped.
 */
class WrapStringTest {
    /** Decodes the escapes that a JS template literal would resolve when the script is evaluated. */
    private fun evaluateAsTemplateLiteral(wrapped: String): String {
        assertThat("wrapped in backticks", wrapped.first() == '`' && wrapped.last() == '`', `is`(true))
        val body = wrapped.substring(1, wrapped.length - 1)
        assertThat("no unescaped substitution", unescapedSubstitution.containsMatchIn(body), `is`(false))
        assertThat("no unescaped backtick", unescapedBacktick.containsMatchIn(body), `is`(false))

        val result = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                result.append(body[i + 1])
                i += 2
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }

    private fun assertRoundTrips(content: String) =
        assertThat(evaluateAsTemplateLiteral(wrapString(content)), equalTo(content))

    @Test
    fun nullBecomesJsNull() = assertThat(wrapString(null), equalTo("null"))

    @Test
    fun plainTextRoundTrips() = assertRoundTrips("In the beginning God created the heaven and the earth.")

    // Ticket 3391: a markdown page containing ${date} was evaluated as a template substitution,
    // which threw ReferenceError and aborted the whole document-loading script.
    @Test
    fun templateSubstitutionRoundTrips() = assertRoundTrips("Deadline \${date} passed")

    @Test
    fun bareDollarRoundTrips() = assertRoundTrips("Price is \$5")

    @Test
    fun backtickRoundTrips() = assertRoundTrips("use `code` here")

    @Test
    fun backslashEscapeSequenceRoundTrips() = assertRoundTrips("""C:\temp\new\unicode""")

    @Test
    fun trailingBackslashDoesNotEscapeClosingBacktick() = assertRoundTrips("""ends with backslash \""")

    @Test
    fun combinedSpecialCharactersRoundTrip() = assertRoundTrips("""`\${'$'}{date}\` mixed""")

    companion object {
        private val unescapedSubstitution = Regex("""(?<!\\)(?:\\\\)*\$\{""")
        private val unescapedBacktick = Regex("""(?<!\\)(?:\\\\)*`""")
    }
}
