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

package net.bible.sharedlogic.platform

import okio.Path
import org.crosswire.jsword.book.Book

/** A reading plan provided by an installed add-on module; [path] is the plan file inside the module. */
class AddonReadingPlan(val book: Book, val fileName: String, val isDateBased: Boolean, val path: Path)

/** Add-on content (SWORD-module provided reading plans, prompt packs) as a platform port. */
interface AddonCatalog {
    /** Drops cached add-on lookups; called when installed books change. */
    fun clearCaches()

    /** Reading plans provided by installed add-on modules, keyed as the platform catalog keys them. */
    val readingPlans: Map<String, AddonReadingPlan>
}
