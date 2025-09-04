/*
 * Copyright (c) 2025 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.ttf

import android.util.Log
import net.bible.android.SharedConstants
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.io.File

const val TAG = "TtfBook"

fun addTtfBook(file: File) {
    if (!(file.canRead() && file.isFile && file.extension.lowercase() == "ttf")) return
    
    val fontName = file.nameWithoutExtension
    val moduleInitials = "TTF_$fontName"
    
    if (Books.installed().getBook(moduleInitials) != null) return
    
    val conf = """
[$moduleInitials]
Description=$fontName
Category=And Bible
ModDrv=RawGenBook
DataPath=./ttf/
Encoding=UTF-8
AndBibleProvidesFont=$fontName;${file.name}
AndBibleMinimumVersion=892
"""
    
    Log.i(TAG, "Creating TTF font module $moduleInitials, $fontName")

    val metadata = SwordBookMetaData(conf.toByteArray(), moduleInitials)
    metadata.location = file.parentFile.toURI()
    val backend = NullBackend()
    val book = SwordBook(metadata, backend)
    Books.installed().addBook(book)
}

fun addManuallyInstalledTtfBooks() {
    val dir = File(SharedConstants.modulesDir, "ttf")
    if (!(dir.isDirectory && dir.canRead())) return
    
    for (f in dir.walkTopDown()) {
        if (f.isFile && f.canRead() && f.extension.lowercase() == "ttf") {
            addTtfBook(f)
        }
    }
}
