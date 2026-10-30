package net.bible.sharedui.desktop.files

import kotlinx.coroutines.CancellationException
import net.bible.sharedcore.platform.desktop.files.FileChooserBackend
import net.bible.sharedcore.platform.desktop.files.fileFiltersFor
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef

class DesktopDocumentPicker(private val chooser: FileChooserBackend) : DocumentPicker {
    override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<DocumentRef> = try {
        chooser.open(fileFiltersFor(mimeTypes), multiple, false)?.map { DocumentRef(it.toUri().toString()) } ?: emptyList()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

    override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? = try {
        chooser.save(suggestedName, fileFiltersFor(listOf(mimeType)))?.let { DocumentRef(it.toUri().toString()) }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    override suspend fun openDocumentTree(): DocumentRef? = try {
        chooser.open(emptyList(), false, true)?.firstOrNull()?.let { DocumentRef(it.toUri().toString()) }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
