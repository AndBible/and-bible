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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.activity.databinding.MyDocumentPageListItemBinding
import net.bible.android.activity.databinding.MyDocumentPagesSelectorBinding
import net.bible.android.control.backup.BackupControl
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageContent
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.android.control.event.ABEventBus
import net.bible.service.sword.mydocument.AiDocPagesChangedEvent
import net.bible.service.sword.mydocument.MyDocumentBookManager
import java.io.File

private const val TAG = "MyDocPagesActivity"

class MyDocumentPageViewHolder(val binding: MyDocumentPageListItemBinding) : RecyclerView.ViewHolder(binding.root)

class MyDocumentPageAdapter(val activity: MyDocumentPagesActivity) : RecyclerView.Adapter<MyDocumentPageViewHolder>() {
    val items get() = activity.dataSet

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyDocumentPageViewHolder {
        val binding = MyDocumentPageListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyDocumentPageViewHolder(binding)
    }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun getItemCount() = items.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: MyDocumentPageViewHolder, position: Int) = holder.binding.run {
        val page = items[position]

        title.text = page.title
        subtitle.text = page.contentType.name

        aiIcon.visibility = if (page.sourcePromptId != null) View.VISIBLE else View.GONE

        root.setOnClickListener {
            activity.openPage(page)
        }
        root.setOnLongClickListener { true }
        dragHolder.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                activity.itemTouchHelper.startDrag(holder)
            }
            true
        }
        menuButton.setOnClickListener {
            activity.showPopupMenu(it, page)
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

        for ((idx, page) in items.withIndex()) {
            if (page.orderNumber != idx) {
                activity.changedPages.add(page.id)
                page.orderNumber = idx
            }
        }
        notifyItemMoved(from, to)
    }
}

@ActivityScope
class MyDocumentPagesActivity : ActivityBase() {
    private var finished = false
    private var isDirty: Boolean = false
    private val pagesToBeDeleted = HashSet<IdType>()
    private lateinit var resultIntent: Intent
    internal lateinit var dataSet: MutableList<MyDocumentPage>
    private lateinit var pageAdapter: MyDocumentPageAdapter
    private lateinit var binding: MyDocumentPagesSelectorBinding

    private lateinit var documentId: IdType
    private lateinit var documentInitials: String
    private var documentName: String = ""

    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    val itemTouchHelper by lazy {
        val cb = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val adapter = recyclerView.adapter as MyDocumentPageAdapter
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
        binding.save.isEnabled = true
    }

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        importFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.buildActivityComponent().inject(this)

        val docIdStr = intent.getStringExtra("documentId")
        documentInitials = intent.getStringExtra("documentInitials") ?: ""
        documentName = intent.getStringExtra("documentName") ?: ""

        if (docIdStr == null) {
            finish()
            return
        }
        documentId = IdType(docIdStr)

        resultIntent = Intent(this, this::class.java)
        binding = MyDocumentPagesSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.my_document_pages_title, documentName)

        pageAdapter = MyDocumentPageAdapter(this).apply {
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

        dataSet = dao.pagesForDocument(documentId).toMutableList()

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
            recyclerView.adapter = pageAdapter

            emptyView.visibility = if (dataSet.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.my_document_pages_options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.newPage -> {
                createNewPage()
                return true
            }
            R.id.importPage -> {
                importFileLauncher.launch(arrayOf("text/*"))
                return true
            }
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createNewPage() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val nameEdit = EditText(this)
        nameEdit.text = SpannableStringBuilder(getString(R.string.my_document_new_page_name, dataSet.size + 1))

        val typeLabel = TextView(this).apply {
            text = getString(R.string.my_document_content_type_label)
            setPadding(0, 16, 0, 4)
        }

        val typeSpinner = Spinner(this)
        val contentTypes = arrayOf("MARKDOWN", "HTML")
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, contentTypes)

        layout.addView(nameEdit)
        layout.addView(typeLabel)
        layout.addView(typeSpinner)

        nameEdit.selectAll()
        nameEdit.requestFocus()

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.my_document_create_page_title))
            .setView(layout)
            .setPositiveButton(R.string.okay) { _, _ ->
                val title = nameEdit.text.toString().trim()
                if (title.isNotEmpty()) {
                    val contentType = if (typeSpinner.selectedItemPosition == 0)
                        MyDocumentContentType.MARKDOWN else MyDocumentContentType.HTML
                    addPageToList(title, contentType, "")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun addPageToList(title: String, contentType: MyDocumentContentType, content: String) {
        val pageId = IdType()
        val page = MyDocumentPage(
            id = pageId,
            documentId = documentId,
            title = title,
            pageKey = "page_$pageId",
            contentType = contentType,
            orderNumber = dataSet.size
        )
        dao.insertPageWithContent(page, content)
        dataSet.add(page)
        pageAdapter.notifyItemInserted(dataSet.size - 1)
        binding.emptyView.visibility = View.GONE
        setDirty()
    }

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
            android.widget.Toast.makeText(this, R.string.error_occurred, android.widget.Toast.LENGTH_SHORT).show()
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

    private fun finishOk() {
        setResult(RESULT_OK, resultIntent)
        finish()
        finished = true
    }

    private fun finishCanceled() {
        setResult(RESULT_CANCELED, resultIntent)
        finish()
        finished = true
    }

    private fun applyChanges() {
        var anyChanges = false

        // Delete pages
        pagesToBeDeleted.forEach { id ->
            val page = dao.pageById(id)
            if (page != null) {
                dao.deletePageWithContent(page)
                anyChanges = true
            }
        }

        // Update changed pages
        val changedPagesList = dataSet.filter { changedPages.contains(it.id) }
        if (changedPagesList.isNotEmpty()) {
            dao.updatePages(changedPagesList)
            anyChanges = true
        }

        // Always refresh SWORD book when dirty — new pages are inserted directly to DB
        // in addPageToList() without going through changedPages tracking, so anyChanges
        // would be false even though the SWORD book is stale.
        MyDocumentBookManager.refreshDocument(documentInitials)
        if (pagesToBeDeleted.isNotEmpty()) {
            ABEventBus.post(AiDocPagesChangedEvent(deletedPageIds = pagesToBeDeleted.toList()))
        }
    }

    private fun handleMenuItem(item: MenuItem?, page: MyDocumentPage): Boolean {
        val position = dataSet.indexOf(page)

        when (item?.itemId) {
            R.id.exportPage -> {
                exportPage(page)
            }
            R.id.deletePage -> {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.my_document_page_delete_confirmation, page.title))
                    .setPositiveButton(R.string.yes) { _, _ ->
                        pagesToBeDeleted.add(page.id)
                        dataSet.removeAt(position)
                        pageAdapter.notifyItemRemoved(position)
                        binding.emptyView.visibility = if (dataSet.isEmpty()) View.VISIBLE else View.GONE
                        setDirty()
                    }
                    .setNegativeButton(R.string.no, null)
                    .create()
                    .show()
            }
            R.id.renamePage -> {
                val nameEdit = EditText(this)
                nameEdit.text = SpannableStringBuilder(page.title)
                nameEdit.selectAll()
                nameEdit.requestFocus()
                val dialog = AlertDialog.Builder(this)
                    .setPositiveButton(R.string.okay) { _, _ ->
                        page.title = nameEdit.text.toString().trim()
                        page.updatedAt = System.currentTimeMillis()
                        changedPages.add(page.id)
                        pageAdapter.notifyItemChanged(position)
                    }
                    .setView(nameEdit)
                    .setNegativeButton(R.string.cancel, null)
                    .setTitle(getString(R.string.my_document_page_rename_title))
                    .create()

                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                dialog.show()
            }
        }
        return false
    }

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
                activity = this@MyDocumentPagesActivity,
                file = targetFile,
                fileName = fileName,
                shareMimeType = mimeType,
                saveMimeType = mimeType,
                chooserTitle = getString(R.string.my_document_export_page),
            )
        }
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
            applyChanges()
        }
        super.onDetachedFromWindow()
    }

    internal val changedPages = mutableSetOf<IdType>()

    fun openPage(page: MyDocumentPage) {
        if (isDirty) {
            AlertDialog.Builder(this)
                .setMessage(R.string.my_document_save_changes)
                .setPositiveButton(R.string.yes) { _, _ ->
                    applyChanges()
                    returnWithPage(page, refreshBook = false)
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    returnWithPage(page)
                }
                .setNeutralButton(R.string.cancel, null)
                .create()
                .show()
        } else {
            returnWithPage(page)
        }
    }

    /**
     * @param refreshBook rebuild the SWORD book's key map before returning.
     * Pages created via addPageToList() are written to the DB immediately, so
     * the book can be stale even when the user declined to save other edits —
     * and MainBibleActivity resolves the returned pageKey against that map.
     * Pass false when [applyChanges] has just refreshed it.
     */
    private fun returnWithPage(page: MyDocumentPage, refreshBook: Boolean = true) {
        if (refreshBook) {
            MyDocumentBookManager.refreshDocument(documentInitials)
        }
        resultIntent.putExtra("documentInitials", documentInitials)
        resultIntent.putExtra("pageKey", page.pageKey)
        finishOk()
    }

    fun showPopupMenu(view: View, page: MyDocumentPage) {
        val popup = PopupMenu(this, view)
        popup.setOnMenuItemClickListener {
            handleMenuItem(it, page)
        }
        popup.menuInflater.inflate(R.menu.my_document_page_popup_menu, popup.menu)
        popup.show()
    }
}
