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
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.MyDocumentPageListItemBinding
import net.bible.android.activity.databinding.MyDocumentPagesSelectorBinding
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.mydocument.MyDocumentBookManager

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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

        if (anyChanges) {
            MyDocumentBookManager.refreshDocument(documentInitials)
        }
    }

    private fun handleMenuItem(item: MenuItem?, page: MyDocumentPage): Boolean {
        val position = dataSet.indexOf(page)

        when (item?.itemId) {
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
                    returnWithPage(page)
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

    private fun returnWithPage(page: MyDocumentPage) {
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
