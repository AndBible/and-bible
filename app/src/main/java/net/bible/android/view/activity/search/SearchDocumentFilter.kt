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

import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.sword.SwordBook

/**
 * Documents offered in the search-results document selector.
 *
 * A Strong's-number query ("find all occurrences") only matches Strong's-tagged modules, so for a
 * Strong's search the chooser is restricted to Strong's-enabled Bibles. Any other search offers all
 * Bibles.
 */
fun candidateSearchDocuments(strongsSearch: Boolean, allBibles: List<SwordBook>): List<SwordBook> =
    if (strongsSearch) allBibles.filter { it.hasFeature(FeatureType.STRONGS_NUMBERS) } else allBibles
