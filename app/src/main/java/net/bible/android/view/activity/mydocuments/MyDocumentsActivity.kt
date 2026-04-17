/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.mydocuments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.MyDocumentListItemBinding
import net.bible.android.activity.databinding.MyDocumentsSelectorBinding
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.android.control.event.ABEventBus
import net.bible.service.sword.mydocument.AiDocPagesChangedEvent
import net.bible.service.sword.mydocument.MyDocumentBookManager

private const val TAG = "MyDocumentsActivity"

class MyDocumentViewHolder(val binding: MyDocumentListItemBinding) : RecyclerView.ViewHolder(binding.root)

class MyDocumentAdapter(val activity: MyDocumentsActivity) : RecyclerView.Adapter<MyDocumentViewHolder>() {
    val items get() = activity.dataSet

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyDocumentViewHolder {
        val binding = MyDocumentListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyDocumentViewHolder(binding)
    }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun getItemCount() = items.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: MyDocumentViewHolder, position: Int) = holder.binding.run {
        val document = items[position]

        title.text = document.name
        summary.text = document.description ?: activity.getString(R.string.my_document_no_description)

        // Show AI icon if document was created by AI
        aiIcon.visibility = if (document.sourcePromptId != null) View.VISIBLE else View.GONE

        root.setOnClickListener {
            activity.openDocument(document)
        }
        root.setOnLongClickListener { true }
        dragHolder.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                activity.itemTouchHelper.startDrag(holder)
            }
            true
        }
        menuButton.setOnClickListener {
            activity.showPopupMenu(it, document)
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to) return

        val item = items[from]
        if (from < to)
            items.removeAt(from)

        items.add(to, item)
        if (from > to)
            items.removeAt(from + 1)

        for ((idx, doc) in items.withIndex()) {
            if (doc.orderNumber != idx) {
                activity.changedDocuments.add(doc.id)
                doc.orderNumber = idx
            }
        }
        notifyItemMoved(from, to)
    }
}

@ActivityScope
class MyDocumentsActivity : ActivityBase() {
    private var finished = false
    private var isDirty: Boolean = false
    private val documentsToBeDeleted = HashSet<IdType>()
    private lateinit var resultIntent: Intent
    internal lateinit var dataSet: MutableList<MyDocument>
    private lateinit var documentAdapter: MyDocumentAdapter
    private lateinit var binding: MyDocumentsSelectorBinding

    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.my_documents_options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when (item.itemId) {
            R.id.newItem -> createNewDocument()
            R.id.importDocument -> importFilesLauncher.launch(arrayOf("text/*"))
            android.R.id.home -> onBackPressed()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    val itemTouchHelper by lazy {
        val cb = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val adapter = recyclerView.adapter as MyDocumentAdapter
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not implemented
            }
        }
        ItemTouchHelper(cb)
    }

    fun setDirty() {
        isDirty = true
        resultIntent.putExtra("changed", true)
        binding.save.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.buildActivityComponent().inject(this)
        resultIntent = Intent(this, this::class.java)
        binding = MyDocumentsSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        documentAdapter = MyDocumentAdapter(this).apply {
            setHasStableIds(true)
            registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    setDirty()
                }

                override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                    if (payload == null) {
                        setDirty()
                    }
                }

                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    setDirty()
                }

                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                    setDirty()
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    setDirty()
                }
            })
        }

        dataSet = dao.allDocuments().toMutableList()

        val llm = LinearLayoutManager(this)
        binding.run {
            recyclerView.apply {
                layoutManager = llm
                setHasFixedSize(true)
            }
            itemTouchHelper.attachToRecyclerView(recyclerView)

            cancel.setOnClickListener {
                finishCanceled()
            }

            save.setOnClickListener {
                applyChanges()
                finishOk()
            }
            recyclerView.adapter = documentAdapter

            emptyView.visibility = if (dataSet.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun createNewDocument() {
        val nameEdit = EditText(this)
        nameEdit.text = SpannableStringBuilder(getString(R.string.my_document_new_name, dataSet.size + 1))

        val dialog = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay) { _, _ ->
                val name = nameEdit.text.toString().trim()
                if (name.isNotEmpty()) {
                    val initials = MyDocumentBookManager.generateInitials(name)
                    val newDocument = MyDocument(
                        name = name,
                        initials = initials,
                        orderNumber = dataSet.size
                    )
                    dao.insert(newDocument)
                    MyDocumentBookManager.registerDocument(newDocument)
                    dataSet.add(newDocument)
                    documentAdapter.notifyItemInserted(dataSet.size - 1)
                    binding.emptyView.visibility = View.GONE
                }
            }
            .setView(nameEdit)
            .setNegativeButton(R.string.cancel, null)
            .setTitle(getString(R.string.my_document_create_title))
            .create()

        nameEdit.selectAll()
        nameEdit.requestFocus()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun finishOk() {
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
        finished = true
    }

    private fun finishCanceled() {
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
        finished = true
    }

    private fun applyChanges() {
        // Collect page IDs before deletion (CASCADE will remove them)
        val deletedPageIds = documentsToBeDeleted.flatMap { id ->
            dao.pagesForDocument(id).map { it.id }
        }

        // Delete documents — use documentById to get the current DB state,
        // since the dataSet list may have already removed the document
        documentsToBeDeleted.forEach { id ->
            val doc = dao.documentById(id) ?: return@forEach
            MyDocumentBookManager.unregisterDocument(doc.initials)
            dao.delete(doc)
        }

        // Update changed documents
        dao.updateDocuments(dataSet.filter { changedDocuments.contains(it.id) })

        if (deletedPageIds.isNotEmpty()) {
            ABEventBus.post(AiDocPagesChangedEvent(deletedPageIds = deletedPageIds))
        }
    }

    private fun handleMenuItem(item: MenuItem?, document: MyDocument): Boolean {
        val position = dataSet.indexOf(document)

        when (item?.itemId) {
            R.id.deleteDocument -> {
                if (!MyDocumentBookManager.canDeleteDocument(document)) {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.my_document_cannot_delete_ai_documents)
                        .setPositiveButton(R.string.okay, null)
                        .create()
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.my_document_delete_confirmation, document.name))
                        .setPositiveButton(R.string.yes) { _, _ ->
                            documentsToBeDeleted.add(document.id)
                            dataSet.removeAt(position)
                            documentAdapter.notifyItemRemoved(position)
                            binding.emptyView.visibility = if (dataSet.isEmpty()) View.VISIBLE else View.GONE
                            setDirty()
                        }
                        .setNegativeButton(R.string.no, null)
                        .create()
                        .show()
                }
            }
            R.id.renameDocument -> {
                val nameEdit = EditText(this)
                nameEdit.text = SpannableStringBuilder(document.name)
                nameEdit.selectAll()
                nameEdit.requestFocus()
                AlertDialog.Builder(this)
                    .setPositiveButton(R.string.okay) { _, _ ->
                        document.name = nameEdit.text.toString().trim()
                        document.updatedAt = System.currentTimeMillis()
                        changedDocuments.add(document.id)
                        documentAdapter.notifyItemChanged(position)
                    }
                    .setView(nameEdit)
                    .setNegativeButton(R.string.cancel, null)
                    .setTitle(getString(R.string.my_document_rename_title))
                    .create()
                    .show()
            }
            R.id.exportDocument -> {
                pendingExportDocument = document
                exportTreeLauncher.launch(null)
            }
            R.id.editDescription -> {
                val descEdit = EditText(this)
                descEdit.text = SpannableStringBuilder(document.description ?: "")
                descEdit.requestFocus()
                AlertDialog.Builder(this)
                    .setPositiveButton(R.string.okay) { _, _ ->
                        document.description = descEdit.text.toString().trim().ifEmpty { null }
                        document.updatedAt = System.currentTimeMillis()
                        changedDocuments.add(document.id)
                        documentAdapter.notifyItemChanged(position)
                    }
                    .setView(descEdit)
                    .setNegativeButton(R.string.cancel, null)
                    .setTitle(getString(R.string.my_document_edit_description))
                    .create()
                    .show()
            }
        }
        return false
    }

    override fun onBackPressed() {
        if (isDirty) {
            AlertDialog.Builder(this)
                .setMessage(R.string.my_document_save_changes)
                .setPositiveButton(R.string.yes) { _, _ ->
                    applyChanges()
                    finishOk()
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    finishCanceled()
                }
                .setNeutralButton(R.string.cancel, null)
                .create()
                .show()
        } else {
            finishCanceled()
            super.onBackPressed()
        }
    }

    override fun onDetachedFromWindow() {
        if (!finished && isDirty) {
            // Auto-save on unexpected close
            applyChanges()
        }
        super.onDetachedFromWindow()
    }

    internal val changedDocuments = mutableSetOf<IdType>()

    private val pagesActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val bookInitials = data?.getStringExtra("documentInitials")
            val pageKey = data?.getStringExtra("pageKey")
            if (bookInitials != null && pageKey != null) {
                resultIntent.putExtra("documentInitials", bookInitials)
                resultIntent.putExtra("pageKey", pageKey)
                finishOk()
                return@registerForActivityResult
            }
        }
    }

    fun openDocument(document: MyDocument) {
        if (isDirty) {
            AlertDialog.Builder(this)
                .setMessage(R.string.my_document_save_changes)
                .setPositiveButton(R.string.yes) { _, _ ->
                    applyChanges()
                    launchPagesActivity(document)
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    launchPagesActivity(document)
                }
                .setNeutralButton(R.string.cancel, null)
                .create()
                .show()
        } else {
            launchPagesActivity(document)
        }
    }

    private fun launchPagesActivity(document: MyDocument) {
        val intent = Intent(this, MyDocumentPagesActivity::class.java)
        intent.putExtra("documentId", document.id.toString())
        intent.putExtra("documentInitials", document.initials)
        intent.putExtra("documentName", document.name)
        pagesActivityLauncher.launch(intent)
    }

    private var pendingExportDocument: MyDocument? = null

    private val exportTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val document = pendingExportDocument ?: return@registerForActivityResult
        pendingExportDocument = null
        exportDocumentToFolder(document, uri)
    }

    private val importFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        showImportNameDialog(uris)
    }

    private fun exportDocumentToFolder(document: MyDocument, treeUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pages = dao.pagesWithContentForDocument(document.id)
                if (pages.isEmpty()) return@launch

                val treeDoc = DocumentFile.fromTreeUri(this@MyDocumentsActivity, treeUri)
                    ?: return@launch

                for ((index, page) in pages.withIndex()) {
                    val ext = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"
                    val mimeType = if (ext == "html") "text/html" else "text/markdown"
                    val orderPrefix = String.format("%02d", index + 1)
                    val sanitizedTitle = page.title
                        .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                        .take(50)
                        .ifEmpty { getString(R.string.my_document_export_fallback_name) }
                    val entryName = "$orderPrefix-$sanitizedTitle.$ext"

                    val file = treeDoc.createFile(mimeType, entryName) ?: continue
                    contentResolver.openOutputStream(file.uri)?.use { out ->
                        out.write((page.content ?: "").toByteArray(Charsets.UTF_8))
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MyDocumentsActivity,
                        R.string.my_document_export_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export document", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MyDocumentsActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showImportNameDialog(uris: List<Uri>) {
        val nameEdit = EditText(this)
        nameEdit.text = SpannableStringBuilder(getString(R.string.my_document_new_name, dataSet.size + 1))

        val dialog = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay) { _, _ ->
                val name = nameEdit.text.toString().trim()
                if (name.isNotEmpty()) {
                    importDocumentFromFiles(name, uris)
                }
            }
            .setView(nameEdit)
            .setNegativeButton(R.string.cancel, null)
            .setTitle(getString(R.string.my_document_create_title))
            .create()

        nameEdit.selectAll()
        nameEdit.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun importDocumentFromFiles(documentName: String, uris: List<Uri>) {
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
                            this@MyDocumentsActivity,
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
                    orderNumber = dataSet.size
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
                    dataSet.add(newDocument)
                    documentAdapter.notifyItemInserted(dataSet.size - 1)
                    binding.emptyView.visibility = View.GONE
                    setDirty()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MyDocumentsActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    fun showPopupMenu(view: View, document: MyDocument) {
        val popup = PopupMenu(this, view)
        popup.setOnMenuItemClickListener {
            handleMenuItem(it, document)
        }
        popup.menuInflater.inflate(R.menu.my_document_popup_menu, popup.menu)
        popup.show()
    }
}
