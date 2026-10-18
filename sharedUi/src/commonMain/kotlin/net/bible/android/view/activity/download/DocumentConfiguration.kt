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
import net.bible.android.control.download.repo
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData

/**
 * The document-configuration model and the book predicates the Download destination reads. Split out of
 * `DocumentBadges.kt` (`:app`, which keeps the Android-resource icon mapping) so the download host can live in
 * `:sharedUi`; the package is unchanged, so every existing import still resolves.
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

val Book.installedDocument get() = Books.installed().getBook(initials)

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
