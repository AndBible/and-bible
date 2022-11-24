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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ListBinding
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.search.searchresultsactionbar.SearchResultsActionBarManager
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.Key
import java.util.*
import javax.inject.Inject

class SearchResultsDto {
    val mainSearchResults: MutableList<Key> = ArrayList()
    val otherSearchResults: MutableList<Key> = ArrayList()
    fun add(resultKey: Key, isMain: Boolean) {
        if (isMain) {
            mainSearchResults.add(resultKey)
        } else {
            otherSearchResults.add(resultKey)
        }
    }

    val size: Int get() = mainSearchResults.size + otherSearchResults.size
}

class SearchResults : ListActivityBase(R.menu.empty_menu) {
    private lateinit var binding: ListBinding
    private var mSearchResultsHolder: SearchResultsDto? = null
    private var mCurrentlyDisplayedSearchResults: List<Key> = ArrayList()
    private var mKeyArrayAdapter: ArrayAdapter<Key>? = null
    private var isScriptureResultsCurrentlyShown = true
    override val integrateWithHistoryManager: Boolean = true
    var searchDocument: SwordBook? = null
    @Inject lateinit var searchResultsActionBarManager: SearchResultsActionBarManager
    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var linkControl: LinkControl
    @Inject lateinit var windowControl: WindowControl
    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Search results view")
        binding = ListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)
        searchResultsActionBarManager.registerScriptureToggleClickListener(scriptureToggleClickListener)
        setActionBarManager(searchResultsActionBarManager)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        isScriptureResultsCurrentlyShown = searchControl.isCurrentlyShowingScripture
        binding.closeButton.setOnClickListener {
            finish()
        }
        lifecycleScope.launch {
            prepareResults()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private suspend fun prepareResults() {
        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.VISIBLE
            binding.empty.visibility = View.GONE
        }
        if (fetchSearchResults()) { // initialise adapters before result population - easier when updating due to later Scripture toggle
            withContext(Dispatchers.Main) {
                mKeyArrayAdapter = SearchItemAdapter(this@SearchResults, LIST_ITEM_TYPE, mCurrentlyDisplayedSearchResults)
                listAdapter = mKeyArrayAdapter as ListAdapter
                populateViewResultsAdapter()
                listView.setSelection(intent.getIntExtra("listPosition", 0))
            }
        }
        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.GONE
            if(listAdapter?.isEmpty == true) {
                binding.empty.visibility = View.VISIBLE
            }
        }
    }
    /** do the search query and prepare results in lists ready for display
     *
     */
    private suspend fun fetchSearchResults(): Boolean = withContext(Dispatchers.IO) Main@ {
        Log.i(TAG, "Preparing search results")
        var isOk: Boolean
        try {
            val searchText =
                if(intent.action == Intent.ACTION_PROCESS_TEXT) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
                    } else ""
                }
                else intent.getStringExtra(SearchControl.SEARCH_TEXT) ?: ""

            val searchDocument = (intent.getStringExtra(SearchControl.SEARCH_DOCUMENT)?: "").run {
                if (StringUtils.isEmpty(this))
                    windowControl.activeWindowPageManager.currentBible.currentDocument!!.initials
                else this
            }
            Log.i(TAG, "Searching $searchText in $searchDocument")

            val doc = Books.installed().getBook(searchDocument)
            if(doc !is SwordBook) {
                Log.e(TAG, "Document ${doc.name} not SwordBook!")
                return@Main false
            }
            if (doc.indexStatus != IndexStatus.DONE) {
                startActivity(Intent(this@SearchResults, SearchIndex::class.java))
                return@Main false
            }
            if(linkControl.tryToOpenRef(searchText)) {
                val handlerIntent = Intent(this@SearchResults, MainBibleActivity::class.java)
                handlerIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(handlerIntent)
                return@Main false
            }
            this@SearchResults.searchDocument = doc
            mSearchResultsHolder = searchControl.getSearchResults(searchDocument, searchText)
            // tell user how many results were returned
            val msg: String
            msg = if (mCurrentlyDisplayedSearchResults.size >= SearchControl.MAX_SEARCH_RESULTS) {
                getString(R.string.search_showing_first, SearchControl.MAX_SEARCH_RESULTS)
            } else {
                getString(R.string.search_result_count, mSearchResultsHolder!!.size)
            }
            withContext(Dispatchers.Main) {
                var resultAmount = mSearchResultsHolder?.size.toString()
                if((mSearchResultsHolder?.size ?: 0) > SearchControl.MAX_SEARCH_RESULTS) {
                    resultAmount += "+"
                }
                supportActionBar?.title = getString(R.string.search_with_results, resultAmount)
                Toast.makeText(this@SearchResults, msg, Toast.LENGTH_SHORT).show()
            }
            isOk = true
        } catch (e: Exception) {
            Log.e(TAG, "Error processing search query", e)
            isOk = false
            Dialogs.showErrorMsg(R.string.error_executing_search) { onBackPressed() }
        }
        return@Main isOk
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
        mKeyArrayAdapter!!.clear()
        mKeyArrayAdapter!!.addAll(mCurrentlyDisplayedSearchResults)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try { // no need to call HistoryManager.addHistoryItem() here because PassageChangeMediator will tell HistoryManager a change is about to occur
            intent.putExtra("listPosition", l.firstVisiblePosition)
            verseSelected(mCurrentlyDisplayedSearchResults[position])
        } catch (e: Exception) {
            Log.e(TAG, "Selection error", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun verseSelected(key: Key?) {
        Log.i(TAG, "chose:$key")
        if (key != null) {
            val targetBook = this.searchDocument
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(targetBook, key)
            // this also calls finish() on this Activity.  If a user re-selects from HistoryList then a new Activity is created
            val intent = Intent(this, MainBibleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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

    companion object {
        private const val TAG = "SearchResults"
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_2
    }
}
