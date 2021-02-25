/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.common

import kotlinx.serialization.Serializable
import net.bible.android.control.event.ABEventBus
import net.bible.service.sword.AndBibleAddonFilter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books

class ReloadAddonsEvent

@Serializable
data class ProvidesJson(
    val fonts: List<String>,
    val features: List<String>,
)

object AndBibleAddons {
    private var _addons: List<Book>? = null
    private val addons: List<Book> get() {
        return _addons ?:
            Books.installed().getBooks(AndBibleAddonFilter()).apply {
                _addons = this
            }
    }

    private var _provides: Map<String, ProvidesJson>? = null

    val providedFonts: List<String> get() {
        val rv = mutableListOf<String>()
        addons.forEach {
            it.bookMetaData.getValues("ProvidesAndBibleFont")?.forEach {
                rv.add(it)
            }
        }
        return rv
    }

    fun clearCaches() {
        _provides = null
        _addons =null
        ABEventBus.getDefault().post(ReloadAddonsEvent())
    }

    val fontModuleNames: List<String> get() =
        addons.filter { it.bookMetaData.getValues("ProvidesAndBibleFont") !== null }.map { it.initials }
}

