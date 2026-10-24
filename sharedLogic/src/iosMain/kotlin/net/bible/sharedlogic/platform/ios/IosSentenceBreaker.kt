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
package net.bible.sharedlogic.platform.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import net.bible.sharedlogic.platform.SentenceBreaker
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.NSStringEnumerationBySentences
import platform.Foundation.NSStringEnumerationSubstringNotRequired
import platform.Foundation.enumerateSubstringsInRange

/**
 * [SentenceBreaker] on `NSString.enumerateSubstringsInRange(.., NSStringEnumerationBySentences)` with
 * `BreakIterator` semantics: boundaries are the starts of the sentences plus the text length; `-1` replaces `DONE`
 * and an offset outside the text is an [IllegalArgumentException]. Offsets are UTF-16 units on both sides.
 */
object IosSentenceBreaker : SentenceBreaker {
    @OptIn(ExperimentalForeignApi::class)
    private fun boundaries(text: String): List<Int> {
        val result = mutableSetOf<Int>()
        result.add(0)
        result.add(text.length)
        @Suppress("CAST_NEVER_SUCCEEDS")
        (text as NSString).enumerateSubstringsInRange(
            NSMakeRange(0uL, text.length.toULong()),
            NSStringEnumerationBySentences or NSStringEnumerationSubstringNotRequired,
        ) { _, _, enclosing, _ ->
            // NSRange.location is NSUInteger = ULong in Kotlin/Native, never an Int.
            val start = enclosing.useContents { location.toLong().toInt() }
            result.add(start)
        }
        return result.toList()
    }

    private fun check(text: String, offset: Int) {
        require(offset in 0..text.length) { "offset out of bounds: $offset" }
    }

    override fun preceding(text: String, offset: Int): Int {
        check(text, offset)
        return boundaries(text).sorted().lastOrNull { it < offset } ?: -1
    }

    override fun following(text: String, offset: Int): Int {
        check(text, offset)
        return boundaries(text).sorted().firstOrNull { it > offset } ?: -1
    }
}
