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
import kotlinx.serialization.serializer
import net.bible.android.control.event.ABEventBus
import net.bible.service.common.CommonUtils.json
import net.bible.service.sword.AndBibleAddonFilter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import java.io.File


class ReloadAddonsEvent

@Serializable
data class ProvidesJson(
    val fonts: List<String>,
    val features: List<String>,
)

object AndBibleAddons {
    private var _addons: List<Book>? = null

    val addons: List<Book> get() {
        return _addons ?:
            Books.installed().getBooks(AndBibleAddonFilter()).apply {
                _addons = this
            }
    }

    private var _provides: Map<String, ProvidesJson>? = null

    val provides: Map<String, ProvidesJson> get() = _provides ?: readProvides.apply {
        _provides = this
    }

    private val readProvides: Map<String, ProvidesJson> get() {
        val map = HashMap<String, ProvidesJson>()
        addons.forEach {
            val modFolder = File(it.bookMetaData.location)
            val abFolder = File(modFolder, "and-bible")
            val providesJsonFile = File(abFolder, "provides.json")
            val providesJson = providesJsonFile.readBytes().decodeToString()
            val provides: ProvidesJson = json.decodeFromString(serializer(), providesJson)
            map[it.initials] = provides
        }
        return map
    }

    val providedFonts: List<String> get() {
        val rv = mutableListOf<String>()

        provides.values.forEach {
            it.fonts.forEach {
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
        provides.keys.filter { !provides[it]?.fonts.isNullOrEmpty() }
}

