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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.sharedui.app.ports.DocumentFolderExport
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.ImportedFile
import net.bible.sharedui.app.ports.ImportedPage
import net.bible.sharedui.app.ports.MyDocumentFiles
import net.bible.sharedui.ios.IosPending

/** Pending (stream F): nothing is read, nothing is written. */
@kotlin.native.HiddenFromObjC
class IosMyDocumentFiles : MyDocumentFiles {
    override suspend fun importPageFile(ref: DocumentRef): ImportedPage? {
        IosPending.log("MyDocumentFiles.importPageFile")
        return null
    }

    override suspend fun readDocumentFiles(refs: List<DocumentRef>): List<ImportedFile> {
        IosPending.log("MyDocumentFiles.readDocumentFiles")
        return emptyList()
    }

    override suspend fun exportPage(page: MyDocumentPageWithContent) {
        IosPending.log("MyDocumentFiles.exportPage")
    }

    override suspend fun exportPagesToFolder(pages: List<MyDocumentPageWithContent>, tree: DocumentRef): Boolean {
        IosPending.log("MyDocumentFiles.exportPagesToFolder")
        return false
    }

    override suspend fun exportDocumentsToFolder(documents: List<DocumentFolderExport>, tree: DocumentRef): Boolean {
        IosPending.log("MyDocumentFiles.exportDocumentsToFolder")
        return false
    }

    override fun displayName(ref: DocumentRef): String? {
        IosPending.log("MyDocumentFiles.displayName")
        return null
    }
}
