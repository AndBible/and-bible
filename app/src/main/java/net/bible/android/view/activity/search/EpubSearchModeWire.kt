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
 * The classic settings key the EPUB search word-mode is stored under. Two surfaces read or write it:
 * the reading-view host and `EpubSearchComposeActivity`. (`EpubSearchResultsComposeActivity` uses the
 * wire FORMAT below but never this key — it receives the mode as a `"searchType"` intent extra.) Kept
 * beside the format itself because the key and the format are one contract: a surface that gets
 * either half wrong reads back a mode another surface never set.
 *
 * Classic `EpubSearch.kt` deliberately still spells the same key literally: it is on the deletion
 * path, and importing an `:app` Compose-era const into it would only make that deletion noisier.
 */
const val EPUB_SEARCH_TYPE_KEY = "epubSearch-SearchType"

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

/**
 * Removes the LUCENE decoration [net.bible.android.control.search.SearchControl.decorateSearchString]
 * wrapped around a text-selection seed, so the raw selected text can be handed to the FTS5 engine.
 *
 * The one entry point that needs this is the reading view's "Search '…'" text-selection action
 * (`BibleView.kt`), which decorates its selection as a Lucene PHRASE — `" \"selected text\""` —
 * before handing it to the host. That string is correct for the SWORD path (which re-decorates with
 * identity decorators) and WRONG for an EPUB: `adjustSearchText(PHRASE, ...)` would quote it a second
 * time (`""selected text""` — an FTS5 syntax error), and the word modes would read the whole quoted
 * blob as a single phrase.
 *
 * Strips CONSERVATIVELY, and that is the point: trim, then remove exactly ONE layer of surrounding
 * double quotes if both are present. A selection that itself contains quotes keeps them, and an
 * undecorated string is returned unchanged — so this can never corrupt a query it was not meant for.
 */
fun stripSearchDecoration(query: String): String {
    val trimmed = query.trim()
    val quoted = trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"'
    return if (quoted) trimmed.substring(1, trimmed.length - 1) else trimmed
}

/** The query text and word-mode one EPUB search run should use — see [epubSearchRunFor]. */
data class EpubSearchRunParams(val query: String, val mode: EpubSearchMode)

/**
 * The parameters for a single EPUB search run.
 *
 * An ordinary run uses the query as typed and [storedMode], the mode the user chose in the settings
 * sheet. A [preDecorated] run — only ever the text-selection "Search '…'" entry point — instead
 * strips the Lucene decoration off the seed ([stripSearchDecoration]) and forces
 * [EpubSearchMode.PHRASE], because a selection IS a phrase: the user asked to find that exact run of
 * text in the book being read.
 *
 * The forcing is ONE-SHOT by construction — it is returned, never stored — so a selection search does
 * not overwrite the mode the user picked and will see again next time the settings sheet opens.
 */
fun epubSearchRunFor(query: String, preDecorated: Boolean, storedMode: EpubSearchMode): EpubSearchRunParams =
    if (preDecorated) EpubSearchRunParams(stripSearchDecoration(query), EpubSearchMode.PHRASE)
    else EpubSearchRunParams(query, storedMode)
