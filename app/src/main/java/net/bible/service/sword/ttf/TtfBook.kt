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

package net.bible.service.sword.ttf

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractBookDriver
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import net.bible.sharedcore.platform.AppDirectories
import okio.Path
import org.crosswire.common.util.KFiles
import org.crosswire.common.util.KUri
import org.koin.mp.KoinPlatform
import okio.FileSystem
import net.bible.service.sword.isWritableFile
import net.bible.service.sword.epub.child

const val TAG = "TtfBook"

class TtfSwordDriver: AbstractBookDriver() {
    override fun getBooks(): Array<Book> {
        return emptyArray()
    }

    override fun getDriverName(): String {
        return "TtfSwordDriver"
    }

    override fun isDeletable(book: Book): Boolean {
        return isWritableFile(book.ttfFile)
    }

    override fun delete(book: Book) {
        KFiles.delete(book.ttfFile)
        Books.installed().removeBook(book)
    }
}

/** The `ttf` module folder, resolved through the [AppDirectories] port at call time. */
@VisibleForTesting fun ttfModulesDir(): Path = KoinPlatform.getKoin().get<AppDirectories>().modulesDir / "ttf"

val Book.ttfFile: Path get() {
    val providesFont = bookMetaData.getProperty("AndBibleProvidesFont") ?: ""
    val fileName = providesFont.split(";").getOrNull(1) ?: ""
    return ttfModulesDir().child(fileName)
}

/**
 * A manually-installed TTF font module: a synthetic book created by [addTtfBook] from a single
 * `.ttf` file dropped into `modulesDir/ttf`. Its metadata is byte-array-constructed, so it has
 * no on-disk `.conf` ([SwordBookMetaData.getConfigFile] is null). Used by backup to package the
 * underlying `.ttf` file instead of falling into the generic SWORD branch.
 *
 * The `AndBibleProvidesFont` property alone is NOT sufficient: a downloaded font add-on module
 * (e.g. "FontPack") also carries it, but is a real SWORD module with an on-disk `.conf` and its
 * font files under a DataPath dir. Such modules must go through the generic SWORD branch (conf +
 * data dir), so we additionally require the absence of a config file to distinguish them.
 */
val Book.isManuallyInstalledTtf get() =
    bookMetaData.getProperty("AndBibleProvidesFont") != null &&
        (bookMetaData as? SwordBookMetaData)?.configFile == null

fun addTtfBook(file: Path) {
    if (!(KFiles.isFile(file) && file.name.substringAfterLast('.', "").lowercase() == "ttf")) return
    
    val fontName = file.name.substringBeforeLast('.')
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

    val metadata = SwordBookMetaData(conf.encodeToByteArray(), moduleInitials)
    metadata.location = KUri("file", null, "${file.parent!!}/", null)
    metadata.setDriver(TtfSwordDriver())
    val backend = NullBackend()
    val book = SwordBook(metadata, backend)
    Books.installed().addBook(book)
}

fun addManuallyInstalledTtfBooks() {
    val dir = ttfModulesDir()
    if (!KFiles.isDirectory(dir)) return
    // An unreadable folder must not crash startup (okio's listRecursively throws on it, File.canRead guarded it before)
    if (KFiles.listOrNull(dir) == null) return
    
    for (f in FileSystem.SYSTEM.listRecursively(dir)) {
        if (KFiles.isFile(f) && f.name.substringAfterLast('.', "").lowercase() == "ttf") {
            addTtfBook(f)
        }
    }
}
