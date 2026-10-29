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
package net.bible.sharedui.desktop.assembly

import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.log.Log
import net.bible.sharedui.app.ai.PromptFiles
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.DocumentFolderExport
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedui.app.ports.ImportedFile
import net.bible.sharedui.app.ports.ImportedPage
import net.bible.sharedui.app.ports.MyDocumentFiles
import net.bible.sharedui.app.ports.TextShare
import net.bible.service.sword.ContentSource
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import kotlinx.coroutines.awaitCancellation
import okio.Buffer
import okio.Source
import org.koin.core.module.Module
import org.koin.dsl.module
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

// OWNER: plan D3b (desktop documents and files: pickers, file delivery, content source, clipboard, share, external
// links, my-document and prompt files). D3b replaces the bindings in this file only and adds its substitutes under its
// own directory. Everything below is a D2b stub: it logs its first use once and answers "cancelled / nothing", except the
// clipboard write and the clipboard-backed text share that the reading panes have used since D2b. Its desktop-only seams:
// [DesktopIncomingFiles] (with the launch arguments in [DesktopLaunchArgs]) and [DesktopFileShare].

private const val TAG = "DesktopDocumentBindings"
private val reported = ConcurrentHashMap.newKeySet<String>()

private fun pending(member: String) {
    if (reported.add(member)) Log.w(TAG, "desktop document stub (D3b pending): $member")
}

class DesktopFilePickerStub : FilePicker {
    override suspend fun pickBackupFile(mime: String): PickedSource? { pending("FilePicker.pickBackupFile"); return null }
    override suspend fun openInput(source: PickedSource): Source { pending("FilePicker.openInput"); return Buffer() }
}

class DesktopFileDeliveryStub : FileDelivery {
    override suspend fun deliver(
        file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String,
        subject: String?, message: String?, chooserTitle: String,
    ): DeliveryResult { pending("FileDelivery.deliver"); return DeliveryResult.CANCELLED }
}

class DesktopDocumentPickerStub : DocumentPicker {
    override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<DocumentRef> { pending("DocumentPicker.openDocuments"); return emptyList() }
    override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? { pending("DocumentPicker.createDocument"); return null }
    override suspend fun openDocumentTree(): DocumentRef? { pending("DocumentPicker.openDocumentTree"); return null }
}

class DesktopImagePickerStub : ImagePicker {
    override suspend fun pickImage(): String? { pending("ImagePicker.pickImage"); return null }
}

class DesktopContentSourceStub : ContentSource {
    override fun openSource(uri: String): Source? { pending("ContentSource.openSource"); return null }
    override fun displayName(uri: String): String? = null
    override fun mimeType(uri: String): String? = null
}

/** Writes to the AWT system clipboard (on the Swing thread; nothing in a headless JVM); reading is still a stub. */
class DesktopClipboardTextStub : ClipboardText {
    override fun read(): String? { pending("ClipboardText.read"); return null }
    override fun write(label: String, text: String) {
        if (GraphicsEnvironment.isHeadless()) { pending("ClipboardText.write (headless)"); return }
        SwingUtilities.invokeLater {
            try {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            } catch (e: Throwable) { // AWTError too: no display behind a non-headless toolkit
                Log.w(TAG, "clipboard write failed", e)
            }
        }
    }
}

/** No share sheet yet: "sharing" text puts it on the clipboard (what the reading panes have done since D2b). */
class DesktopTextShareStub(private val clipboard: ClipboardText) : TextShare {
    override fun shareText(text: String, chooserTitle: String) = clipboard.write(chooserTitle, text)
}

/** No incoming files: suspends until cancelled. */
class DesktopIncomingFilesStub : DesktopIncomingFiles {
    override suspend fun receive(open: (List<DocumentRef>, () -> Unit) -> Unit, onFailure: () -> Unit) = awaitCancellation()
}

/** No file share: the reading panes skip "share as HTML". */
class DesktopFileShareStub : DesktopFileShare {
    override val canShare: Boolean get() = false
    override fun shareFile(path: String) = pending("DesktopFileShare.shareFile")
}

class DesktopExternalLinksStub : ExternalLinks {
    override val askBeforeOpening: Boolean get() = false
    override fun open(url: String) = pending("ExternalLinks.open")
}

class DesktopMyDocumentFilesStub : MyDocumentFiles {
    override suspend fun importPageFile(ref: DocumentRef): ImportedPage? { pending("MyDocumentFiles.importPageFile"); return null }
    override suspend fun readDocumentFiles(refs: List<DocumentRef>): List<ImportedFile> { pending("MyDocumentFiles.readDocumentFiles"); return emptyList() }
    override suspend fun exportPage(page: MyDocumentPageWithContent) = pending("MyDocumentFiles.exportPage")
    override suspend fun exportPagesToFolder(pages: List<MyDocumentPageWithContent>, tree: DocumentRef): Boolean { pending("MyDocumentFiles.exportPagesToFolder"); return false }
    override suspend fun exportDocumentsToFolder(documents: List<DocumentFolderExport>, tree: DocumentRef): Boolean { pending("MyDocumentFiles.exportDocumentsToFolder"); return false }
    override fun displayName(ref: DocumentRef): String? = null
}

class DesktopPromptFilesStub : PromptFiles {
    override suspend fun read(ref: DocumentRef): String { pending("PromptFiles.read"); return "" }
    override suspend fun write(ref: DocumentRef, text: String) = pending("PromptFiles.write")
    override suspend fun installAsAddon(ref: DocumentRef) = pending("PromptFiles.installAsAddon")
    override fun suggestedExportName(): String = "prompts.zip"
}

fun desktopDocumentModule(): Module = module {
    single<FilePicker> { DesktopFilePickerStub() }
    single<FileDelivery> { DesktopFileDeliveryStub() }
    single<DocumentPicker> { DesktopDocumentPickerStub() }
    single<ImagePicker> { DesktopImagePickerStub() }
    single<ContentSource> { DesktopContentSourceStub() }
    single<ClipboardText> { DesktopClipboardTextStub() }
    single<TextShare> { DesktopTextShareStub(get<ClipboardText>()) }
    single<DesktopIncomingFiles> { DesktopIncomingFilesStub() }
    single<DesktopFileShare> { DesktopFileShareStub() }
    single<ExternalLinks> { DesktopExternalLinksStub() }
    single<MyDocumentFiles> { DesktopMyDocumentFilesStub() }
    single<PromptFiles> { DesktopPromptFilesStub() }
}
