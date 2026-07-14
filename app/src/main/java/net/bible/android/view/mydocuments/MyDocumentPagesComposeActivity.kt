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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.mydocuments.MyDocumentPagesActivity
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.mydocument.AiDocPagesChangedEvent
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedcore.mydocuments.MyDocumentPagesController
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.mydocuments.MyDocumentPagesScreen
import net.bible.sharedui.theme.AbTheme
import java.io.File

/**
 * Compose host for the pages-within-a-document editor — the new-path twin of classic
 * [MyDocumentPagesActivity], keyed on a single [documentId] read from the intent (finish() early if
 * missing, exactly like classic). Loads Room rows off-main, assigns STABLE load-index Long ids
 * (survive reorder) with an [entityByLong] map back to the entities, and drives the shared
 * [MyDocumentPagesController]/[MyDocumentPagesScreen]. All Room/SWORD/SAF/EventBus side effects are
 * host-side.
 *
 * Result-className parity (correctness-critical): results are built as
 * `Intent(this, MyDocumentPagesActivity::class.java)` with `documentInitials`/`pageKey` extras, so
 * `MainBibleActivity.onActivityResult`'s className dispatch (~line 1943) is untouched.
 */
class MyDocumentPagesComposeActivity : ActivityBase() {
    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    private lateinit var documentId: IdType
    private var documentInitials: String = ""
    private var documentName: String = ""

    /** Stable load-index Long id -> the backing Room entity, rebuilt on every (re)load + create/import. */
    private var entityByLong: Map<Long, MyDocumentPage> = emptyMap()
    private lateinit var resultIntent: Intent
    private var finished = false

    private val controller by lazy {
        MyDocumentPagesController(
            onOpenPage = ::openPage,
            onImport = { importFileLauncher.launch(arrayOf("text/*")) },
            onExport = { id -> entityByLong[id]?.let { exportPage(it) } },
            onCreatePage = ::createPage,
            onSave = ::applyChanges,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val docIdStr = intent.getStringExtra("documentId")
        documentInitials = intent.getStringExtra("documentInitials") ?: ""
        documentName = intent.getStringExtra("documentName") ?: ""
        if (docIdStr == null) { finish(); return }
        documentId = IdType(docIdStr)

        resultIntent = Intent(this, MyDocumentPagesActivity::class.java)
        val title = getString(R.string.my_document_pages_title, documentName)
        lifecycleScope.launch { reload() }
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = net.bible.service.device.ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val pages by controller.pages.collectAsState()
                    val dirty by controller.dirty.collectAsState()
                    MyDocumentPagesScreen(
                        title = title,
                        pages = pages,
                        dirty = dirty,
                        onMove = controller::moveItem,
                        onOpen = controller::openPage,
                        onRename = controller::rename,
                        onDelete = controller::delete,
                        onExport = controller::export,
                        onCreate = controller::createPage,
                        onImport = controller::importPage,
                        onSave = { controller.save(); finishOk() },
                        onCancel = { finishCanceled() },
                        onNavigateUp = { onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
    }

    private suspend fun reload() {
        val list = withContext(Dispatchers.IO) { dao.pagesForDocument(documentId) }
        entityByLong = list.mapIndexed { i, p -> i.toLong() to p }.toMap()
        controller.setPages(list.mapIndexed { i, p ->
            MyDocPageItem(
                id = i.toLong(),
                name = p.title,
                contentType = if (p.contentType == MyDocumentContentType.HTML) ContentType.HTML else ContentType.MARKDOWN,
                isAiGenerated = p.sourcePromptId != null,
            )
        })
    }

    /**
     * Mirror of classic [MyDocumentPagesActivity.applyChanges]: delete removed pages, apply the current
     * view-data order + renames onto the surviving entities and persist changed rows; always
     * refresh the SWORD book (new pages are inserted directly, so `changed` may be empty even when
     * the book is stale), and post [AiDocPagesChangedEvent] for the deleted pages.
     */
    private fun applyChanges(orderedIds: List<Long>, changed: Set<Long>, deleted: Set<Long>) {
        deleted.mapNotNull { entityByLong[it] }.forEach { p ->
            dao.pageById(p.id)?.let { dao.deletePageWithContent(it) }
        }
        val items = controller.pages.value
        val toUpdate = ArrayList<MyDocumentPage>()
        items.forEachIndexed { index, item ->
            val p = entityByLong[item.id] ?: return@forEachIndexed
            p.orderNumber = index
            p.title = item.name
            if (item.id in changed) { p.updatedAt = System.currentTimeMillis(); toUpdate.add(p) }
        }
        if (toUpdate.isNotEmpty()) dao.updatePages(toUpdate)
        // Classic always refreshes: new pages are inserted directly to the DB in addPageToList()
        // without going through `changed`, so the SWORD book would otherwise be stale.
        MyDocumentBookManager.refreshDocument(documentInitials)
        val deletedIds = deleted.mapNotNull { entityByLong[it]?.id }
        if (deletedIds.isNotEmpty()) ABEventBus.post(AiDocPagesChangedEvent(deletedPageIds = deletedIds))
    }

    /** Next free stable Long id for a newly-added (create/import) page. */
    private fun nextLongId(): Long = (entityByLong.keys.maxOrNull() ?: -1L) + 1L

    private fun createPage(name: String, type: ContentType) {
        val ct = if (type == ContentType.HTML) MyDocumentContentType.HTML else MyDocumentContentType.MARKDOWN
        addPageToList(name, ct, "")
    }

    /**
     * Insert a new page (with [content]) and add it to the view-data, replacing classic's
     * `dataSet.add + notifyItemInserted`. Shared by [createPage] and [importFile].
     */
    private fun addPageToList(title: String, contentType: MyDocumentContentType, content: String) {
        val pageId = IdType()
        val page = MyDocumentPage(
            id = pageId,
            documentId = documentId,
            title = title,
            pageKey = "page_$pageId",
            contentType = contentType,
            orderNumber = controller.pages.value.size,
        )
        dao.insertPageWithContent(page, content)
        val id = nextLongId()
        entityByLong = entityByLong + (id to page)
        val ct = if (contentType == MyDocumentContentType.HTML) ContentType.HTML else ContentType.MARKDOWN
        controller.addPage(MyDocPageItem(id, title, ct, isAiGenerated = false))
    }

    private fun openPage(id: Long) {
        val page = entityByLong[id] ?: return
        // Classic offers a save-changes prompt here; the auto-save-on-leave contract auto-saves instead.
        if (controller.dirty.value) controller.save()
        resultIntent.putExtra("documentInitials", documentInitials)
        resultIntent.putExtra("pageKey", page.pageKey)
        finishOk()
    }

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        importFile(uri)
    }

    /** Import a single text file as a new page. Ported verbatim from classic
     * [MyDocumentPagesActivity.importFile] (:326), replacing `addPageToList` with the host helper. */
    private fun importFile(uri: Uri) {
        try {
            val fileName = getFileName(uri) ?: getString(R.string.my_document_imported_page_name)
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return

            val contentType = when {
                fileName.endsWith(".html", ignoreCase = true) || fileName.endsWith(".htm", ignoreCase = true) ->
                    MyDocumentContentType.HTML
                else -> MyDocumentContentType.MARKDOWN
            }

            val title = fileName.substringBeforeLast(".")
            addPageToList(title, contentType, content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import file", e)
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
        }
    }

    /** Export one page via [BackupControl.saveOrShare]. Ported verbatim from classic
     * [MyDocumentPagesActivity.exportPage] (:440). */
    private fun exportPage(page: MyDocumentPage) {
        lifecycleScope.launch(Dispatchers.IO) {
            val pageWithContent = dao.pageByIdWithContent(page.id) ?: return@launch
            val ext = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"
            val sanitizedTitle = page.title.replace(Regex("[^a-zA-Z0-9._\\- ]"), "").take(50).ifEmpty { getString(R.string.my_document_export_fallback_name) }
            val fileName = "$sanitizedTitle.$ext"
            val targetDir = File(SharedConstants.internalFilesDir, "export/")
            targetDir.mkdirs()
            val targetFile = File(targetDir, fileName)
            targetFile.writeText(pageWithContent.content ?: "")
            val mimeType = if (ext == "html") "text/html" else "text/markdown"
            BackupControl.saveOrShare(
                activity = this@MyDocumentPagesComposeActivity,
                file = targetFile,
                fileName = fileName,
                shareMimeType = mimeType,
                saveMimeType = mimeType,
                chooserTitle = getString(R.string.my_document_export_page),
            )
        }
    }

    /** Resolve a display file name for a content Uri. Ported verbatim from classic
     * [MyDocumentPagesActivity.getFileName] (:345). */
    private fun getFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    private fun finishOk() { setResult(RESULT_OK, resultIntent); finished = true; finish() }
    private fun finishCanceled() { setResult(RESULT_CANCELED, resultIntent); finished = true; finish() }

    override fun onDetachedFromWindow() {
        if (!finished && controller.dirty.value) controller.save()   // classic auto-save-on-leave
        super.onDetachedFromWindow()
    }

    companion object { private const val TAG = "MyDocPagesCompose" }
}
