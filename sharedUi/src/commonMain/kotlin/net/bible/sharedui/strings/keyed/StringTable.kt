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

package net.bible.sharedui.strings.keyed

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * One locale's string resources as a set of lookups keyed by the Android resource names. The `Keyed*` classes are
 * written against this so they can be tested on the JVM; iOS builds it over the generated tables (`iosStringTable`).
 *
 * [text] answers the key itself for an unknown key (the generated tables' behaviour); [array] answers an empty list.
 * [raw] reads a raw text resource (the LLM system prompts); platforms without one leave the default, which fails
 * loudly instead of answering an empty prompt.
 */
class StringTable(
    val tag: String,
    val text: (key: String) -> String,
    val format: (key: String, args: Array<out Any?>) -> String,
    val plural: (name: String, count: Int) -> String,
    val array: (name: String) -> List<String>,
    val raw: (name: String) -> String = { error("Raw text resource '$it' is not available on this platform yet") },
) {
    /** [text] of [key] with its `%` placeholders filled from [args]. */
    fun fmt(key: String, vararg args: Any?): String = format(key, args)

    companion object {
        private const val STRING_REF = "@string/"

        /**
         * A table over lookups that return a template; [format] is [formatTemplate] of that template. The generated
         * array tables keep `@string/name` references as items (Android resolves them when it loads the array), so
         * [array] resolves them through [lookup].
         */
        fun of(
            tag: String,
            lookup: (key: String) -> String,
            plural: (name: String, count: Int) -> String,
            array: (name: String) -> List<String>,
            raw: (name: String) -> String = { error("Raw text resource '$it' is not available on this platform yet") },
        ): StringTable = StringTable(tag, lookup, { key, args -> formatTemplate(lookup(key), args) }, { n, c -> formatTemplate(plural(n, c), arrayOf(c)) },
            { name -> array(name).map { if (it.startsWith(STRING_REF)) lookup(it.removePrefix(STRING_REF)) else it } }, raw)
    }
}

/**
 * Fills a `java.util.Formatter`-style Android string template: `%s`, `%d`, `%f` with an optional precision (`%.1f`),
 * `%x`/`%X`, optional position (`%1$d`), zero-pad width (`%02X`), `%%` and `%n`. Unsequenced specs take the
 * arguments in order. An unrecognised spec is copied unchanged; a missing argument prints as empty.
 */
fun formatTemplate(template: String, args: Array<out Any?>): String {
    val out = StringBuilder(template.length + 16)
    var next = 0
    var i = 0
    while (i < template.length) {
        val c = template[i]
        if (c != '%' || i + 1 >= template.length) { out.append(c); i++; continue }
        if (template[i + 1] == '%') { out.append('%'); i += 2; continue }
        if (template[i + 1] == 'n') { out.append('\n'); i += 2; continue }
        var j = i + 1
        var position = -1
        val digitsEnd = run { var k = j; while (k < template.length && template[k].isDigit()) k++; k }
        if (digitsEnd > j && digitsEnd < template.length && template[digitsEnd] == '$') {
            position = template.substring(j, digitsEnd).toInt()
            j = digitsEnd + 1
        }
        val zeroPad = j < template.length && template[j] == '0'
        var width = 0
        while (j < template.length && template[j].isDigit()) { width = width * 10 + (template[j] - '0'); j++ }
        var precision = -1
        if (j < template.length && template[j] == '.') {
            j++; precision = 0
            while (j < template.length && template[j].isDigit()) { precision = precision * 10 + (template[j] - '0'); j++ }
        }
        val conv = template.getOrNull(j)
        if (conv == null || conv !in "sdfxX") { out.append('%'); i++; continue }
        val index = if (position >= 1) position - 1 else next++
        val arg = args.getOrNull(index)
        val body = when (conv) {
            's' -> arg?.toString() ?: ""
            'd' -> (arg as? Number)?.toLong()?.toString() ?: arg?.toString() ?: ""
            'f' -> (arg as? Number)?.let { fixed(it.toDouble(), if (precision < 0) 6 else precision) } ?: ""
            else -> ((arg as? Number)?.toLong() ?: 0L).toString(16).let { if (conv == 'X') it.uppercase() else it }
        }
        out.append(if (width > body.length) body.padStart(width, if (zeroPad) '0' else ' ') else body)
        i = j + 1
    }
    return out.toString()
}

/** [value] rounded half up to [decimals] places, without `String.format` (not available in common code). */
private fun fixed(value: Double, decimals: Int): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    val scale = 10.0.pow(decimals)
    val scaled = (abs(value) * scale).let { (it + 1e-9 * it.coerceAtLeast(1.0)) }.roundToLong()
    val whole = scaled / scale.toLong()
    val frac = scaled % scale.toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return if (decimals == 0) "$sign$whole" else "$sign$whole.${frac.toString().padStart(decimals, '0')}"
}
