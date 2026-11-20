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

/** More broad open-ended repetitions (`.*`, `\w+`, `[^,]*`) than this in one pattern are refused: `.*.*.*.*.*.*.*!` is polynomial of a high degree. */
private const val MAX_BROAD_OPEN_ENDED = 6

/** A repetition counts as "large" from this many repeats up (or unbounded): `{10,}`, `{3,50}`, `*`, `+`. */
private const val LARGE_REPEAT = 10

/** Why a pattern is refused before it runs; each reason has its own message. */
internal enum class RegexRisk { NESTED_REPETITION, TOO_MANY_WILDCARDS }

/** Whether [pattern] is refused for nested repetition (see [regexRisk]). */
internal fun hasNestedRepetition(pattern: String): Boolean = regexRisk(pattern) == RegexRisk.NESTED_REPETITION

/**
 * A cheap, conservative check for the textbook sources of catastrophic backtracking, or null when the pattern is
 * allowed. Android's ICU regex engine cannot be interrupted, so such a pattern would keep a thread at full CPU until
 * the process dies; it is rejected before it runs, on every platform (so that the behaviour does not depend on the engine).
 *
 * [RegexRisk.NESTED_REPETITION]:
 *  - a group that contains a large repetition and is itself repeated, e.g. `(\w+\s?)+:`, `(.*a){20}`, `(a*)*b`, `((a+)b)+`
 *    (`(\w+\s){2}` too, since the cost multiplies);
 *  - a repeated group (`*`, `+`, `{n,}`, `{n,m}` with m >= 2) with alternatives of which one is empty, optional (`a?`),
 *    broad (`.`, `\S`, `\W`, `\D`, a negated class) or matches the same character as another single-character
 *    alternative: `(.|\s)*q`, `(\S|\w)+x`, `(a?|b)+`, `(\w|\d)+x`;
 *  - a group holding an optional atom repeated a large counted number of times: `(a?){30}a{30}`.
 *
 * [RegexRisk.TOO_MANY_WILDCARDS]: more than 6 broad open-ended atoms (`.`, `\w`, `\S`, `\W`, `\D`, negated classes
 * under `*`, `+` or `{n,}`). `\s+` between words is not counted.
 *
 * Bounded small repetitions stay allowed (`(\d{1,3}\.){3}`), as do repeated groups with distinct alternatives
 * (`(said|spake)+`, `(ab|cd)+`) and `(a?)+`. Character classes, escapes and `\Q..\E` are skipped, so `[(+*]+` is fine.
 * This is not a proof: multi-character alternatives that overlap such as `(a|aa)+` still pass, and are left to the
 * time limit of the engines that can be interrupted.
 */
internal fun regexRisk(pattern: String): RegexRisk? {
    val groups = ArrayList<Group>()
    var broadOpenEnded = 0
    var atomBroad = false // the atom just read is broad, so an open-ended quantifier after it counts
    var i = 0
    while (i < pattern.length) {
        when (pattern[i]) {
            '\\' -> {
                if (pattern.startsWith("\\Q", i)) {
                    val end = pattern.indexOf("\\E", i + 2)
                    i = if (end < 0) pattern.length else end + 2
                    atomBroad = false
                } else {
                    atomBroad = pattern.startsWith("\\w", i) || pattern.startsWith("\\S", i) ||
                        pattern.startsWith("\\W", i) || pattern.startsWith("\\D", i)
                    i += 2
                }
            }
            '[' -> { atomBroad = pattern.startsWith("[^", i); i = endOfClass(pattern, i) }
            '(' -> {
                var j = i + 1
                if (j < pattern.length && pattern[j] == '?') {
                    val prefix = GROUP_PREFIX.matchAt(pattern, j)
                    j = if (prefix != null) j + prefix.value.length else {
                        j++
                        while (j < pattern.length && (pattern[j].isLetter() || pattern[j] == '-')) j++
                        j
                    }
                }
                groups += Group(j)
                atomBroad = false
                i = j
            }
            ')' -> {
                val g = if (groups.isEmpty()) Group(i) else groups.removeAt(groups.size - 1)
                val content = pattern.substring(minOf(g.contentStart, i), i)
                i++
                val q = quantifierAt(pattern, i)
                if (q != null) {
                    if (g.hasLarge && q.repeats) return RegexRisk.NESTED_REPETITION
                    if (q.repeats && alternationRisk(content)) return RegexRisk.NESTED_REPETITION
                    if (g.hasOptional && q.large && q.counted) return RegexRisk.NESTED_REPETITION
                    i = q.end
                }
                atomBroad = false
                groups.lastOrNull()?.let { parent ->
                    if (g.hasLarge || q?.large == true) parent.hasLarge = true
                    if (g.hasOptional || q?.optional == true) parent.hasOptional = true
                }
            }
            '*', '+', '{', '?' -> {
                val q = quantifierAt(pattern, i)
                if (q == null) { i++; atomBroad = false } else {
                    i = q.end
                    if (q.unbounded && atomBroad && ++broadOpenEnded > MAX_BROAD_OPEN_ENDED) return RegexRisk.TOO_MANY_WILDCARDS
                    atomBroad = false
                    groups.lastOrNull()?.let {
                        if (q.large) it.hasLarge = true
                        if (q.optional) it.hasOptional = true
                    }
                }
            }
            else -> { atomBroad = pattern[i] == '.'; i++ }
        }
    }
    return null
}

/** One open group: where its content starts, whether it holds a large repetition or an optional atom (at any depth). */
private class Group(val contentStart: Int, var hasLarge: Boolean = false, var hasOptional: Boolean = false)

private val GROUP_PREFIX = Regex("""\?(<=|<!|=|!|>|:|<[A-Za-z][A-Za-z0-9]*>|[a-zA-Z\-]*:)""")

/** Characters the overlap probe tries on single-character alternatives: printable ASCII, whitespace, a few non-ASCII ones. */
private val PROBES: List<String> = (' '..'~').map { it.toString() } + listOf("\t", "\n", "\u00e9", "\u00c4", "\u0663")

/** Whether the alternatives of a repeated group (its [content]) allow the same text to be split in many ways. */
private fun alternationRisk(content: String): Boolean {
    val branches = splitAlternatives(content)
    if (branches.size < 2) return false
    val atomMatches = ArrayList<Set<String>>()
    for (branch in branches) {
        if (branch.isEmpty()) return true
        val atom = singleAtom(branch) ?: continue
        if (atom.optional) return true
        if (atom.text == "." || atom.text in BROAD_ESCAPES || atom.text.startsWith("[^")) return true
        val regex = try { Regex(atom.text, RegexOption.IGNORE_CASE) } catch (e: IllegalArgumentException) { continue }
        atomMatches += PROBES.filterTo(HashSet()) { regex.matches(it) }
    }
    for (a in atomMatches.indices) for (b in a + 1 until atomMatches.size) {
        if (atomMatches[a].any { it in atomMatches[b] }) return true
    }
    return false
}

private val BROAD_ESCAPES = setOf("\\S", "\\W", "\\D")

/** A branch that is one atom ([text]) with at most one quantifier; [optional] when that quantifier allows zero repeats. */
private class SingleAtom(val text: String, val optional: Boolean)

private fun singleAtom(branch: String): SingleAtom? {
    val end = when (branch[0]) {
        '\\' -> if (branch.startsWith("\\Q") || branch.length < 2) return null else 2
        '[' -> endOfClass(branch, 0)
        '(' -> endOfGroup(branch, 0)
        else -> 1
    }
    if (end >= branch.length) return SingleAtom(branch, false)
    val q = quantifierAt(branch, end)
    if (q == null || q.end != branch.length) return null
    return SingleAtom(branch.substring(0, end), q.optional)
}

/** The index after the group that opens at [start]. */
private fun endOfGroup(p: String, start: Int): Int {
    var depth = 0
    var i = start
    while (i < p.length) {
        when (p[i]) {
            '\\' -> i++
            '[' -> { i = endOfClass(p, i); continue }
            '(' -> depth++
            ')' -> if (--depth == 0) return i + 1
        }
        i++
    }
    return p.length
}

/** Splits [content] at its top-level `|` (not inside a nested group or class, not escaped). */
private fun splitAlternatives(content: String): List<String> {
    val parts = ArrayList<String>()
    var depth = 0
    var from = 0
    var i = 0
    while (i < content.length) {
        when (content[i]) {
            '\\' -> i++
            '[' -> { i = endOfClass(content, i); continue }
            '(' -> depth++
            ')' -> depth--
            '|' -> if (depth == 0) { parts += content.substring(from, i); from = i + 1 }
        }
        i++
    }
    parts += content.substring(from)
    return parts
}

private class Quantifier(
    val large: Boolean, val unbounded: Boolean, val repeats: Boolean, val optional: Boolean, val counted: Boolean, val end: Int,
)

/** The quantifier starting at [start] (`*`, `+`, `?`, `{n}`, `{n,}`, `{n,m}`, with a lazy or possessive suffix), or null. */
private fun quantifierAt(p: String, start: Int): Quantifier? {
    if (start >= p.length) return null
    var i = start
    val large: Boolean
    val unbounded: Boolean
    val repeats: Boolean // can run more than once: an inner repetition under it multiplies
    val optional: Boolean // can run zero times, bounded: `?`, `{0,m}`
    val counted: Boolean // written with braces
    when (p[i]) {
        '*', '+' -> { large = true; unbounded = true; repeats = true; optional = false; counted = false; i++ }
        '?' -> { large = false; unbounded = false; repeats = false; optional = true; counted = false; i++ }
        '{' -> {
            val close = p.indexOf('}', i)
            if (close < 0) return null
            val body = p.substring(i + 1, close)
            val parts = body.split(',')
            if (parts.size > 2 || parts[0].isEmpty() || parts.any { it.isNotEmpty() && !it.all { c -> c in '0'..'9' } }) return null
            val max = if (parts.size == 1) parts[0].toLongOrNull() else parts[1].takeIf { it.isNotEmpty() }?.toLongOrNull()
            unbounded = parts.size == 2 && parts[1].isEmpty()
            large = unbounded || (max ?: Long.MAX_VALUE) >= LARGE_REPEAT
            repeats = unbounded || (max ?: Long.MAX_VALUE) >= 2
            optional = !unbounded && parts[0].toLongOrNull() == 0L
            counted = true
            i = close + 1
        }
        else -> return null
    }
    if (i < p.length && (p[i] == '?' || p[i] == '+')) i++
    return Quantifier(large, unbounded, repeats, optional, counted, i)
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
