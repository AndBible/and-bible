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

import androidx.annotation.DrawableRes
import net.bible.android.activity.R
import net.bible.sharedcore.navigation.DocCategory

/**
 * The classic per-category document icon, mirroring `BookCategory.imageResource`
 * (`download/DocumentBadges.kt`). Backs the `LocalCategoryIcon` seam so the moved Compose
 * `DocumentRow` shows the bespoke vector art instead of generic Material icons.
 */
@DrawableRes
fun categoryDrawableRes(category: DocCategory): Int = when (category) {
    DocCategory.BIBLE -> R.drawable.ic_bible_24dp
    DocCategory.COMMENTARY -> R.drawable.ic_commentary
    DocCategory.DICTIONARY -> R.drawable.ic_dictionary_24dp
    DocCategory.MAPS -> R.drawable.ic_map_black_24dp
    DocCategory.GENERAL_BOOK -> R.drawable.ic_book_24dp
    DocCategory.AND_BIBLE -> R.drawable.ic_addon_24dp
    DocCategory.OTHER -> R.drawable.ic_book_24dp
}
