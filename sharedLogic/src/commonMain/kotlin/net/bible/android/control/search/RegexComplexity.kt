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

/** A repetition counts as "large" from this many repeats up (or unbounded): `{10,}`, `{3,50}`, `*`, `+`. */
private const val LARGE_REPEAT = 10

/**
 * A cheap, conservative check for the textbook source of catastrophic backtracking: a group that contains a large
 * repetition and is itself repeated a large number of times, e.g. `(\w+\s?)+:`, `(.*a){20}`, `(a*)*b`, `((a+)b)+`.
 * Android's ICU regex engine cannot be interrupted, so such a pattern would keep a thread at full CPU until the process
 * dies; it is rejected before it runs, on every platform (so that the behaviour does not depend on the engine).
 *
 * Bounded repetitions of small size stay allowed (`(\d{1,3}\.){3}`, `(\w+\s){2}`), as does a repeated group without an
 * inner repetition (`(ab|cd)+`). Character classes, escapes and `\Q..\E` are skipped, so `[(+*]+` is fine. This is not
 * a proof: overlapping alternatives such as `(a|aa)+` still pass, and are left to the time limit of the engines that
 * can be interrupted.
 */
internal fun hasNestedRepetition(pattern: String): Boolean {
    // One flag per open group: does it contain a large repetition, at any depth? (Outside every group nothing can nest.)
    val groups = ArrayList<Boolean>()
    var i = 0
    while (i < pattern.length) {
        when (pattern[i]) {
            '\\' -> {
                if (pattern.startsWith("\\Q", i)) {
                    val end = pattern.indexOf("\\E", i + 2)
                    i = if (end < 0) pattern.length else end + 2
                } else i += 2
            }
            '[' -> i = endOfClass(pattern, i)
            '(' -> { groups += false; i++ }
            ')' -> {
                i++
                val inner = if (groups.isEmpty()) false else groups.removeAt(groups.size - 1)
                val q = quantifierAt(pattern, i)
                val large = q != null && q.large
                if (inner && large) return true
                if (q != null) i = q.end
                if (groups.isNotEmpty() && (inner || large)) groups[groups.size - 1] = true
            }
            '*', '+', '{' -> {
                val q = quantifierAt(pattern, i)
                if (q == null) i++ else {
                    i = q.end
                    if (q.large && groups.isNotEmpty()) groups[groups.size - 1] = true
                }
            }
            else -> i++
        }
    }
    return false
}

private class Quantifier(val large: Boolean, val end: Int)

/** The quantifier starting at [start] (`*`, `+`, `?`, `{n}`, `{n,}`, `{n,m}`, with a lazy or possessive suffix), or null. */
private fun quantifierAt(p: String, start: Int): Quantifier? {
    if (start >= p.length) return null
    var i = start
    val large: Boolean
    when (p[i]) {
        '*', '+' -> { large = true; i++ }
        '?' -> { large = false; i++ }
        '{' -> {
            val close = p.indexOf('}', i)
            if (close < 0) return null
            val body = p.substring(i + 1, close)
            val parts = body.split(',')
            if (parts.size > 2 || parts[0].isEmpty() || parts.any { it.isNotEmpty() && !it.all { c -> c in '0'..'9' } }) return null
            val max = if (parts.size == 1) parts[0].toLongOrNull() else parts[1].takeIf { it.isNotEmpty() }?.toLongOrNull()
            large = parts.size == 2 && parts[1].isEmpty() || (max ?: Long.MAX_VALUE) >= LARGE_REPEAT
            i = close + 1
        }
        else -> return null
    }
    if (i < p.length && (p[i] == '?' || p[i] == '+')) i++
    return Quantifier(large, i)
}

/** The index after the character class that opens at [start] (nested classes, escapes and a leading `]` handled). */
private fun endOfClass(p: String, start: Int): Int {
    var depth = 0
    var i = start
    while (i < p.length) {
        when (p[i]) {
            '\\' -> i++
            '[' -> {
                depth++
                if (p.startsWith("[^", i)) i++
                if (i + 1 < p.length && p[i + 1] == ']') i++
            }
            ']' -> if (--depth == 0) return i + 1
        }
        i++
    }
    return p.length
}
