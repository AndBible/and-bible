/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.platform

import net.bible.service.common.AndBibleAddons
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedlogic.platform.AddonReadingPlan
import okio.Path.Companion.toOkioPath

/** Android [AddonCatalog]: delegates to [AndBibleAddons]. */
class AndroidAddonCatalog : AddonCatalog {
    override fun clearCaches() = AndBibleAddons.clearCaches()

    override val readingPlans: Map<String, AddonReadingPlan>
        get() = AndBibleAddons.providedReadingPlans.mapValues { (_, p) ->
            AddonReadingPlan(p.book, p.fileName, p.isDateBased, p.file.toOkioPath())
        }
}
