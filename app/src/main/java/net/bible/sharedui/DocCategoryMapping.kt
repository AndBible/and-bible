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
package net.bible.sharedui

import net.bible.sharedcore.navigation.DocCategory
import org.crosswire.jsword.book.BookCategory

/**
 * The single `BookCategory → DocCategory` mapping for the whole app.
 *
 * Was copy-pasted in `ChooseDocumentComposeActivity`, `DownloadComposeActivity` and
 * `CloudDocumentsComposeActivity`; the quick-doc picker (F45) would have made a fourth copy, so it
 * moved here instead — next to [categoryDrawableRes], which consumes the result.
 *
 * [category] is intentionally non-null: [net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem.category]
 * is the only nullable source, and its `null` is meaningful there (unknown/never-installed
 * category) and distinct from [BookCategory.OTHER] — so that call site maps null→null itself
 * (`category?.let { docCategoryOf(it) }`) instead of this function silently collapsing both into
 * one value.
 */
fun docCategoryOf(category: BookCategory): DocCategory = when (category) {
    BookCategory.BIBLE -> DocCategory.BIBLE
    BookCategory.COMMENTARY -> DocCategory.COMMENTARY
    BookCategory.DICTIONARY -> DocCategory.DICTIONARY
    BookCategory.MAPS -> DocCategory.MAPS
    BookCategory.GENERAL_BOOK -> DocCategory.GENERAL_BOOK
    BookCategory.AND_BIBLE -> DocCategory.AND_BIBLE
    // DAILY_DEVOTIONS, GLOSSARY, QUESTIONABLE, ESSAYS, IMAGES have no DocCategory of their own —
    // genuinely unmapped, not a null-substitute.
    else -> DocCategory.OTHER
}
