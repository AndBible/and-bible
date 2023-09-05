/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.navigation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.document.canDelete
import net.bible.android.database.DocumentSearchDao
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.DocumentSelectionBase
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.installedDocument
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.common.util.Language
import org.crosswire.jsword.book.Book
import java.util.*

/**
 * Choose a bible or commentary to use
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ChooseDocument : DocumentSelectionBase(R.menu.choose_document_menu, R.menu.document_context_menu) {
    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildActivityComponent().inject(this)
        documentItemAdapter = DocumentItemAdapter(this)
        initialiseView()

        lifecycleScope.launch {
            populateMasterDocumentList(false)
        }
        Log.i(TAG, "ChooseDocument downloadControl:$downloadControl")
    }

    override fun setInitialDocumentType() {
        selectedDocumentFilterNo = when(intent.extras?.getString("type")) {
            "BIBLE" -> 1
            "COMMENTARY" -> 2
            else -> CommonUtils.settings.getInt("selected_document_filter_no", 0)
        }
    }

    override fun setDefaultLanguage() {
        selectedLanguageNo = -1
    }

    override val dao: DocumentSearchDao get() = DatabaseContainer.instance.chooseDocumentsDb.documentSearchDao()

    /** load list of docs to display
     *
     */
    override suspend fun getDocumentsFromSource(refresh: Boolean): List<Book> {
        Log.i(TAG, "get document list from source")
        return SwordDocumentFacade.documents + FakeBookFactory.pseudoDocuments
    }

    /**
     * Get normally sorted list of languages for the language selection spinner
     */
    override fun sortLanguages(languages: Collection<Language>?): List<Language> {
        val languageList: MutableList<Language> = ArrayList()
        if (languages != null) {
            languageList.addAll(languages)

            // sort languages alphabetically
            languageList.sort()
        }
        return languageList
    }

    override fun handleDocumentSelection(selectedDocument: Book) {
        lifecycleScope.launch(Dispatchers.Main) {
            if(selectedDocument.isLocked && !CommonUtils.unlockDocument(this@ChooseDocument, selectedDocument)) {
                reloadDocuments()
                return@launch
            }
            Log.i(TAG, "Book selected:" + selectedDocument.initials)

            val myIntent = Intent(this@ChooseDocument, ChooseDocument::class.java)
            myIntent.putExtra("book", selectedDocument.initials)
            setResult(Activity.RESULT_OK, myIntent)
            finish()
        }
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu, selectedItemPositions: List<Int>): Boolean {
        if(selectedItemPositions.isNotEmpty()) {
            menu.findItem(R.id.unlock).isVisible = displayedDocuments[selectedItemPositions[0]].isEnciphered
            menu.findItem(R.id.delete).isVisible = displayedDocuments[selectedItemPositions[0]].canDelete
        }
        return super.onPrepareActionMode(mode, menu, selectedItemPositions)
    }

    override fun onActionItemClicked(item: MenuItem, selectedItemPositions: List<Int>): Boolean {
        when(item.itemId) {
            R.id.unlock -> lifecycleScope.launch(Dispatchers.Main) {
                CommonUtils.unlockDocument(this@ChooseDocument, displayedDocuments[selectedItemPositions[0]])
                reloadDocuments()
            }
        }
        return super.onActionItemClicked(item, selectedItemPositions)
    }
    /**
     * on Click handlers
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = false
        when (item.itemId) {
            R.id.downloadButton -> {
                isHandled = true
                try {
                    if (downloadControl.checkDownloadOkay()) {
                        val handlerIntent = Intent(this, DownloadActivity::class.java)
                        val requestCode = IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH
                        startActivityForResult(handlerIntent, requestCode)

                        // do not return here after download
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sorting bookmarks", e)
                    Dialogs.showErrorMsg(R.string.error_occurred, e)
                }
            }
            R.id.backupButton -> {
                lifecycleScope.launch {
                    BackupControl.backupModulesViaIntent(this@ChooseDocument)
                }
            }
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    companion object {
        private const val TAG = "ChooseDocument"
    }
}
