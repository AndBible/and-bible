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
package net.bible.android.control.download

import org.crosswire.common.util.Language
import java.util.Locale

/**
 * Collapses [Language]s that refer to the same human language but were declared with
 * different code standards into a single representative.
 *
 * Document repositories are inconsistent about language codes: CrossWire uses ISO 639-1
 * two-letter codes (`en`, `fi`), while eBible and IBT use ISO 639-3 three-letter codes
 * (`eng`, `fin`). JSword's [Language.equals] compares the raw code, so `Language("eng")`
 * and `Language("en")` are not equal even though both render as "English". When the
 * download/document language spinner deduplicates with a plain `HashSet<Language>`, these
 * survive as separate entries and the user sees the same language listed twice.
 *
 * The canonical key normalises the three-letter language part to its two-letter equivalent
 * and drops the country code, but **keeps the script** so that genuinely distinct variants
 * (e.g. `zh-Hans` vs `zh-Hant`) are still treated as different languages.
 */
object LanguageDeduplication {
    /**
     * Map from ISO 639-2/T three-letter codes to their ISO 639-1 two-letter equivalent,
     * derived from the JDK's locale data (e.g. `eng -> en`, `fin -> fi`, `deu -> de`).
     */
    private val iso3ToIso2: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { iso2 ->
            val iso3 = try {
                Locale(iso2).isO3Language
            } catch (e: Exception) {
                null
            }
            if (iso3.isNullOrEmpty()) null else iso3 to iso2
        }.toMap()
    }

    /** The two-letter form of a language code, or the original code if no mapping exists. */
    private fun normalizeCode(code: String): String =
        if (code.length == 3) iso3ToIso2[code] ?: code else code

    /**
     * A key that is identical for languages differing only by code standard (2- vs 3-letter)
     * or by country, but distinct for languages with a different script.
     */
    fun canonicalKey(language: Language?): String? {
        val code = language?.code ?: return null
        val normalized = normalizeCode(code)
        val script = language.script
        return if (script != null) "$normalized-$script" else normalized
    }

    /**
     * Prefer the most "canonical" member of a group as the visible representative:
     * a two-letter code over a three-letter one, and one without a country qualifier.
     */
    private val representativeComparator = compareBy<Language>(
        { it.code.length },
        { if (it.country == null) 0 else 1 },
        { it.code },
        { it.country ?: "" },
    )

    /**
     * Remove duplicate languages, keeping one representative per [canonicalKey].
     * Languages with no resolvable code are kept as-is.
     */
    fun deduplicate(languages: Collection<Language>): List<Language> =
        languages
            .groupBy { canonicalKey(it) }
            .map { (_, group) -> group.minWithOrNull(representativeComparator)!! }
}
