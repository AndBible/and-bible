/*
 * Copyright (c) 2020-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.activity.databinding.MyDocumentsSelectorBinding
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.mydocument.MyDocumentBookManager

private const val TAG = "MyDocumentsActivity"

class MyDocumentViewHolder(val layout: ViewGroup) : RecyclerView.ViewHolder(layout)

class MyDocumentAdapter(val activity: MyDocumentsActivity) : RecyclerView.Adapter<MyDocumentViewHolder>() {
    val items get() = activity.dataSet

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyDocumentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.my_document_list_item, parent, false) as ViewGroup
        return MyDocumentViewHolder(view)
    }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun getItemCount() = items.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: MyDocumentViewHolder, position: Int) {
        val dragHolder = holder.layout.findViewById<ImageView>(R.id.dragHolder)
        val title = holder.layout.findViewById<TextView>(R.id.title)
        val summary = holder.layout.findViewById<TextView>(R.id.summary)
        val menuButton = holder.layout.findViewById<ImageButton>(R.id.menuButton)
        val aiIcon = holder.layout.findViewById<ImageView>(R.id.aiIcon)
        val layout = holder.layout
        val document = items[position]

        title.text = document.name
        summary.text = document.description ?: activity.getString(R.string.my_document_no_description)

        // Show AI icon if document was created by AI
        aiIcon.visibility = if (document.sourcePromptId != null) View.VISIBLE else View.GONE

        layout.setOnClickListener {
            activity.openDocument(document)
        }
        layout.setOnLongClickListener { true }
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
            R.id.createDemoDocument -> createDemoDocument()
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

    /**
     * Debug function: Create a demo document with sample markdown content
     */
    private fun createDemoDocument() {
        val name = "Demo Document ${dataSet.size + 1}"
        val initials = MyDocumentBookManager.generateInitials(name)
        val newDocument = MyDocument(
            name = name,
            description = "A demo document with sample markdown content",
            initials = initials,
            orderNumber = dataSet.size
        )
        dao.insert(newDocument)

        // Create sample pages with markdown content
        val pages = listOf(
            Triple("intro", "Introduction", """
# Welcome to My Documents

This is a **demo document** created for testing the My Documents feature.

## Features

- Supports **bold** and *italic* text
- Lists (like this one)
- [Links to Bible references](sword://KJV/John.3.16)
- Code blocks and more

## Bible Links

You can link to Bible verses:
- [John 3:16](sword://KJV/John.3.16)
- [Psalm 23](sword://KJV/Ps.23)
- [Romans 8:28](sword://KJV/Rom.8.28)

---

Navigate using the table of contents on the left.
            """.trimIndent()),

            Triple("theology", "Theological Notes", """
# Theological Notes

## The Trinity

The doctrine of the Trinity teaches that:

1. There is one God
2. The Father is God
3. The Son is God
4. The Holy Spirit is God
5. The Father, Son, and Holy Spirit are distinct persons

### Key Verses

> "Go therefore and make disciples of all nations, baptizing them in the name of the Father and of the Son and of the Holy Spirit"
> — [Matthew 28:19](sword://KJV/Matt.28.19)

## Salvation by Grace

| Concept | Description |
|---------|-------------|
| Grace | Unmerited favor from God |
| Faith | Trust in Christ alone |
| Works | Result of salvation, not cause |

See [Ephesians 2:8-9](sword://KJV/Eph.2.8-9) for the key passage.
            """.trimIndent()),

            Triple("study", "Study Methods", """
# Bible Study Methods

## Inductive Bible Study

The inductive method involves three steps:

### 1. Observation
*What does the text say?*

- Read the passage multiple times
- Note key words and phrases
- Identify the literary genre

### 2. Interpretation
*What does the text mean?*

```
Context → Grammar → Word Study → Cross-references
```

### 3. Application
*How does this apply to my life?*

Ask yourself:
- Is there a command to obey?
- Is there a promise to claim?
- Is there an example to follow?

---

**Remember:** Always let Scripture interpret Scripture!
            """.trimIndent())
        )

        pages.forEachIndexed { index, (key, title, content) ->
            val page = MyDocumentPage(
                documentId = newDocument.id,
                title = title,
                pageKey = key,
                contentType = MyDocumentContentType.MARKDOWN,
                orderNumber = index
            )
            dao.insertPageWithContent(page, content)
        }

        // Register the document with JSword
        MyDocumentBookManager.registerDocument(newDocument)

        // Update UI
        dataSet.add(newDocument)
        documentAdapter.notifyItemInserted(dataSet.size - 1)
        binding.emptyView.visibility = View.GONE

        Log.i(TAG, "Created demo document: ${newDocument.initials} with ${pages.size} pages")
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
        // Delete documents
        documentsToBeDeleted.forEach { id ->
            val doc = dao.documentById(id)
            if (doc != null) {
                MyDocumentBookManager.unregisterDocument(doc.initials)
                dao.delete(doc)
            }
        }

        // Update changed documents
        dao.updateDocuments(dataSet.filter { changedDocuments.contains(it.id) })
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
                    .setTitle(getString(R.string.my_document_edit_description_title))
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

    fun openDocument(document: MyDocument) {
        // Return the document initials to open it
        if (isDirty) {
            AlertDialog.Builder(this)
                .setMessage(R.string.my_document_save_changes)
                .setPositiveButton(R.string.yes) { _, _ ->
                    applyChanges()
                    returnWithDocument(document)
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    returnWithDocument(document)
                }
                .setNeutralButton(R.string.cancel, null)
                .create()
                .show()
        } else {
            returnWithDocument(document)
        }
    }

    private fun returnWithDocument(document: MyDocument) {
        resultIntent.putExtra("documentInitials", document.initials)
        finishOk()
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
