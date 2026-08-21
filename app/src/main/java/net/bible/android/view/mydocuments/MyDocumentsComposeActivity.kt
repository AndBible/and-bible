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
package net.bible.android.view.mydocuments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.mydocuments.MyDocumentsActivity
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.mydocument.AiDocPagesChangedEvent
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.mydocuments.MyDocItem
import net.bible.sharedcore.mydocuments.MyDocumentsController
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.mydocuments.MyDocumentsScreen

/**
 * Compose host for MyDocuments — the new-path twin of classic [MyDocumentsActivity]. Loads Room
 * rows off-main, assigns STABLE load-index Long ids (survive reorder) with an [entityByLong] map
 * back to the entities, and drives the shared [MyDocumentsController]/[MyDocumentsScreen]. All
 * Room/SWORD/SAF/EventBus side effects are host-side.
 *
 * Result-className parity (correctness-critical): results are built as
 * `Intent(this, MyDocumentsActivity::class.java)` with `documentInitials`/`pageKey`/`changed`
 * extras, so `MainBibleActivity.onActivityResult`'s className dispatch (~line 1956) is untouched.
 */
class MyDocumentsComposeActivity : ActivityBase() {
    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    /** Stable load-index Long id -> the backing Room entity, rebuilt on every (re)load. */
    private var entityByLong: Map<Long, MyDocument> = emptyMap()
    private lateinit var resultIntent: Intent
    private var finished = false
    private var pendingExportId: Long? = null
    private var pendingExportIds: List<Long> = emptyList()

    /** SAF-picked URIs awaiting a user-entered name; the import name dialog is shown while non-null. */
    private var pendingImportUris: List<Uri>? = null
    /** Pre-fill string for the import name dialog (non-null ⇒ dialog shown). Compose state so the screen
     * recomposes when the SAF picker returns. */
    private var importNamePrompt by mutableStateOf<String?>(null)

    private val controller by lazy {
        MyDocumentsController(
            onOpen = ::openDocument,
            onImport = { importFilesLauncher.launch(arrayOf("text/*")) },
            onExport = { id -> pendingExportId = id; exportTreeLauncher.launch(null) },
            onCreate = ::createDocument,
            onExportSelected = { ids -> pendingExportIds = ids; exportBatchTreeLauncher.launch(null) },
            onSave = ::applyChanges,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultIntent = Intent(this, MyDocumentsActivity::class.java)
        val title = getString(R.string.my_documents_title)
        lifecycleScope.launch { reload() }
        setContent {
            AbAppTheme {
                    val documents by controller.documents.collectAsState()
                    val dirty by controller.dirty.collectAsState()
                    val query by controller.query.collectAsState()
                    val filtering by controller.filtering.collectAsState()
                    val searchModeActive by controller.searchModeActive.collectAsState()
                    val selection by controller.selection.collectAsState()
                    val totalCount by controller.totalCount.collectAsState()
                    MyDocumentsScreen(
                        title = title,
                        documents = documents,
                        dirty = dirty,
                        query = query,
                        filtering = filtering,
                        searchModeActive = searchModeActive,
                        totalCount = totalCount,
                        onOpenSearch = controller::openSearch,
                        onCloseSearch = controller::closeSearch,
                        onQueryChange = controller::setQuery,
                        onMove = controller::moveItem,
                        onOpen = controller::open,
                        onRename = controller::rename,
                        onEditDescription = controller::editDescription,
                        onDelete = controller::delete,
                        onExport = controller::export,
                        onCreate = controller::create,
                        onImport = controller::importDocuments,
                        onSave = { controller.save(); finishOk() },
                        onCancel = { finishCanceled() },
                        onNavigateUp = { onBackPressedDispatcher.onBackPressed() },
                        importNamePrompt = importNamePrompt,
                        onConfirmImport = ::confirmImport,
                        onDismissImport = ::dismissImport,
                        selection = selection,
                        onToggleSelected = controller::toggleSelect,
                        onClearSelection = controller::clearSelection,
                        onDeleteSelected = controller::deleteSelected,
                        onExportSelected = controller::exportSelected,
                    )
            }
        }
    }

    private suspend fun reload() {
        val docs = withContext(Dispatchers.IO) { dao.allDocuments() }
        entityByLong = docs.mapIndexed { i, d -> i.toLong() to d }.toMap()
        controller.setDocuments(docs.mapIndexed { i, d ->
            MyDocItem(
                id = i.toLong(),
                initials = d.initials,
                name = d.name,
                description = d.description ?: "",
                isAiGenerated = d.sourcePromptId != null,
                canDelete = MyDocumentBookManager.canDeleteDocument(d),
            )
        })
    }

    /** Next free stable Long id for a newly-added (create/import) document. */
    private fun nextLongId(): Long = (entityByLong.keys.maxOrNull() ?: -1L) + 1L

    /**
     * Mirror of classic [MyDocumentsActivity.applyChanges]: collect deleted docs' page ids →
     * unregister + delete each doc; then apply the current view-data order + renames/descriptions
     * onto the surviving entities and persist changed rows.
     *
     * [ordered] is the controller's FULL working list. It must not be replaced by a read of
     * `controller.documents.value`, which is the *filtered* publish: a save while a search is
     * active (leaving the screen, Save, opening a row, or the auto-save in
     * [onDetachedFromWindow]) would then renumber only the visible rows and drop every hidden
     * row's rename. The seam hands over items rather than ids precisely so that read is impossible.
     */
    private fun applyChanges(ordered: List<MyDocItem>, changed: Set<Long>, deleted: Set<Long>) {
        resultIntent.putExtra("changed", true)
        // Collect page IDs before deletion (CASCADE will remove them).
        val deletedPageIds = deleted.mapNotNull { entityByLong[it] }.flatMap { doc ->
            dao.pagesForDocument(doc.id).map { it.id }
        }
        // Delete documents — use documentById to get the current DB state.
        deleted.mapNotNull { entityByLong[it] }.forEach { doc ->
            dao.documentById(doc.id)?.let { fresh ->
                MyDocumentBookManager.unregisterDocument(fresh.initials)
                dao.delete(fresh)
            }
        }
        // Apply order + renames/descriptions from the current view-data onto the entities.
        val toUpdate = ArrayList<MyDocument>()
        ordered.forEachIndexed { index, item ->
            val doc = entityByLong[item.id] ?: return@forEachIndexed
            doc.orderNumber = index
            doc.name = item.name
            doc.description = item.description.ifEmpty { null }
            if (item.id in changed) { doc.updatedAt = System.currentTimeMillis(); toUpdate.add(doc) }
        }
        if (toUpdate.isNotEmpty()) dao.updateDocuments(toUpdate)
        if (deletedPageIds.isNotEmpty()) ABEventBus.post(AiDocPagesChangedEvent(deletedPageIds = deletedPageIds))
    }

    /** Classic createNewDocument: insert + register a fresh empty document, then add it to the list. */
    private fun createDocument(name: String) {
        val initials = MyDocumentBookManager.generateInitials(name)
        // totalCount, not documents.value.size: the published list is filtered, so a create while a
        // search is active would seed a colliding orderNumber.
        val newDoc = MyDocument(name = name, initials = initials, orderNumber = controller.totalCount.value)
        dao.insert(newDoc)
        MyDocumentBookManager.registerDocument(newDoc)
        val id = nextLongId()
        entityByLong = entityByLong + (id to newDoc)
        controller.addDocument(MyDocItem(id, initials, name, "", isAiGenerated = false, canDelete = true))
    }

    private fun openDocument(id: Long) {
        val doc = entityByLong[id] ?: return
        // Classic offers a save-changes prompt here; the auto-save-on-leave contract auto-saves instead.
        if (controller.dirty.value) controller.save()
        // Route the pages drill-down through ScreenLauncher so the use_compose_ui flag governs it too.
        val intent = Intent(this, ScreenLauncher.targetFor(Screen.MyDocumentPages))
            .putExtra("documentId", doc.id.toString())
            .putExtra("documentInitials", doc.initials)
            .putExtra("documentName", doc.name)
        pagesLauncher.launch(intent)
    }

    private val pagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val bookInitials = data?.getStringExtra("documentInitials")
            val pageKey = data?.getStringExtra("pageKey")
            if (bookInitials != null && pageKey != null) {
                resultIntent.putExtra("documentInitials", bookInitials)
                resultIntent.putExtra("pageKey", pageKey)
                finishOk()
            }
        }
    }

    private val importFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        // Parity with classic showImportNameDialog: don't import immediately — stash the URIs and open the
        // import name dialog pre-filled with the same default name; import runs on the user's confirm.
        if (!uris.isNullOrEmpty()) {
            pendingImportUris = uris
            // entityByLong mirrors the current document count (avoids referencing the by-lazy `controller`
            // here, which would create a property-init cycle with importFilesLauncher).
            importNamePrompt = getString(R.string.my_document_new_name, entityByLong.size + 1)
        }
    }

    /** Import name dialog confirmed: run the verbatim import with the user's chosen name, then clear pending state. */
    private fun confirmImport(name: String) {
        val uris = pendingImportUris
        importNamePrompt = null
        pendingImportUris = null
        if (uris != null) importFromFiles(name, uris)
    }

    /** Import name dialog dismissed: drop the pending URIs without importing. */
    private fun dismissImport() {
        importNamePrompt = null
        pendingImportUris = null
    }

    private val exportTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val id = pendingExportId; pendingExportId = null
        if (uri != null && id != null) entityByLong[id]?.let { exportDocumentToFolder(it, uri) }
    }

    private val exportBatchTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val ids = pendingExportIds; pendingExportIds = emptyList()
        if (uri != null && ids.isNotEmpty()) exportDocumentsToFolder(ids, uri)
    }

    /**
     * Export several documents into one chosen folder, each into its OWN subdirectory — two documents
     * can hold same-named pages, and a flat batch export would collide. Single-document export
     * ([exportDocumentToFolder]) deliberately keeps writing flat into the picked folder, so nothing
     * about the existing per-row action changes.
     */
    private fun exportDocumentsToFolder(ids: List<Long>, treeUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val treeDoc = DocumentFile.fromTreeUri(this@MyDocumentsComposeActivity, treeUri) ?: return@launch
                for (id in ids) {
                    val document = entityByLong[id] ?: continue
                    // Fetch first, and skip a page-less document BEFORE creating its subdirectory —
                    // otherwise a batch containing an empty document leaves a stray empty folder in
                    // the user's chosen directory (the single-document path returns early instead).
                    val pages = dao.pagesWithContentForDocument(document.id)
                    if (pages.isEmpty()) continue
                    val folderName = document.name
                        .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                        .take(50)
                        .ifEmpty { document.initials }
                    val subDir = treeDoc.createDirectory(folderName) ?: continue
                    writePagesInto(subDir, pages)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MyDocumentsComposeActivity,
                        R.string.my_document_export_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export documents", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MyDocumentsComposeActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** The per-page write loop, lifted verbatim out of [exportDocumentToFolder] so the batch path
     *  reuses it. Takes the already-fetched [pages] — both callers need to inspect the list anyway
     *  (to skip a page-less document), and querying it twice loaded every page's content twice.
     *  Runs on the caller's (IO) dispatcher. */
    private fun writePagesInto(dir: DocumentFile, pages: List<MyDocumentPageWithContent>) {
        for ((index, page) in pages.withIndex()) {
            val ext = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"
            val mimeType = if (ext == "html") "text/html" else "text/markdown"
            val orderPrefix = String.format("%02d", index + 1)
            val sanitizedTitle = page.title
                .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                .take(50)
                .ifEmpty { getString(R.string.my_document_export_fallback_name) }
            val entryName = "$orderPrefix-$sanitizedTitle.$ext"
            val file = dir.createFile(mimeType, entryName) ?: continue
            contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write((page.content ?: "").toByteArray(Charsets.UTF_8))
            }
        }
    }

    /**
     * Import selected text files as a new document under the user-entered [documentName]. Ported from
     * classic [MyDocumentsActivity.importDocumentFromFiles] (Dispatchers.IO body); the name comes from the
     * import name dialog (parity with classic). The classic `dataSet.add + notifyItemInserted` is replaced
     * by [MyDocumentsController.addDocument] on Main.
     */
    private fun importFromFiles(documentName: String, uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                data class FileEntry(val fileName: String, val content: String)

                val entries = uris.mapNotNull { uri ->
                    val fileName = getFileName(uri) ?: return@mapNotNull null
                    val content = contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: return@mapNotNull null
                    FileEntry(fileName, content)
                }.sortedBy { it.fileName }

                if (entries.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MyDocumentsComposeActivity,
                            R.string.my_document_import_empty_selection,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val initials = MyDocumentBookManager.generateInitials(documentName)
                val newDocument = MyDocument(
                    name = documentName,
                    initials = initials,
                    // totalCount, not documents.value.size — this runs on IO while the user can be
                    // typing in the search field, which would otherwise yield a filtered count.
                    orderNumber = controller.totalCount.value
                )
                dao.insert(newDocument)

                for ((index, entry) in entries.withIndex()) {
                    val contentType = when {
                        entry.fileName.endsWith(".html", true)
                            || entry.fileName.endsWith(".htm", true) -> MyDocumentContentType.HTML
                        else -> MyDocumentContentType.MARKDOWN
                    }
                    val rawName = entry.fileName.substringBeforeLast(".")
                    val title = rawName.replace(Regex("^\\d+-"), "").trim().ifEmpty { getString(R.string.my_document_new_page_name, index + 1) }

                    val pageId = IdType()
                    val page = MyDocumentPage(
                        id = pageId,
                        documentId = newDocument.id,
                        title = title,
                        pageKey = "page_$pageId",
                        contentType = contentType,
                        orderNumber = index
                    )
                    dao.insertPageWithContent(page, entry.content)
                }

                MyDocumentBookManager.registerDocument(newDocument)

                withContext(Dispatchers.Main) {
                    val id = nextLongId()
                    entityByLong = entityByLong + (id to newDocument)
                    controller.addDocument(
                        MyDocItem(id, initials, documentName, "", isAiGenerated = false, canDelete = true)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MyDocumentsComposeActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Export one document's pages to a chosen tree folder. Ported verbatim from classic
     * [MyDocumentsActivity.exportDocumentToFolder] (Dispatchers.IO body); the per-page write loop
     * itself is shared with the batch path via [writePagesInto]. */
    private fun exportDocumentToFolder(document: MyDocument, treeUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pages = dao.pagesWithContentForDocument(document.id)
                if (pages.isEmpty()) return@launch

                val treeDoc = DocumentFile.fromTreeUri(this@MyDocumentsComposeActivity, treeUri)
                    ?: return@launch

                writePagesInto(treeDoc, pages)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MyDocumentsComposeActivity,
                        R.string.my_document_export_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export document", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MyDocumentsComposeActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Resolve a display file name for a content Uri. Ported verbatim from classic MyDocumentsActivity. */
    private fun getFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    private fun finishOk() { setResult(Activity.RESULT_OK, resultIntent); finished = true; finish() }
    private fun finishCanceled() { setResult(Activity.RESULT_CANCELED, resultIntent); finished = true; finish() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back dismisses what is visually on top: the selection bar covers the search bar
        // (AbSelectionScaffold's precedence), so selection goes first. Closing search underneath a
        // visible selection bar would clear the query and re-filter the list invisibly.
        if (controller.selection.value.isNotEmpty()) { controller.clearSelection(); return }
        if (controller.searchModeActive.value) { controller.closeSearch(); return }
        super.onBackPressed()
    }

    override fun onDetachedFromWindow() {
        if (!finished && controller.dirty.value) controller.save()   // classic auto-save-on-leave
        super.onDetachedFromWindow()
    }

    companion object { private const val TAG = "MyDocumentsCompose" }
}
