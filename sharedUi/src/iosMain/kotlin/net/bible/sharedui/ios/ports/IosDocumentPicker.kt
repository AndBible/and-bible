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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.files.IosDocumentPickerSession
import net.bible.sharedcore.platform.files.IosPresenter
import net.bible.sharedcore.platform.files.iosDocumentContentTypes
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeFolder

/** Returns absolute file URLs; callers own security-scoped access while reading/writing them. */
@kotlin.native.HiddenFromObjC
class IosDocumentPicker(
    private val presenter: () -> UIViewController? = IosPresenter::topViewController,
) : DocumentPicker {
    private val session = IosDocumentPickerSession()

    override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<DocumentRef> =
        pick {
            UIDocumentPickerViewController(
                forOpeningContentTypes = iosDocumentContentTypes(mimeTypes), asCopy = true,
            ).apply { allowsMultipleSelection = multiple }
        }

    override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? =
        withContext(Dispatchers.Main) {
            session.cancelPrevious()
            var staging: Path? = null
            try {
                require(suggestedName.isNotBlank() && suggestedName != "." && suggestedName != ".." &&
                    '/' !in suggestedName && '\\' !in suggestedName && '\u0000' !in suggestedName) {
                    "Invalid document file name"
                }
                val directory = NSTemporaryDirectory().toPath() / "andbible-document-${NSUUID().UUIDString}"
                staging = directory
                val file = directory / suggestedName
                withContext(Dispatchers.IO) {
                    FileSystem.SYSTEM.createDirectory(directory)
                    FileSystem.SYSTEM.write(file) { }
                }
                pick {
                    UIDocumentPickerViewController(
                        forExportingURLs = listOf(NSURL.fileURLWithPath(file.toString())), asCopy = false,
                    )
                }.firstOrNull()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("IosDocumentPicker", "Document creation failed", error)
                null
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    staging?.let { directory ->
                        try { FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false) }
                        catch (error: Exception) { Log.w("IosDocumentPicker", "Document staging cleanup failed", error) }
                    }
                }
            }
        }

    override suspend fun openDocumentTree(): DocumentRef? = pick {
        // Unlike imported document copies, the original folder needs security-scoped access on use.
        UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
    }.firstOrNull()

    private suspend fun pick(controller: () -> UIDocumentPickerViewController): List<DocumentRef> =
        withContext(Dispatchers.Main) {
            try {
                session.pick(controller(), presenter).map { DocumentRef(it.absoluteString!!) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("IosDocumentPicker", "Document picker failed", error)
                emptyList()
            }
        }
}
