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
package net.bible.android.control.search

import kotlin.test.Test
import kotlin.test.assertEquals

class RegexComplexityTest {
    private fun assertNested(vararg patterns: String) = patterns.forEach { assertEquals(true, hasNestedRepetition(it), "should be refused: $it") }
    private fun assertFine(vararg patterns: String) = patterns.forEach { assertEquals(false, hasNestedRepetition(it), "should be allowed: $it") }

    @Test fun refusesARepeatedGroupThatContainsARepetition() {
        assertNested(
            """(\w+\s?)+:""", "(.*a){20}", "(a*)*b", "(a*)*\\1b", "(a+)+", "(a+)*", "(a*)+", "((a+)b)+", "x(?:a|b+)*y", "(a{2,})+", "(.{1,50}x){10,}",
            "(a+)+?", "(a+)++", "([a-z]+\\s)+", "(?i)(foo.*)*",
        )
    }

    @Test fun allowsEverydayPatterns() {
        assertFine(
            "light: and there was light", "[Gg]od said", "said:", "\\bLord\\b.{0,40}\\bGod\\b", "(ab|cd)+", "(\\d{1,3}\\.){3}\\d{1,3}", "\\w+\\s\\w+\\s", ".*a.*b.*c", 
            "(a?)+", "(?:the|a) (?:man|woman)s?", "^([A-Z][a-z]+ )?said", ".*said.*", "a+b*c?", "(a+)b", "(a+)(b+)", "(a+)?", "(foo)*",
        )
    }

    @Test fun skipsEscapesCharacterClassesAndQuotedText() {
        assertFine("\\(a+\\)+", "[(a+)]+", "[+*]+", "[^)]+", "[]a+)]*", "\\Q(a+)+\\E", "\\Q(a+)+", "a\\", "\\\\(a+)")
        assertNested("[a]\\(((b+))+", "\\\\(a+)+", "\\Q(\\E(a+)+")
    }

    @Test fun readsBoundedQuantifiersByTheirUpperLimit() {
        assertFine("(a{1,9}){9}", "(a{2}){20}", "x{3,5}")
        assertNested("(a+){2}", "(a+){1,9}", "(\\w+\\s){2}", "(.*a){9}", "(.*,){5}x", "(a+){10}", "(a+){2,10}", "(a+){5,}", "(a{10}){10}")
        // Not a quantifier: kept as literal text, so nothing is repeated.
        assertFine("(a+){x}", "(a+){,5}", "(a+){1,2,3}", "(a+){")
    }

    @Test fun refusesManyOpenEndedRepetitions() {
        assertNested(".*.*.*.*.*.*.*!", "a+b+c+d+e+f+g+", "\\w*\\w*\\w*\\w*\\w*\\w*\\w*")
        assertFine(".*.*.*.*.*.*!", "[a-z]+ [a-z]+ [a-z]+")
    }

    @Test fun survivesMalformedPatterns() {
        assertFine("", "(", "((", "[", "[[", "{", "a)", ")))")
    }
}
