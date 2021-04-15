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

package net.bible.android.view.activity.search

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.isVisible

import net.bible.android.activity.R
import net.bible.android.activity.databinding.SearchIndexBinding
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.base.CustomTitlebarActivityBase

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book

import javax.inject.Inject

/** Create a Lucene search index
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class SearchIndex : CustomTitlebarActivityBase() {

    private lateinit var binding: SearchIndexBinding

    @Inject lateinit var searchControl: SearchControl

    private val documentToIndex: Book?
        get() {
            val documentInitials = intent.getStringExtra(SearchControl.SEARCH_DOCUMENT)

            val documentToIndex: Book?
            if (StringUtils.isNotEmpty(documentInitials)) {
                documentToIndex = swordDocumentFacade.getDocumentByInitials(documentInitials)
            } else {
                documentToIndex = pageControl.currentPageManager.currentPage.currentDocument
            }

            return documentToIndex
        }

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        buildActivityComponent().inject(this)
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying SearchIndex view")
        binding = SearchIndexBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val hasIndex = swordDocumentFacade.hasIndex(documentToIndex)
        binding.indexCreationRequired.text = getString(if(hasIndex) R.string.rebuild_index_for else R.string.create_index_for, documentToIndex!!.name)
        binding.createButton.text = getString(if(hasIndex) R.string.rebuild_index_button else R.string.index_create)

        Log.d(TAG, "Finished displaying Search Index view")
    }

    fun onCancel(v: View) = finish()

    /** Indexing is very slow
     *
     * @param v
     */
    fun onIndex(v: View) {
        Log.i(TAG, "CLICKED")
        try {
            // start background thread to create index
            val doc = documentToIndex
            swordDocumentFacade.deleteDocumentIndex(doc)
            val bOk = searchControl.createIndex(doc)

            if (bOk) {
                monitorProgress()
            }
        } catch (e: Exception) {
            Log.e(TAG, "error indexing:" + e.message)
            e.printStackTrace()
        }

    }

    /**
     * Show progress monitor screen
     */
    private fun monitorProgress() {
        // monitor the progress
        val intent = Intent(this, SearchIndexProgressStatus::class.java)

        // a search may be pre-defined, if so then pass the pre-defined search through so it can be executed directly
        if (getIntent().extras != null) {
            intent.putExtras(getIntent().extras!!)
        }

        // always need to specify which document is being indexed
        if (StringUtils.isEmpty(intent.getStringExtra(SearchControl.SEARCH_DOCUMENT))) {
            // must tell the progress status screen which doc is being downloaded because it checks it downloaded successfully
            intent.putExtra(SearchControl.SEARCH_DOCUMENT, documentToIndex?.initials)
        }

        startActivity(intent)
        finish()
    }

    companion object {

        private val TAG = "SearchIndex"
    }
}
