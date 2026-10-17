/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedlogic.util

import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UrlDecodingParityTest {
    @Test fun matchesJdkDecoder() {
        for (s in listOf("a+b", "%20", "%C3%A4iti", "%E2%82%AC", "%FF%FE", "plain", "", "David%2BDOBYA", "%d7%90", "100%25")) {
            assertEquals(URLDecoder.decode(s, "UTF-8"), decodeUrl(s), "input: $s")
        }
    }

    @Test fun malformedPercentThrowsLikeJdk() {
        for (s in listOf("%", "%2", "%zz")) {
            assertFailsWith<IllegalArgumentException> { URLDecoder.decode(s, "UTF-8") }
            assertFailsWith<IllegalArgumentException> { decodeUrl(s) }
        }
    }
}
