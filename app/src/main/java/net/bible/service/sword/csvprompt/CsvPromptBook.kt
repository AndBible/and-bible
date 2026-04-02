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

package net.bible.service.sword.csvprompt

import android.util.Log
import net.bible.android.SharedConstants
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractBookDriver
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.io.File

private const val TAG = "CsvPromptBook"

val Book.csvPromptFile: File get() {
    val fileName = bookMetaData.getProperty("AndBibleProvidesPrompts") ?: ""
    return File(File(SharedConstants.modulesDir, "prompts"), fileName)
}

class CsvPromptSwordDriver : AbstractBookDriver() {
    override fun getBooks(): Array<Book> = emptyArray()
    override fun getDriverName(): String = "CsvPromptSwordDriver"

    override fun isDeletable(book: Book): Boolean = book.csvPromptFile.canWrite()

    override fun delete(book: Book) {
        book.csvPromptFile.delete()
        Books.installed().removeBook(book)
    }
}

fun addCsvPromptBook(file: File) {
    if (!(file.canRead() && file.isFile && file.extension.equals("csv", ignoreCase = true))) return

    val packName = file.nameWithoutExtension
    val moduleInitials = "Prompts_$packName"

    if (Books.installed().getBook(moduleInitials) != null) return

    val conf = """
[$moduleInitials]
Description=$packName prompts
Category=And Bible
ModDrv=RawGenBook
DataPath=./prompts/
Encoding=UTF-8
AndBibleProvidesPrompts=${file.name}
AndBibleMinimumVersion=892
"""

    Log.i(TAG, "Creating CSV prompt module $moduleInitials")

    val metadata = SwordBookMetaData(conf.toByteArray(), moduleInitials)
    metadata.location = file.parentFile.toURI()
    metadata.driver = CsvPromptSwordDriver()
    val book = SwordBook(metadata, NullBackend())
    Books.installed().addBook(book)
}

fun addManuallyInstalledCsvPromptBooks() {
    val dir = File(SharedConstants.modulesDir, "prompts")
    Log.i(TAG, "Scanning for CSV prompt packs in $dir (exists=${dir.isDirectory})")
    if (!(dir.isDirectory && dir.canRead())) return

    val files = dir.listFiles() ?: emptyArray()
    Log.i(TAG, "Found ${files.size} files in prompts dir")
    for (f in files) {
        if (f.isFile && f.canRead() && f.extension.equals("csv", ignoreCase = true)) {
            addCsvPromptBook(f)
        }
    }
}
