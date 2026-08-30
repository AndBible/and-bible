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

/**
 * The fields the arrangement engine sorts and groups by, exposed as a seam so ONE engine serves
 * both document list shapes: [DocRow] here and `CloudDocItem` (round 17e-2). The screens rank
 * different things — install status vs sync status — so [sortStatusRank] carries a rank, not an
 * enum: the engine must not know either status vocabulary.
 *
 * [sortName] is whatever the row's HEADLINE starts with, so that "sort by name" agrees with what
 * the user is reading: the document rows render "<abbreviation> <name>" and sort by abbreviation,
 * while a cloud row renders the name alone and will sort by that. [sortSecondaryName] is the
 * always-applied final tie-break, which makes the result independent of input order.
 */
interface DocSortable {
    val sortName: String
    val sortSecondaryName: String
    val sortCategory: DocCategory?
    val sortLanguage: String?
    val sortRepository: String?
    val sortSizeBytes: Long?
    val sortStatusRank: Int
    val sortRecommended: Boolean
}

/**
 * Classic's category display order, preserved verbatim from `computeDisplayedDocuments`'s
 * comparator — INCLUDING its gap at 3, which is a historical artefact of a removed category. The
 * gap is harmless (only the relative order matters) and is kept so this function is provably the
 * same ordering rather than a re-derivation. `null` sorts last, after OTHER.
 */
fun docCategoryRank(category: DocCategory?): Int = when (category) {
    DocCategory.BIBLE -> 0
    DocCategory.COMMENTARY -> 1
    DocCategory.DICTIONARY -> 2
    DocCategory.GENERAL_BOOK -> 4
    DocCategory.MAPS -> 5
    DocCategory.AND_BIBLE -> 6
    DocCategory.OTHER -> 7
    null -> 8
}
