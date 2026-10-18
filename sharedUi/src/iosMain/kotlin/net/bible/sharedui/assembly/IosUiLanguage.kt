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
package net.bible.sharedui.assembly

/** Picks the UI language on iOS (spec C4). Pure logic so it is testable without a device. */
object IosUiLanguage {
    private const val FALLBACK = "en"

    /**
     * The `locale_pref` setting if set (blank counts as unset) and supported, else the first of the iOS
     * [preferred] languages that [supported] has, else English. A tag may match through a shorter form: `pt-BR`
     * matches `pt`, `sr-Latn-RS` matches `sr-Latn` and then `sr`; underscores are read as dashes. The answer is
     * the supported tag that matched, so it is always one `iosStrings` can serve.
     */
    fun resolve(localePref: String?, preferred: List<String>, supported: Set<String>): String {
        val candidates = listOfNotNull(localePref?.takeIf { it.isNotBlank() }) + preferred
        for (candidate in candidates) {
            var tag = candidate.trim().replace('_', '-')
            while (tag.isNotEmpty()) {
                if (tag in supported) return tag
                val dash = tag.lastIndexOf('-')
                if (dash <= 0) break
                tag = tag.substring(0, dash)
            }
        }
        return FALLBACK
    }
}
