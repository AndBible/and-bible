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
package net.bible.android.view.activity.search

import net.bible.sharedcore.search.EpubSearchMode
import org.crosswire.jsword.index.search.SearchType

/**
 * The single classic-settings wire format for the EPUB search word-mode, in BOTH directions.
 * Shared by [net.bible.android.view.activity.page.screen.ComposeReadingViewHost] (the reading-view
 * host's own EPUB search, F43 Task 4) and the two standalone EPUB search Activities
 * ([EpubSearchComposeActivity], [EpubSearchResultsComposeActivity]) — all three must read and write
 * the EXACT SAME format, or a mode set on one surface silently reads back wrong on another.
 *
 * The format is classic's: the JSword [SearchType] name for a word-mode, or an ABSENT/null value for
 * FTS (classic wrote `searchType?.name`) — never [EpubSearchMode]'s own names, which do not match
 * (`ANY_WORD` vs [SearchType.ANY_WORDS]).
 *
 * Kept in `:app`, not `:sharedCore`: `:sharedCore` has no JSword ([SearchType]) on its classpath.
 */
fun EpubSearchMode.toClassicSearchTypeName(): String? = when (this) {
    EpubSearchMode.ALL_WORDS -> SearchType.ALL_WORDS.name
    EpubSearchMode.ANY_WORD -> SearchType.ANY_WORDS.name
    EpubSearchMode.PHRASE -> SearchType.PHRASE.name
    EpubSearchMode.FTS -> null
}

/**
 * The read half of [toClassicSearchTypeName]: absent or unknown → FTS (classic's `ftsQuery` radio),
 * everything else via the real 7-constant JSword [SearchType] — NOT [net.bible.sharedcore.search.SearchType],
 * the unrelated 3-constant SWORD word-mode enum some callers already have in scope, which happens to
 * share three names with this one and would silently parse the wrong thing.
 */
fun epubSearchModeFromClassicName(name: String?): EpubSearchMode {
    if (name == null) return EpubSearchMode.FTS
    val jsword = try {
        SearchType.valueOf(name)
    } catch (e: IllegalArgumentException) {
        return EpubSearchMode.FTS
    }
    return when (jsword) {
        SearchType.ALL_WORDS -> EpubSearchMode.ALL_WORDS
        SearchType.ANY_WORDS -> EpubSearchMode.ANY_WORD
        SearchType.PHRASE -> EpubSearchMode.PHRASE
        else -> EpubSearchMode.FTS
    }
}
