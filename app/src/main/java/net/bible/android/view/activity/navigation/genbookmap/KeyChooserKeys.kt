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
package net.bible.android.view.activity.navigation.genbookmap

import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.Key

/*
 * The key lists behind the map and general-book choosers, in ONE place.
 *
 * Round 15b Task 9 gave both choosers a quick-sheet form in `ComposeReadingViewHost`, so each list
 * now has two callers: the full-screen activity in this package and the sheet. Lifted here rather
 * than copied because the EPUB branch below is exactly the kind of thing that drifts — the sheet
 * silently offering `cachedGlobalKeyList` where the screen offers the EPUB's table of contents would
 * be invisible to every test in the repo.
 *
 * **The contract these carry with them:** a `KeyRow.keyId` is the INDEX into the returned list, as a
 * decimal string. So a caller must resolve ONCE and hold the result for as long as its rows are on
 * screen — re-resolving between building the rows and handling the selection would silently
 * re-number them.
 */

/** The map chooser's keys. */
internal fun CurrentMapPage.keyChooserKeys(): List<Key> = cachedGlobalKeyList ?: emptyList()

/**
 * The general-book chooser's keys: an EPUB offers its table of contents, everything else the cached
 * global key list.
 *
 * The `as` casts are the activity's own, kept verbatim on the way here rather than softened to the
 * null-safe `Book.epubBackend` helper: an `isEpub` document that is not a `SwordGenBook` over an
 * `EpubBackend` would then fall back to the global key list instead of failing, which is a
 * behaviour change this extraction has no business making. A null `currentDocument` returns empty,
 * which is the one case the activity's `!!` would have crashed on and which now simply means "no
 * rows" — and, for the sheet, "do not open" (spec §4.6 / Task 9 amendment E3).
 */
internal fun CurrentGeneralBookPage.keyChooserKeys(): List<Key> {
    val doc = currentDocument ?: return emptyList()
    return if (doc.isEpub) {
        ((doc as SwordGenBook).backend as EpubBackend).tocKeys
    } else {
        cachedGlobalKeyList ?: emptyList()
    }
}
