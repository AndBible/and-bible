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

import android.util.Log
import net.bible.android.control.event.ABEventBus
import net.bible.service.sword.AndBibleAddonFilter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import java.io.File

class ReloadAddonsEvent

class ProvidedFont(val book: Book, val name: String, val path: String) {
    val file: File get() = File(File(book.bookMetaData.location), path)
}

class ProvidedReadingPlan(val book: Book, val fileName: String, val isDateBased: Boolean) {
    val file: File get() = File(File(book.bookMetaData.location), fileName)
}

object AndBibleAddons {
    private var _addons: List<Book>? = null
    private val addons: List<Book> get() {
        return _addons ?:
            Books.installed().getBooks(AndBibleAddonFilter()).apply {
                _addons = this
            }
    }

    val providedFonts: Map<String, ProvidedFont> get() {
        val fontsByName = mutableMapOf<String, ProvidedFont>()
        for (book in addons) {
            for (it in book.bookMetaData.getValues("AndBibleProvidesFont")?: emptyList()) {
                val values = it.split(";")
                val name = values[0]
                val filename = values[1]
                val provFont = ProvidedFont(book, name, filename)
                if(provFont.file.canRead()) {
                    fontsByName[name] = provFont
                } else {
                    Log.e("ProvidedFonts", "Could not read font file ${provFont.file}")
                }
            }
        }
        return fontsByName
    }

    val fontsByModule: Map<String, List<ProvidedFont>> get() {
        val fontsByModule = mutableMapOf<String, MutableList<ProvidedFont>>()
        for (it in providedFonts.values) {
            val fonts = fontsByModule[it.book.initials] ?: mutableListOf<ProvidedFont>()
                .apply {fontsByModule[it.book.initials] = this}
            fonts.add(it)
        }
        return fontsByModule
    }

    val providedReadingPlans: Map<String, ProvidedReadingPlan> get() {
        val readingPlansByFileName = mutableMapOf<String, ProvidedReadingPlan>()
        for (book in addons) {
            for (fileName in book.bookMetaData.getValues("AndBibleProvidesReadingPlan")?: emptyList()) {
                val isDateBased = book.bookMetaData.getProperty("AndBibleReadingPlanDateBased")?.equals("True", ignoreCase = true) == true
                val planCode = File(File(book.bookMetaData.location), fileName).nameWithoutExtension
                readingPlansByFileName[planCode] = ProvidedReadingPlan(book, fileName, isDateBased)
            }
        }
        return readingPlansByFileName
    }

    fun clearCaches() {
        _addons =null
        ABEventBus.getDefault().post(ReloadAddonsEvent())
    }

    val fontModuleNames: List<String> get() =
        addons.filter { it.bookMetaData.getValues("AndBibleProvidesFont") !== null }.map { it.initials }

    val featureModuleNames: List<String> get() =
        addons.filter { it.bookMetaData.getValues("AndBibleProvidesFeature") !== null }.map { it.initials }

    val styleModuleNames: List<String> get() =
        addons.filter { it.bookMetaData.getValues("AndBibleProvidesStyle") !== null }.map { it.initials }
}

