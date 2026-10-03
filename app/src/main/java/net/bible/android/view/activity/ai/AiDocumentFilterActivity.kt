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

package net.bible.android.view.activity.ai

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityPromptToolPermissionsBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books

/**
 * Activity for managing which documents the AI can access.
 *
 * Uses a blacklist approach: checked = allowed (default), unchecked = excluded.
 * Documents are grouped by category (Bible, Commentary, Dictionary, General Book).
 */
class AiDocumentFilterActivity : ActivityBase() {

    private data class DocumentRow(val initials: String, val checkBox: CheckBox)

    private val documentRows = mutableListOf<DocumentRow>()
    private lateinit var binding: ActivityPromptToolPermissionsBinding
    private lateinit var initialExcluded: Set<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromptToolPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.ai_document_filter_activity_title)

        initialExcluded = CommonUtils.aiSettings.aiExcludedDocuments

        val allBooks = Books.installed().books
        val categories = listOf(
            BookCategory.BIBLE,
            BookCategory.COMMENTARY,
            BookCategory.DICTIONARY,
            BookCategory.GENERAL_BOOK,
        )

        for (category in categories) {
            val booksInCategory = allBooks
                .filter { it.bookCategory == category }
                .sortedBy { it.initials }
            if (booksInCategory.isEmpty()) continue

            addSectionHeader(category.name)
            for (book in booksInCategory) {
                addDocumentRow(book.initials, "${book.initials} — ${book.name}")
            }
        }
    }

    private fun addSectionHeader(text: String) {
        val density = resources.displayMetrics.density
        val header = TextView(this).apply {
            this.text = text
            setTextAppearance(android.R.style.TextAppearance_Medium)
            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (4 * density).toInt()
            )
        }
        binding.toolListContainer.addView(header)
    }

    private fun addDocumentRow(initials: String, label: String) {
        val density = resources.displayMetrics.density
        val checkBox = CheckBox(this).apply {
            text = label
            isChecked = initials !in initialExcluded
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(
                (16 * density).toInt(),
                (4 * density).toInt(),
                (16 * density).toInt(),
                (4 * density).toInt()
            )
            layoutParams = params
        }
        binding.toolListContainer.addView(checkBox)
        documentRows.add(DocumentRow(initials, checkBox))
    }

    private fun collectExcluded(): Set<String> =
        documentRows
            .filter { !it.checkBox.isChecked }
            .map { it.initials }
            .toSet()

    private fun isDirty(): Boolean = collectExcluded() != initialExcluded

    private fun saveAndFinish() {
        CommonUtils.aiSettings.aiExcludedDocuments = collectExcluded()
        finish()
    }

    private fun cancelOrConfirmDiscard() {
        if (isDirty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.discard_changes_confirmation)
                .setPositiveButton(R.string.yes) { _, _ -> finish() }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            finish()
        }
    }

    private fun resetAll() {
        for (row in documentRows) {
            row.checkBox.isChecked = true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.prompt_tool_permissions_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save_permissions -> {
                saveAndFinish()
                true
            }
            R.id.reset_all -> {
                resetAll()
                true
            }
            android.R.id.home -> {
                cancelOrConfirmDiscard()
                true
            }
            R.id.show_help -> {
                CommonUtils.showHelpDialog(
                    activity = this,
                    titleResId = R.string.help,
                    messageResId = R.string.help_ai_document_filter_text,
                    helpPath = "ai.html#available-data-and-documents",
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        cancelOrConfirmDiscard()
    }
}
