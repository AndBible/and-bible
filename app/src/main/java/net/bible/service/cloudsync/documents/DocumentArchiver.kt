/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndBible. If not, see <http://www.gnu.org/licenses/>.
 */

package net.bible.service.cloudsync.documents

import net.bible.android.control.backup.BackupControl
import net.bible.service.common.CommonUtils
import net.bible.service.sword.epub.isManuallyInstalledEpub
import net.bible.service.sword.esword.isManuallyInstalledESwordBook
import net.bible.service.sword.mybible.isManuallyInstalledMyBibleBook
import net.bible.service.sword.mysword.isManuallyInstalledMySwordBook
import org.crosswire.jsword.book.Book
import java.io.File

/**
 * Packages installed documents into `.abmd.zip` archives and installs such archives back
 * into the running app, all without any Activity/UI. Used by the cloud document-sync layer.
 *
 * Packaging reuses [BackupControl.createSingleModuleZip]; headless installation reuses
 * [BackupControl.installModuleArchive], which shares its extraction/registration logic
 * with the Activity-based `InstallZip` flow (no duplication).
 */
object DocumentArchiver {
    /** Classify [book] by which kind of manual installation (if any) it came from. */
    fun documentTypeOf(book: Book): DocumentType = when {
        book.isManuallyInstalledMyBibleBook -> DocumentType.MYBIBLE
        book.isManuallyInstalledMySwordBook -> DocumentType.MYSWORD
        book.isManuallyInstalledESwordBook -> DocumentType.ESWORD
        book.isManuallyInstalledEpub -> DocumentType.EPUB
        else -> DocumentType.SWORD
    }

    /** The document's `Version` metadata property, defaulting to "0.0" when absent. */
    fun documentVersion(book: Book): String =
        book.bookMetaData.getProperty("Version") ?: "0.0"

    /**
     * Package [book] into a fresh temporary `.abmd.zip` archive and return the file.
     * Any pre-existing temp file for the same document is overwritten.
     */
    suspend fun packageDocument(book: Book): File {
        val zipFile = File(CommonUtils.tmpDir, "doc-${book.initials}.abmd.zip")
        if (zipFile.exists()) zipFile.delete()
        BackupControl.createSingleModuleZip(book, zipFile)
        return zipFile
    }

    /**
     * Install an `.abmd.zip` archive (as produced by [packageDocument]) headlessly and
     * report whether the document is registered afterwards.
     *
     * @param archive the `.abmd.zip` file to install.
     * @param expectedInitials initials of the document expected to appear in the JSword
     *   registry after install; when null, success is inferred from the registry growing.
     * @return true if the document is installed.
     */
    suspend fun installArchive(archive: File, expectedInitials: String? = null): Boolean =
        BackupControl.installModuleArchive(archive, expectedInitials)
}
