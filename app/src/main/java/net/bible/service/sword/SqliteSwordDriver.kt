/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractBookDriver
import androidx.annotation.VisibleForTesting
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.crosswire.common.util.KFiles


val Book.dbPath get() = getProperty("AndBibleDbFile")!!.toPath()

/**
 * What `File.canWrite()` answered for a regular file: okio has no permission API, so open the existing file
 * for appending (writes nothing) and see whether the OS allows it.
 */
@VisibleForTesting
fun isWritableFile(path: Path): Boolean {
    if (!KFiles.isFile(path)) return false
    return try {
        FileSystem.SYSTEM.appendingSink(path, mustExist = true).close()
        true
    } catch (e: IOException) {
        false
    }
}

/**
 * Driver for both MySword and MyBible books
 */
class SqliteSwordDriver: AbstractBookDriver() {
    override fun getBooks(): Array<Book> {
        return emptyArray()
    }

    override fun getDriverName(): String {
        return "SqliteSwordDriver"
    }

    override fun isDeletable(book: Book): Boolean {
        return isWritableFile(book.dbPath)
    }

    override fun delete(book: Book) {
        KFiles.delete(book.dbPath)
        Books.installed().removeBook(book)
    }
}
