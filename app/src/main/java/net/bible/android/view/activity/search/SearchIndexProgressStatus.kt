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
package net.bible.android.view.activity.search


import net.bible.service.common.CommonUtils.pause
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.control.search.SearchControl
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ProgressActivityBase
import android.os.Bundle
import org.crosswire.common.progress.Progress
import org.crosswire.jsword.index.IndexStatus
import android.content.Intent
import android.util.Log
import android.view.View
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class SearchIndexProgressStatus : ProgressActivityBase() {
    private var documentBeingIndexed: Book? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_index_status)
        super.buildActivityComponent().inject(this)
        setMainText(getString(R.string.indexing_wait_msg))
        findViewById<View>(R.id.hideButton).setOnClickListener { v: View? -> finish() }
        val docInitials = intent.getStringExtra(SearchControl.SEARCH_DOCUMENT)
        documentBeingIndexed = swordDocumentFacade.getDocumentByInitials(docInitials)
    }

    /**
     * check index exists and go to search screen if index exists
     * if no more jobs in progress and no index then error
     *
     */
    override fun jobFinished(jobJustFinished: Progress) {
        // give the document up to 12 secs to reload - the Progress declares itself finished before the index status has been changed
        var attempts = 0
        while ((documentBeingIndexed == null || IndexStatus.DONE != documentBeingIndexed!!.indexStatus) && attempts++ < 6) {
            pause(2)
        }

        // if index is fine then goto search
        if (IndexStatus.DONE == documentBeingIndexed!!.indexStatus) {
            Log.i(TAG, "Index created")
            val intent: Intent
            if (StringUtils.isNotEmpty(getIntent().getStringExtra(SearchControl.SEARCH_TEXT))) {
                // the search string was passed in so execute it directly
                intent = Intent(this, SearchResults::class.java)
                intent.putExtras(getIntent().extras!!)
            } else {
                // just go to the normal Search screen
                intent = Intent(this, Search::class.java)
            }
            startActivity(intent)
            finish()
        } else {
            // if jobs still running then just wait else error
            if (isAllJobsFinished) {
                Log.e(TAG, "Index finished but document's index is invalid")
                instance.showErrorMsg(R.string.error_occurred)
            }
        }
    }

    companion object {
        private const val TAG = "SearchIndexProgressStat"
    }
}
