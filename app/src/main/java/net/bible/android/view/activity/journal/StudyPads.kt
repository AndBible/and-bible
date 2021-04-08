/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
/**
 *
 */
package net.bible.android.view.activity.journal

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ListView
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActionModeHelper
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.service.common.CommonUtils
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import java.util.*
import javax.inject.Inject

/**
 * Show a list of existing Bookmarks that have notes written to them
 */
class StudyPads : ListActivityBase(), ActionModeActivity {
    @Inject lateinit var bookmarkControl: BookmarkControl
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    private val journalList: MutableList<BookmarkEntities.Label> = ArrayList()
    private var listActionModeHelper: ListActionModeHelper? = null

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.journals_options_menu, menu)
        return true
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, true)
        setContentView(R.layout.list)
        buildActivityComponent().inject(this)
        initialiseView()
    }

    private fun initialiseView() {
        listActionModeHelper = ListActionModeHelper(listView, R.menu.usernote_context_menu)
        listView.onItemLongClickListener=
            OnItemLongClickListener {
                parent, view, position, id ->
                listActionModeHelper!!.startActionMode(this@StudyPads, position)
            }

        // prepare the document list view
        val myNoteArrayAdapter = StudyPadItemAdapter(this, R.layout.studypad_list_item, journalList, bookmarkControl)
        listAdapter = myNoteArrayAdapter
        loadJournalList()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            if (!listActionModeHelper!!.isInActionMode) {
                journalSelected(journalList[position])

                // HistoryManager will create a new Activity on Back
                val resultIntent = Intent(this, StudyPads::class.java)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "document selection error", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    override fun onActionItemClicked(item: MenuItem, selectedItemPositions: List<Int>): Boolean {
        val selectedNotes = getSelectedMyNotes(selectedItemPositions)
        if (!selectedNotes.isEmpty()) {
            when (item.itemId) {
                R.id.delete -> {
                    delete(selectedNotes)
                    return true
                }
            }
        }
        return false
    }

    override fun isItemChecked(position: Int): Boolean {
        return listView.isItemChecked(position)
    }

    private fun getSelectedMyNotes(selectedItemPositions: List<Int>): List<BookmarkEntities.Label> {
        val selectedNotes: MutableList<BookmarkEntities.Label> = ArrayList()
        for (position in selectedItemPositions) {
            selectedNotes.add(journalList[position])
        }
        return selectedNotes
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = false
        when(item.itemId){
            R.id.manage -> {
                startActivityForResult(Intent(this, ManageLabels::class.java), STD_REQUEST_CODE)
                isHandled = true
            }
            R.id.help -> {
                CommonUtils.showHelp(this, listOf(R.string.help_studypads_title))
            }
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        loadJournalList()
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun delete(journals: List<BookmarkEntities.Label>) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete_study_pads, journals.size))
            .setPositiveButton(R.string.yes) { _, _ ->
                bookmarkControl.deleteLabels(journals.map { it.id })
                loadJournalList()
            }
            .setCancelable(true)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadJournalList() {
        listActionModeHelper!!.exitActionMode()
        journalList.clear()
        journalList.addAll(bookmarkControl.assignableLabels.filterNot { it.isSpeakLabel })
        notifyDataSetChanged()
    }

    private fun journalSelected(journal: BookmarkEntities.Label) {
        Log.d(TAG, "Journal selected:" + journal.name)
        try {
            activeWindowPageManagerProvider.activeWindowPageManager.setCurrentDocumentAndKey(FakeBookFactory.journalDocument, StudyPadKey(journal))
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to show journal", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    companion object {
        private const val TAG = "Journals"
    }
}
