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

package net.bible.service.sword.backgroundimage

import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractBookDriver
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import net.bible.sharedcore.platform.AppDirectories
import net.bible.service.sword.isWritableFile
import okio.FileSystem
import okio.Path
import org.crosswire.common.util.KFiles
import org.crosswire.common.util.KUri
import org.koin.mp.KoinPlatform

private const val TAG = "BackgroundImageBook"

const val BACKGROUND_IMAGE_MARKER = "AndBibleProvidesBackgroundImage"
const val BACKGROUND_IMAGE_DIR = "background"

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

class BackgroundImageSwordDriver : AbstractBookDriver() {
    override fun getBooks(): Array<Book> = emptyArray()
    override fun getDriverName(): String = "BackgroundImageSwordDriver"
    override fun isDeletable(book: Book): Boolean = isWritableFile(book.backgroundImageFile)
    override fun delete(book: Book) {
        KFiles.delete(book.backgroundImageFile)
        Books.installed().removeBook(book)
    }
}

/** The `background` module folder, resolved through the [AppDirectories] port at call time. */
internal fun backgroundImageModulesDir(): Path = KoinPlatform.getKoin().get<AppDirectories>().modulesDir / BACKGROUND_IMAGE_DIR

val Book.backgroundImageFile: Path get() {
    val marker = bookMetaData.getProperty(BACKGROUND_IMAGE_MARKER) ?: ""
    val fileName = marker.split(";").getOrNull(1) ?: ""
    return backgroundImageModulesDir() / fileName
}

/**
 * A manually-installed background-image module: a synthetic book created by
 * [addBackgroundImageBook] from a single image file dropped into `modulesDir/background`.
 * Its metadata is byte-array-constructed, so it has no on-disk `.conf`
 * ([SwordBookMetaData.getConfigFile] is null). As with [net.bible.service.sword.ttf], the
 * marker property alone is not sufficient to distinguish it from a hypothetical downloaded
 * add-on carrying the same marker — we additionally require the absence of a config file.
 */
val Book.isBackgroundImageModule get() =
    bookMetaData.getProperty(BACKGROUND_IMAGE_MARKER) != null &&
        (bookMetaData as? SwordBookMetaData)?.configFile == null

/** Build a URL-safe, unique module initials from a display name. */
fun backgroundImageModuleInitials(displayName: String, exists: (String) -> Boolean): String {
    val sanitized = displayName.substringBeforeLast('.')
        .replace(Regex("[^A-Za-z0-9]+"), "_")
        .trim('_')
        .ifEmpty { "image" }
    val base = "BGIMG_$sanitized"
    if (!exists(base)) return base
    var i = 2
    while (exists("${base}_$i")) i++
    return "${base}_$i"
}

fun addBackgroundImageBook(file: Path) {
    if (!(KFiles.isFile(file) && file.name.substringAfterLast('.', "").lowercase() in imageExtensions)) return

    // Skip a file that already backs a registered module, so repeated scans stay idempotent.
    // (Done before generating initials: otherwise dedup would mint a fresh _N initials and the
    // existing-book guard below would fail to fire, registering a duplicate module each scan.)
    if (Books.installed().getBooks().any { it.isBackgroundImageModule && it.backgroundImageFile == file }) return

    val displayName = file.name.substringBeforeLast('.')
    val moduleInitials = backgroundImageModuleInitials(file.name) {
        Books.installed().getBook(it) != null
    }
    if (Books.installed().getBook(moduleInitials) != null) return

    val conf = """
[$moduleInitials]
Description=$displayName
Category=And Bible
ModDrv=RawGenBook
DataPath=./$BACKGROUND_IMAGE_DIR/
Encoding=UTF-8
$BACKGROUND_IMAGE_MARKER=$displayName;${file.name}
AndBibleMinimumVersion=1112
"""
    Log.i(TAG, "Creating background-image module $moduleInitials, $displayName")

    val metadata = SwordBookMetaData(conf.toByteArray(), moduleInitials)
    metadata.location = KUri("file", null, "${file.parent!!}/", null)
    metadata.setDriver(BackgroundImageSwordDriver())
    val book = SwordBook(metadata, NullBackend())
    Books.installed().addBook(book)
}

fun addManuallyInstalledBackgroundImageBooks() {
    val dir = backgroundImageModulesDir()
    if (!KFiles.isDirectory(dir)) return
    for (f in FileSystem.SYSTEM.listRecursively(dir)) {
        if (KFiles.isFile(f) && f.name.substringAfterLast('.', "").lowercase() in imageExtensions) {
            addBackgroundImageBook(f)
        }
    }
}
