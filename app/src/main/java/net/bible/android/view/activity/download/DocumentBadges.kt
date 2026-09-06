/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.download

import net.bible.android.activity.R
import net.bible.android.control.download.repo
import net.bible.android.view.activity.base.DocumentConfiguration
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData

/**
 * Document badge/icon predicates and the per-document icon mapping, extracted from
 * `DocumentListItem.kt` and `DownloadActivity.kt` when slice S6 deleted those classic screens.
 *
 * These are top-level declarations, not members, and they outlive the classic Activities that
 * happened to host them: [BookCategory.imageResource] and [Book.imageResource] are consumed by
 * `page/MainBibleActivity.kt` alone (`:1638`, the doc-type icon on the Compose restore rail) — the
 * Z-late epilogue deleted their only other reader, `view/util/widget/WindowButtonWidget.kt`;
 * [Book.isRecommended], [BadDocumentAction], [Book.isBadDocument] and [Book.isInstalled] are
 * consumed by the SURVIVING `DownloadComposeActivity` and by the surviving
 * `base/DocumentSelectionBase`.
 *
 * That deleted reader used to be cited here as unrewritable because spec 2.4 protects
 * `view/util/widget/` wholesale. **Do not carry that reading forward as a precedent.** The Z-late
 * epilogue deleted four files out of that very directory — `WindowButtonWidget` (with
 * `AddNewWindowButtonWidget` inside it) and `TwoLineListItem` under decision D2, `SpeakTransportWidget`
 * and `AgentLogWidget` under D1 — each a named, user-approved exception rather than a general rule.
 * §2.4 itself still stands as the default, and still keeps files nothing references at all:
 * `BookmarkListItem.kt` and `BookmarkStyleAdapterHelper.kt` in the same directory are referenceless
 * since S9 and are deliberately retained (`ClassicBookmarkRemovalGuardTest.survivingCollaborators`).
 * So "referenceless and in `view/util/widget/`" does not license a deletion; a named decision does.
 *
 * [customRepositoriesHelpUrl] is also kept here rather than deleted: it is pinned against the
 * `:sharedUi` copy ([net.bible.sharedui.download.customRepositoriesHelpUrl]) by
 * `CustomRepositoryHelpUrlTest` to catch the two copies drifting apart.
 *
 * The package is deliberately unchanged (`net.bible.android.view.activity.download`):
 * `DownloadComposeActivity` reads four of these by bare name with NO import, because it sits in
 * this package. Moving the file to a "tidier" package is a compile break, not a cleanup.
 */
val BookCategory.imageResource: Int
    get() = when(this) {
        BookCategory.BIBLE -> if(CommonUtils.isDiscrete) R.drawable.ic_baseline_menu_book_24 else  R.drawable.ic_bible_24dp
        BookCategory.COMMENTARY -> R.drawable.ic_commentary
        BookCategory.DICTIONARY -> R.drawable.ic_dictionary_24dp
        BookCategory.MAPS -> R.drawable.ic_map_black_24dp
        BookCategory.GENERAL_BOOK -> R.drawable.ic_book_24dp
        BookCategory.AND_BIBLE -> R.drawable.ic_addon_24dp
        else -> R.drawable.ic_book_24dp
    }

val Book.imageResource: Int
    get() = bookCategory.imageResource

fun Book.isRecommended(recommendedDocuments: DocumentConfiguration?): Boolean =
    recommendedDocuments?.getForBookCategory(bookCategory)?.get(language.code)?.find {
        if(it.contains("::")) {
            val (initials, repository) = it.split("::")
            initials == this.initials && repository == this.repo
        } else {
            it == initials
        }
    } != null

enum class BadDocumentAction {
    WARN, HIDE, NONE;
    companion object {
        fun getByLetter(actionLetter: String) =
            when (actionLetter) {
                "W" -> WARN
                "H" -> HIDE
                else -> NONE
            }
    }
}
fun Book.isBadDocument(badDocuments: DocumentConfiguration?, actionForDocument: BadDocumentAction): Boolean =
    badDocuments?.getForBookCategory(bookCategory)?.get(language.code)?.find {
        val (initials, repository, version, actionStr) = it.split("::")
        val action = BadDocumentAction.getByLetter(actionStr)
        initials == this.initials
            && repository == this.repo
            && version == bookMetaData.getProperty(SwordBookMetaData.KEY_VERSION)
            && action == actionForDocument
    } != null

val Book.isInstalled: Boolean get() = Books.installed().getBook(initials) != null

const val customRepositoriesHelpUrl = "https://docs.andbible.org/en/latest/custom_repositories.html"
