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

import net.bible.android.control.page.OrdinalRange
import net.bible.service.sword.BookAndKey
import org.crosswire.jsword.book.Book

/**
 * Resolves an EPUB search-result row's key for navigation, with the hit's [ordinal] re-attached as an
 * [OrdinalRange] — classic hands `setCurrentDocumentAndKey` the whole `BookAndKey` with its
 * `OrdinalRange` included (`EpubSearchResults.kt:171-178`, built from the search hit's own ordinal at
 * `EpubBackendState.kt:323`), so the reader lands on the hit rather than the top of the fragment. The
 * Compose result rows carry only the fragment's `osisRef` plus the ordinal separately (`EpubResultRow`),
 * so the two are re-joined here.
 *
 * [keyId] is the row's `BookAndKey.osisRef` (`"<docId>:<fragmentId>"`); the [docId] prefix is stripped
 * before re-resolving the inner osisRef via [Book.getKey].
 *
 * Shared by both Compose EPUB-result surfaces
 * ([net.bible.android.view.activity.page.screen.ComposeReadingViewHost] and
 * [net.bible.android.view.activity.search.EpubSearchResultsComposeActivity]) so the ordinal
 * reattachment is proved once, not copied — a copy is exactly the kind of drift this port has shipped
 * before (see the message-key positional drift note in `CLAUDE.md`).
 */
fun epubKeyFor(book: Book, docId: String, keyId: String, ordinal: Int): BookAndKey {
    val innerOsisRef = keyId.removePrefix("$docId:")
    return BookAndKey(book.getKey(innerOsisRef), book, OrdinalRange(ordinal, null))
}
