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

/** One sortable dimension. Order here is the canonical default priority — see [DOC_SORT_KEY_ORDER]. */
enum class DocSortKey { STATUS, RECOMMENDED, TYPE, NAME, LANGUAGE, REPOSITORY, SIZE }

/** One entry of the user's ordered sort list: which dimension, and which way. */
data class DocSortCriterion(val key: DocSortKey, val descending: Boolean = false)

/** The optional outer grouping. [NONE] is a flat list. */
enum class DocGroupBy { NONE, TYPE, LANGUAGE, REPOSITORY, STATUS }

/**
 * The whole user-controlled arrangement of a document list.
 *
 * [sort] is a FULL permutation of the screen's applicable keys, not a subset: there is no per-key
 * on/off, because dragging a criterion to the bottom already makes it a mere tie-break, and a
 * second control per row would buy nothing. [repository] is null for "every repository".
 */
data class DocArrangement(
    val sort: List<DocSortCriterion>,
    val groupBy: DocGroupBy = DocGroupBy.NONE,
    val repository: String? = null,
)

/** Canonical priority. The head of this list reproduces the pre-17e fixed comparator. */
val DOC_SORT_KEY_ORDER: List<DocSortKey> = listOf(
    DocSortKey.STATUS, DocSortKey.RECOMMENDED, DocSortKey.TYPE, DocSortKey.NAME,
    DocSortKey.LANGUAGE, DocSortKey.REPOSITORY, DocSortKey.SIZE,
)

fun defaultArrangement(applicable: Set<DocSortKey>): DocArrangement =
    DocArrangement(sort = DOC_SORT_KEY_ORDER.filter { it in applicable }.map { DocSortCriterion(it) })

/**
 * A comparator over one criterion. Missing values sort LAST in BOTH directions: a document of
 * unknown size is not the smallest document, and flipping the direction must not promote every
 * unknown to the top.
 */
private fun <T, R : Comparable<R>> nullsLastBy(descending: Boolean, selector: (T) -> R?): Comparator<T> =
    Comparator { a, b ->
        val x = selector(a)
        val y = selector(b)
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            descending -> y.compareTo(x)
            else -> x.compareTo(y)
        }
    }

private fun <T : DocSortable> comparatorFor(criterion: DocSortCriterion): Comparator<T> =
    when (criterion.key) {
        DocSortKey.STATUS -> nullsLastBy(criterion.descending) { it.sortStatusRank }
        // "Recommended first" ascending, so the flag is inverted before comparing (false < true).
        DocSortKey.RECOMMENDED -> nullsLastBy(criterion.descending) { !it.sortRecommended }
        DocSortKey.TYPE -> nullsLastBy(criterion.descending) { docCategoryRank(it.sortCategory) }
        DocSortKey.NAME -> nullsLastBy(criterion.descending) { it.sortName.lowercase() }
        DocSortKey.LANGUAGE -> nullsLastBy(criterion.descending) { it.sortLanguage?.lowercase() }
        DocSortKey.REPOSITORY -> nullsLastBy(criterion.descending) { it.sortRepository?.lowercase() }
        DocSortKey.SIZE -> nullsLastBy(criterion.descending) { it.sortSizeBytes }
    }

/**
 * Order [rows] by [DocArrangement.sort], folding the criteria in list order — position IS priority.
 *
 * [DocSortable.sortSecondaryName] is always appended as a final tie-break so the result does not
 * depend on the input order. (`sortedWith` is stable, so without it two rows equal on every
 * criterion would silently keep whatever order the repository listing happened to produce, and a
 * test asserting an exact list would pass or fail by accident.)
 */
fun <T : DocSortable> sortDocuments(rows: List<T>, arrangement: DocArrangement): List<T> {
    val comparators = arrangement.sort.map { comparatorFor<T>(it) } +
        nullsLastBy<T, String>(descending = false) { it.sortSecondaryName.lowercase() }
    // Chained by hand rather than with Comparator.thenComparing: that is a JDK default method,
    // available through kotlin.Comparator's interop on JVM but NOT in commonMain, so it would
    // compile here and fail the iOS gate.
    val combined = Comparator<T> { a, b ->
        for (c in comparators) {
            val r = c.compare(a, b)
            if (r != 0) return@Comparator r
        }
        0
    }
    return rows.sortedWith(combined)
}
