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

import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.control.download.repo
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
 * consumed by `nav/NavHostComposeActivity.kt` (the Download destination's host, successor to the
 * classic `DownloadComposeActivity` deleted in nav-graph slice 4 Task 9) via explicit imports, and
 * by the surviving `base/DocumentSelectionBase`.
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
 * The custom-repository help URL no longer has a copy here: `:app` depends on `:sharedUi`, so
 * everything reads [net.bible.sharedui.download.customRepositoriesHelpUrl].
 *
 * The package is deliberately unchanged (`net.bible.android.view.activity.download`): classic
 * `DownloadComposeActivity` used to read four of these by bare name with no import, because it sat
 * in this package, before nav-graph slice 4 Task 9 deleted it in favour of
 * `nav/NavHostComposeActivity.kt`'s explicit imports. Moving the file to a "tidier" package is
 * still a needless churn, not a cleanup.
 *
 * [DocumentConfiguration], [PseudoBook] and [Book.installedDocument] were promoted here from
 * `base/DocumentSelectionBase.kt` by nav-graph slice 8 F7, which deleted that abstract class (zero
 * subclasses): these three top-level declarations outlive it, consumed by
 * `nav/NavHostComposeActivity.kt`, `navigation/DocRowMapper.kt` and
 * `service/download/FakeBookFactory.kt`.
 */
@Serializable
data class DocumentConfiguration(
    val bibles: Map<String, List<String>>,
    val commentaries: Map<String, List<String>>,
    val dictionaries: Map<String, List<String>>,
    val books: Map<String, List<String>>,
    val maps: Map<String, List<String>>,
    val addons: Map<String, List<String>> = emptyMap(),
) {
    fun getForBookCategory(c: BookCategory): Map<String, List<String>> {
        return when(c) {
            BookCategory.BIBLE -> bibles
            BookCategory.COMMENTARY -> commentaries
            BookCategory.GENERAL_BOOK -> books
            BookCategory.MAPS -> maps
            BookCategory.DICTIONARY -> dictionaries
            BookCategory.AND_BIBLE -> addons
            else -> emptyMap()
        }
    }
}

@Serializable
data class PseudoBook(
    val id: String,
    val suggested: String,
)

val Book.installedDocument get() = Books.installed().getBook(initials)

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
