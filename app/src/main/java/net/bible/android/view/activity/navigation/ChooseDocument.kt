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
package net.bible.android.view.activity.navigation

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.DocumentSelectionBase
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.download.DownloadActivity
import org.crosswire.common.util.Language
import org.crosswire.jsword.book.Book
import java.util.*
import javax.inject.Inject

/**
 * Choose a bible or commentary to use
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ChooseDocument : DocumentSelectionBase(R.menu.choose_document_menu, R.menu.document_context_menu) {
    @Inject lateinit var downloadControl: DownloadControl
    @Inject lateinit var backupControl: BackupControl

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildActivityComponent().inject(this)
        documentItemAdapter = DocumentItemAdapter(this)
        initialiseView()
        GlobalScope.launch {
            populateMasterDocumentList(false)
        }
        Log.i(TAG, "ChooseDocument downloadControl:$downloadControl")
    }

    override fun setInitialDocumentType() {
        selectedDocumentFilterNo = when(intent.extras?.getString("type")) {
            "BIBLE" -> 1
            "COMMENTARY" -> 2
            else -> 0
        }
    }

    override fun setDefaultLanguage() {
        selectedLanguageNo = -1
    }

    /** load list of docs to display
     *
     */
    override fun getDocumentsFromSource(refresh: Boolean): List<Book> {
        Log.d(TAG, "get document list from source")
        return swordDocumentFacade.documents
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

    override fun handleDocumentSelection(selectedDocument: Book?) {
        Log.d(TAG, "Book selected:" + selectedDocument!!.initials)
        try {
            documentControl.changeDocument(selectedDocument)

            // if key is valid then the new doc will have been shown already
            returnToPreviousScreen()
        } catch (e: Exception) {
            Log.e(TAG, "error on select of bible book", e)
        }
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
                    instance.showErrorMsg(R.string.error_occurred, e)
                }
            }
            R.id.backupButton -> {
                GlobalScope.launch {
                    backupControl.backupModulesViaIntent(this@ChooseDocument)
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
