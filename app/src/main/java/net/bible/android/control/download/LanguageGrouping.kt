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
 * Groups [Language]s that refer to the same human language but were declared with different
 * code conventions, so that the document selection language spinner shows each language once.
 *
 * Document repositories are inconsistent about language codes. The same language turns up as:
 *  - two-letter ISO 639-1 codes (`en`, `fi`) and three-letter ISO 639-3 codes (`eng`, `fin`)
 *  - bare codes (`en`) and script-qualified codes (`en-Latn`, `he-Hebr`, `ru-Cyrl`)
 *  - codes with a country qualifier (`fi-FI`, `zh-Hans-CN`)
 *
 * JSword's [Language.equals] compares the raw code/script/country, so all of these are distinct
 * objects even though they render to the same name, and a plain `HashSet<Language>` leaves them
 * as duplicate entries in the spinner.
 *
 * The grouping [key] collapses these by:
 *  - normalising a three-letter language code to its two-letter equivalent,
 *  - dropping the country qualifier, and
 *  - keeping the script only when the same base language appears with more than one script in
 *    this dataset **and** the scripts actually produce different display names. That preserves
 *    genuinely distinct variants (e.g. `zh-Hans` 简体中文 vs `zh-Hant` 繁体中文) while merging
 *    redundant default-script qualifiers (`en-Latn` == `en`) and same-name script pairs that
 *    only differ by writing system (e.g. Gagauz in Latin vs Cyrillic, both "gagauzi").
 *
 * Because whether a script is significant depends on the whole dataset, this is a bound instance
 * built from the full set of languages rather than a context-free function.
 */
class LanguageGrouping(private val languages: Collection<Language>) {
    /** Distinct scripts seen per normalised base code; >1 means the script is significant. */
    private val scriptsByBaseCode: Map<String, Set<String>> = buildScriptIndex(languages)

    /**
     * A key that is identical for languages which should be shown as a single spinner entry.
     * Returns null for a null/codeless language.
     */
    fun key(language: Language?): String? {
        val code = language?.code ?: return null
        val base = normalizeCode(code)
        // The script only matters when the same base language is present with more than one
        // script; in that case different scripts may be genuinely different languages, so split
        // by display name (which differs for e.g. zh-Hans/zh-Hant but is identical for Gagauz
        // written in Latin vs Cyrillic, merging the latter into one entry).
        val hasMultipleScripts = (scriptsByBaseCode[base]?.size ?: 0) >= 2
        return if (hasMultipleScripts) "$base/${language.name}" else base
    }

    /** One representative [Language] per [key], preferring the most canonical form. */
    val representatives: List<Language> by lazy {
        languages
            .groupBy { key(it) }
            .map { (_, group) -> group.minWithOrNull(representativeComparator)!! }
    }

    companion object {
        /**
         * Map from ISO 639-2/T three-letter codes to their ISO 639-1 two-letter equivalent,
         * derived from the JDK's locale data (e.g. `eng -> en`, `fin -> fi`, `ben -> bn`).
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

        private fun buildScriptIndex(languages: Collection<Language>): Map<String, Set<String>> {
            val index = HashMap<String, MutableSet<String>>()
            for (language in languages) {
                val script = language.script ?: continue
                index.getOrPut(normalizeCode(language.code)) { HashSet() }.add(script)
            }
            return index
        }

        /**
         * Prefer the most "canonical" member of a group as the visible representative:
         * a two-letter code, then no script, then no country qualifier.
         */
        private val representativeComparator = compareBy<Language>(
            { it.code.length },
            { if (it.script == null) 0 else 1 },
            { if (it.country == null) 0 else 1 },
            { it.code },
            { it.script ?: "" },
            { it.country ?: "" },
        )
    }
}
