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

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractBookDriver
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import net.bible.sharedcore.platform.AppDirectories
import net.bible.service.sword.isWritableFile
import okio.Path
import org.crosswire.common.util.KFiles
import org.crosswire.common.util.KUri
import org.koin.mp.KoinPlatform
import net.bible.service.sword.epub.child

private const val TAG = "CsvPromptBook"

/** The `prompts` module folder, resolved through the [AppDirectories] port at call time. */
@VisibleForTesting fun csvPromptModulesDir(): Path = KoinPlatform.getKoin().get<AppDirectories>().modulesDir / "prompts"

val Book.csvPromptFile: Path get() {
    val fileName = bookMetaData.getProperty("AndBibleProvidesPrompts") ?: ""
    return csvPromptModulesDir().child(fileName)
}

class CsvPromptSwordDriver : AbstractBookDriver() {
    override fun getBooks(): Array<Book> = emptyArray()
    override fun getDriverName(): String = "CsvPromptSwordDriver"

    override fun isDeletable(book: Book): Boolean = isWritableFile(book.csvPromptFile)

    override fun delete(book: Book) {
        KFiles.delete(book.csvPromptFile)
        Books.installed().removeBook(book)
    }
}

fun addCsvPromptBook(file: Path) {
    if (!(KFiles.isFile(file) && file.name.substringAfterLast('.', "").equals("csv", ignoreCase = true))) return

    val packName = file.name.substringBeforeLast('.')
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

    val metadata = SwordBookMetaData(conf.encodeToByteArray(), moduleInitials)
    metadata.location = KUri("file", null, "${file.parent!!}/", null)
    metadata.setDriver(CsvPromptSwordDriver())
    val book = SwordBook(metadata, NullBackend())
    Books.installed().addBook(book)
}

fun addManuallyInstalledCsvPromptBooks() {
    val dir = csvPromptModulesDir()
    Log.i(TAG, "Scanning for CSV prompt packs in $dir (exists=${KFiles.isDirectory(dir)})")
    if (!KFiles.isDirectory(dir)) return

    val files = KFiles.list(dir).map { dir / it }
    Log.i(TAG, "Found ${files.size} files in prompts dir")
    for (f in files) {
        if (KFiles.isFile(f) && f.name.substringAfterLast('.', "").equals("csv", ignoreCase = true)) {
            addCsvPromptBook(f)
        }
    }
}
