/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.format.osistohtml.osishandlers

import fi.sykero.kdom.Element
import fi.sykero.kdom.Text
import fi.sykero.kdom.query.descendantElements
import org.crosswire.common.util.KLocale
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import org.crosswire.common.util.ResourceBundles
import org.crosswire.jsword.passage.OsisParser
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.BibleNames
import org.crosswire.jsword.versification.system.Versifications

/** Expands link targets rather than trusting labels such as "v. 9". Invalid/non-Bible links keep their label. */
internal class SpeakReferenceText(private val locale: KLocale = LocaleProviderManager.getLocale()) {
    private val parser = OsisParser()

    /** Preserve the existing BVA ordinals: speak a containing link once, at its first anchor. */
    fun forAnchor(anchor: Element): String {
        val reference = generateSequence(anchor.parentElement) { it.parentElement }
            .firstOrNull { it.name == "reference" }
        val expanded = reference?.let { expand(it.getAttributeValue("osisRef")) }
        if (expanded != null) {
            return if (reference.descendantElements().firstOrNull { it.name == "BVA" } === anchor) expanded else ""
        }
        return text(anchor)
    }

    private fun text(element: Element): String {
        if (element.name == "reference") expand(element.getAttributeValue("osisRef"))?.let { return it }
        return element.content.joinToString("") {
            when (it) {
                is Text -> it.text
                is Element -> text(it)
                else -> ""
            }
        }
    }

    /** Drops a leading `Work:` prefix and any `!grain` / `@grain` suffix from one osisRef endpoint. */
    private fun stripWorkAndGrain(id: String): String =
        id.substringBefore('@').substringBefore('!').substringAfter(':')

    /**
     * Rewrites the book part of one endpoint to its canonical OSIS id. Modules write ids such as
     * `ps.89.39`, `Deu.18.15` or `Genesis.1.1`, which [OsisParser] (exact OSIS only) rejects.
     * Unknown books are left alone, so the reference still fails and keeps its label.
     */
    private fun canonicalBook(id: String): String {
        val book = id.substringBefore('.')
        if (BibleBook.fromExactOSIS(book) != null) return id
        val match = BibleBook.fromOSIS(book) ?: BibleNames.instance().getBook(book) ?: return id
        return match.osis + id.removePrefix(book)
    }

    fun expand(osisRef: String?): String? {
        if (osisRef.isNullOrBlank()) return null
        return try {
            val names = ResourceBundles.get("BibleNames", locale) ?: return null
            osisRef.trim().split(Regex("\\s+")).map { rawTarget ->
                val ids = rawTarget.split('-').map { canonicalBook(stripWorkAndGrain(it)) }
                val target = ids.joinToString("-")
                val range = parser.parseOsisRef(Versifications.instance().getVersification("KJVA"), target)
                    ?: return null
                fun endpoint(id: String, includeBook: Boolean): String {
                    val parts = id.split('.')
                    val book = if (includeBook) names.getString("${parts[0]}.Full") ?: return "" else ""
                    val location = parts.drop(1).joinToString(":")
                    return listOf(book, location).filter { it.isNotEmpty() }.joinToString(" ")
                }
                val start = endpoint(ids[0], true).takeIf { it.isNotEmpty() } ?: return null
                if (ids.size == 1) start
                else "$start-${endpoint(ids[1], range.start.book != range.end.book)}"
            }.joinToString("; ")
        } catch (_: Exception) {
            null
        }
    }
}
