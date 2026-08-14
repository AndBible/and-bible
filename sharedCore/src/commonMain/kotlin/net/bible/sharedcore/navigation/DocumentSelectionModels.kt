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
package net.bible.sharedcore.navigation

/** A language choice for the language filter. [groupingKey] collapses "eng"/"en"/"en-Latn" etc. */
data class LangOption(val code: String, val displayName: String, val groupingKey: String)

enum class DocCategory { BIBLE, COMMENTARY, DICTIONARY, GENERAL_BOOK, MAPS, AND_BIBLE, OTHER }

enum class DocInstallStatus {
    NOT_INSTALLED, INSTALLED, UPGRADE_AVAILABLE, BEING_INSTALLED, ERROR_DOWNLOADING, INSTALL_CANCELLED
}

/** The seven classic document-type spinner filters, as DocRow predicates. Order == classic spinner order. */
enum class DocTypeFilter(private val predicate: (DocRow) -> Boolean) {
    ALL({ it.category != DocCategory.AND_BIBLE }),          // classic slot 0: all non-addon
    BIBLE({ it.category == DocCategory.BIBLE }),
    COMMENTARY({ it.category == DocCategory.COMMENTARY }),
    DICTIONARY({ it.category == DocCategory.DICTIONARY }),
    GENERAL_BOOK({ it.category == DocCategory.GENERAL_BOOK }),
    MAPS({ it.category == DocCategory.MAPS }),
    ADDON({ it.category == DocCategory.AND_BIBLE });         // classic slot 6: AndBibleAddonFilter
    fun test(row: DocRow): Boolean = predicate(row)
}

/**
 * The document category whose icon represents this filter in the type picker, or null for
 * [DocTypeFilter.ALL] (which spans every category and therefore has no single icon).
 *
 * The `when` is exhaustive on purpose: adding an eighth filter must fail this build rather than
 * silently render a blank icon slot in the type sheet.
 */
val DocTypeFilter.iconCategory: DocCategory?
    get() = when (this) {
        DocTypeFilter.ALL -> null
        DocTypeFilter.BIBLE -> DocCategory.BIBLE
        DocTypeFilter.COMMENTARY -> DocCategory.COMMENTARY
        DocTypeFilter.DICTIONARY -> DocCategory.DICTIONARY
        DocTypeFilter.GENERAL_BOOK -> DocCategory.GENERAL_BOOK
        DocTypeFilter.MAPS -> DocCategory.MAPS
        DocTypeFilter.ADDON -> DocCategory.AND_BIBLE
    }

/**
 * Flattened, framework-free view of one JSword Book. The host owns the docId->Book map.
 * [docId] = Book.initials (command/result key). [osisId] = Book.osisID (search intersection only).
 */
data class DocRow(
    val docId: String,
    val osisId: String,
    val abbreviation: String,
    val name: String,
    val language: LangOption,
    val repository: String,
    val category: DocCategory,
    val installStatus: DocInstallStatus,
    val percentDone: Int,
    val recommended: Boolean,
    val badWarn: Boolean,
    val locked: Boolean,
    val enciphered: Boolean,
    val canDelete: Boolean,
    val installSizeMb: Double?,
)
