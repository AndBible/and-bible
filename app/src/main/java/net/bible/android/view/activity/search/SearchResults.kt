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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import kotlinx.android.synthetic.main.list.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.search.SearchControl
import net.bible.android.control.search.SearchResultsDto
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.search.searchresultsactionbar.SearchResultsActionBarManager
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key
import java.util.*
import javax.inject.Inject

/** do the search and show the search results
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class SearchResults : ListActivityBase(R.menu.empty_menu) {
    override val customTheme: Boolean
        protected get() = false

    private var mSearchResultsHolder: SearchResultsDto? = null
    private var mCurrentlyDisplayedSearchResults: List<Key> = ArrayList()
    private var mKeyArrayAdapter: ArrayAdapter<Key>? = null
    private var isScriptureResultsCurrentlyShown = true
    @Inject lateinit var searchResultsActionBarManager: SearchResultsActionBarManager
    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, true)
        Log.i(TAG, "Displaying Search results view")
        setContentView(R.layout.list)
        buildActivityComponent().inject(this)
        searchResultsActionBarManager.registerScriptureToggleClickListener(scriptureToggleClickListener)
        setActionBarManager(searchResultsActionBarManager)
        isScriptureResultsCurrentlyShown = searchControl.isCurrentDefaultScripture
        GlobalScope.launch {
            prepareResults()
        }
    }

    private suspend fun prepareResults() {
        loadingIndicator.visibility = View.VISIBLE
        empty.visibility = View.GONE
        if (fetchSearchResults()) { // initialise adapters before result population - easier when updating due to later Scripture toggle
            withContext(Dispatchers.Main) {
                mKeyArrayAdapter = SearchItemAdapter(this@SearchResults, LIST_ITEM_TYPE, mCurrentlyDisplayedSearchResults, searchControl)
                listAdapter = mKeyArrayAdapter
                populateViewResultsAdapter()
            }
        }
        loadingIndicator.visibility = View.GONE
        empty.visibility = View.VISIBLE
    }
    /** do the search query and prepare results in lists ready for display
     *
     */
    private suspend fun fetchSearchResults(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Preparing search results")
        var isOk: Boolean
        try { // get search string - passed in using extras so extras cannot be null
            val extras = intent.extras
            val searchText = extras!!.getString(SearchControl.SEARCH_TEXT)
            var searchDocument = extras.getString(SearchControl.SEARCH_DOCUMENT)
            if (StringUtils.isEmpty(searchDocument)) {
                searchDocument = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument!!.initials
            }
            mSearchResultsHolder = searchControl.getSearchResults(searchDocument, searchText)
            // tell user how many results were returned
            val msg: String
            msg = if (mCurrentlyDisplayedSearchResults.size >= SearchControl.MAX_SEARCH_RESULTS) {
                getString(R.string.search_showing_first, SearchControl.MAX_SEARCH_RESULTS)
            } else {
                getString(R.string.search_result_count, mSearchResultsHolder!!.size())
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SearchResults, msg, Toast.LENGTH_SHORT).show()
            }
            isOk = true
        } catch (e: Exception) {
            Log.e(TAG, "Error processing search query", e)
            isOk = false
            Dialogs.instance.showErrorMsg(R.string.error_executing_search) { onBackPressed() }
        }
        return@withContext isOk
    }

    /**
     * Move search results into view Adapter
     */
    private fun populateViewResultsAdapter() {
        mCurrentlyDisplayedSearchResults = if (isScriptureResultsCurrentlyShown) {
            mSearchResultsHolder!!.mainSearchResults
        } else {
            mSearchResultsHolder!!.otherSearchResults
        }
        // addAll is only supported in Api 11+
        mKeyArrayAdapter!!.clear()
        for (key in mCurrentlyDisplayedSearchResults) {
            mKeyArrayAdapter!!.add(key)
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try { // no need to call HistoryManager.addHistoryItem() here because PassageChangeMediator will tell HistoryManager a change is about to occur
            verseSelected(mCurrentlyDisplayedSearchResults[position])
        } catch (e: Exception) {
            Log.e(TAG, "Selection error", e)
            Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun verseSelected(key: Key?) {
        Log.i(TAG, "chose:$key")
        if (key != null) { // which doc do we show
            var targetDocInitials = intent.extras!!.getString(SearchControl.TARGET_DOCUMENT)
            if (StringUtils.isEmpty(targetDocInitials)) {
                targetDocInitials = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument!!.initials
            }
            val targetBook = swordDocumentFacade.getDocumentByInitials(targetDocInitials)
            activeWindowPageManagerProvider.activeWindowPageManager.setCurrentDocumentAndKey(targetBook, key)
            // this also calls finish() on this Activity.  If a user re-selects from HistoryList then a new Activity is created
            val intent = Intent(this, MainBibleActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }
    }

    /**
     * Handle scripture/Appendix toggle
     */
    private val scriptureToggleClickListener = View.OnClickListener {
        isScriptureResultsCurrentlyShown = !isScriptureResultsCurrentlyShown
        populateViewResultsAdapter()
        mKeyArrayAdapter!!.notifyDataSetChanged()
        searchResultsActionBarManager.setScriptureShown(isScriptureResultsCurrentlyShown)
    }

	fun onClose(v: View) {
		finish()
	}

    companion object {
        private const val TAG = "SearchResults"
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_2
    }
}
