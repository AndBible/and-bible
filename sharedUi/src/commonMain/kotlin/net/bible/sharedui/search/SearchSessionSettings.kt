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
package net.bible.sharedui.search

/**
 * Settings key of the recent search terms, shared by the search screen and the in-reading search:
 * **newline**-separated, since a query may contain a comma. (The translation selection's key is
 * `SearchControl.SEARCH_TRANSLATIONS_PREF`.)
 */
const val SEARCH_RECENT_TERMS_KEY = "search_recent_terms"

/**
 * The translations a search session starts with, shared by the search screen and the in-reading search:
 * the saved multi-selection belongs to Bible searches only, so it applies when the current document
 * [isBible]; a commentary (or any other) search targets just that document and leaves the saved Bible
 * selection alone. Saved initials no longer in [installedBibles] are dropped; when nothing usable is left,
 * the current document ([currentInitials]) is the selection.
 *
 * [saved] is the value stored under `SearchControl.SEARCH_TRANSLATIONS_PREF`.
 */
fun seedSearchTranslations(
    currentInitials: String?,
    isBible: Boolean,
    saved: String?,
    installedBibles: Set<String>,
): List<String> {
    val fallback = listOfNotNull(currentInitials)
    if (!isBible) return fallback
    return parseSavedSearchTranslations(saved, installedBibles).ifEmpty { fallback }
}

/** The comma-joined saved selection, restricted to [installedBibles] initials; empty when none is saved. */
fun parseSavedSearchTranslations(saved: String?, installedBibles: Set<String>): List<String> {
    if (saved.isNullOrBlank()) return emptyList()
    return saved.split(",").filter { it in installedBibles }
}
