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
 * [DocTypeFilter.ALL] (which spans every non-addon category and therefore has no single icon).
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
) : DocSortable {
    // The row renders "<abbreviation> <name>", so sorting by "name" must lead with the
    // abbreviation or the visible order would not match the chosen criterion.
    override val sortName: String get() = abbreviation
    override val sortSecondaryName: String get() = name
    override val sortCategory: DocCategory? get() = category
    override val sortLanguage: String? get() = language.displayName
    override val sortRepository: String? get() = repository.ifEmpty { null }
    // installSizeMb is MiB (the repo manifest's unit); null means unknown, and must stay null
    // rather than collapsing to 0 — a document of unknown size is not the smallest document.
    override val sortSizeBytes: Long? get() = installSizeMb?.let { (it * 1024 * 1024).toLong() }
    override val sortStatusRank: Int get() = when (installStatus) {
        DocInstallStatus.BEING_INSTALLED -> 0
        DocInstallStatus.UPGRADE_AVAILABLE -> 1
        DocInstallStatus.INSTALLED, DocInstallStatus.ERROR_DOWNLOADING, DocInstallStatus.INSTALL_CANCELLED -> 2
        DocInstallStatus.NOT_INSTALLED -> 3
    }
    override val sortRecommended: Boolean get() = recommended
}

/**
 * Whether the current selection contains at least one deletable document — the rule behind the
 * selection bar's delete action.
 *
 * It lives here rather than in the hosts for two reasons. It used to be the inline host expression
 * `displayed.firstOrNull { it.docId in selectedIds }?.canDelete == true`, which (a) no test could
 * reach and (b) consulted an arbitrary row of a multi-selection, so whether the action appeared
 * depended on set iteration order. Classic showed the menu entry unconditionally and toasted
 * `cant_delete_document` for the ones it could not delete; "at least one deletable" is the closest
 * honest rule that never offers an action that would do nothing at all.
 */
fun anySelectedDeletable(rows: List<DocRow>, selectedIds: Set<String>): Boolean =
    rows.any { it.docId in selectedIds && it.canDelete }
