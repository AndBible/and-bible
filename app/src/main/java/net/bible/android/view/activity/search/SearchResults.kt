/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import net.bible.android.control.search.GroupedSearchResult
import net.bible.android.control.search.MultiSearchResultsDto
import net.bible.service.download.FakeBookFactory
import net.bible.service.common.CommonUtils
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.Key
import javax.inject.Inject

class SearchResults : ListActivityBase(R.menu.empty_menu) {
    private lateinit var binding: ListBinding
    private var mSearchResultsHolder: MultiSearchResultsDto? = null
    private var mCurrentlyDisplayedResults: List<GroupedSearchResult> = ArrayList()
    private var mSearchAdapter: MultiSearchItemAdapter? = null
    private var isScriptureResultsCurrentlyShown = true
    override val integrateWithHistoryManager: Boolean = true

    private var selectedTranslations: List<String> = emptyList()
    private var isStrongsSearch = false
    private var documentSelectorMenuItem: MenuItem? = null

    @Inject lateinit var searchResultsActionBarManager: SearchResultsActionBarManager
    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var linkControl: LinkControl
    @Inject lateinit var windowControl: WindowControl

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

        selectedTranslations = intent.getStringArrayListExtra(SearchControl.SELECTED_TRANSLATIONS)
            ?: intent.getStringExtra(SearchControl.SEARCH_DOCUMENT)?.let { listOf(it) }
            ?: emptyList()

        isStrongsSearch = intent.getBooleanExtra(SearchControl.IS_STRONGS_SEARCH, false)

        binding.closeButton.setOnClickListener {
            finish()
        }
        lifecycleScope.launch {
            prepareResults()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_results_actionbar_menu, menu)
        documentSelectorMenuItem = menu.findItem(R.id.changeSearchDocuments)
        updateDocumentSelectorTitle()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.openResultsInWindow -> {
                openResultsInAWindow()
                true
            }
            R.id.changeSearchDocuments -> {
                lifecycleScope.launch { showDocumentSelector() }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openResultsInAWindow() {
        val lst = BookAndKeyList()
        for (result in mCurrentlyDisplayedResults) {
            for (match in result.translationMatches) {
                lst.addAll(BookAndKey(match.key, match.book))
            }
        }
        linkControl.showLink(FakeBookFactory.multiDocument, lst)
        finish()
    }

    private fun allBibles(): List<SwordBook> =
        SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().sortedBy { it.abbreviation }

    private fun updateDocumentSelectorTitle() {
        val byInitials = allBibles().associateBy { it.initials }
        val label = selectedTranslations
            .mapNotNull { byInitials[it]?.abbreviation }
            .joinToString(", ")
            .ifEmpty { getString(R.string.choose_translations) }
        documentSelectorMenuItem?.title = label
    }

    private suspend fun showDocumentSelector() {
        val candidates = candidateSearchDocuments(isStrongsSearch, allBibles())
        if (candidates.isEmpty()) return

        val selected = Dialogs.multiselect(
            context = this,
            title = getString(R.string.choose_translations),
            items = candidates,
            itemToString = { book ->
                if (book.indexStatus == IndexStatus.DONE) "${book.abbreviation} - ${book.name}"
                else "${book.abbreviation} - ${book.name} (${getString(R.string.search_index_not_created)})"
            },
            preSelected = { selectedTranslations.contains(it.initials) }
        )
        // Empty result = cancelled / dismissed / nothing checked -> no change.
        if (selected.isEmpty()) return

        selectedTranslations = selected.map { it.initials }
        updateDocumentSelectorTitle()
        persistSelection()

        // An unindexed selected document must be indexed before it can be searched. Carry the full
        // search context to SearchIndex so that after indexing the flow returns to SearchResults
        // (SEARCH_TEXT present) and re-runs with the chosen documents. Without SEARCH_TEXT,
        // SearchIndexProgressStatus falls through to the Search entry screen, which shows
        // "An error has occurred" when the current window's document is itself unindexed.
        val unindexed = selected.filter { it.indexStatus != IndexStatus.DONE }
        if (unindexed.isNotEmpty()) {
            startActivity(Intent(this, SearchIndex::class.java).apply {
                putExtra(SearchControl.SEARCH_DOCUMENT, unindexed.first().initials)
                putExtra(SearchControl.SEARCH_TEXT, intent.getStringExtra(SearchControl.SEARCH_TEXT))
                putExtra(SearchControl.IS_STRONGS_SEARCH, isStrongsSearch)
                putStringArrayListExtra(SearchControl.SELECTED_TRANSLATIONS, ArrayList(selectedTranslations))
            })
            return
        }

        prepareResults()
    }

    /**
     * Remembers the chosen documents so the next search restores them. Strong's find-all uses its
     * own key (a Strong's-enabled subset); normal Bible searches share the manual search screen's
     * key, so the two stay in sync.
     */
    private fun persistSelection() {
        val key = if (isStrongsSearch) SearchControl.STRONGS_SEARCH_TRANSLATIONS_PREF
        else SearchControl.SEARCH_TRANSLATIONS_PREF
        CommonUtils.settings.setString(key, selectedTranslations.joinToString(","))
    }

    private suspend fun prepareResults() {
        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.VISIBLE
            binding.empty.visibility = View.GONE
        }
        if (fetchSearchResults()) {
            withContext(Dispatchers.Main) {
                mSearchAdapter = MultiSearchItemAdapter(
                    this@SearchResults,
                    mCurrentlyDisplayedResults,
                    ::onTranslationPillClick
                )
                listAdapter = mSearchAdapter as ListAdapter
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

    private fun onTranslationPillClick(book: SwordBook, key: Key) {
        intent.putExtra("listPosition", listView.firstVisiblePosition)
        windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
        val intent = Intent(this, MainBibleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private suspend fun fetchSearchResults(): Boolean = withContext(Dispatchers.IO) Main@ {
        Log.i(TAG, "Preparing search results")
        var isOk: Boolean
        try {
            val searchText = intent.getStringExtra(SearchControl.SEARCH_TEXT) ?: ""

            if(linkControl.tryToOpenRef(searchText)) {
                historyTraversal.historyManager.popHistoryItem()
                historyTraversal.historyManager.popHistoryItem()
                finish()
                return@Main false
            }

            Log.i(TAG, "Searching: $searchText in ${selectedTranslations.size} translations")

            mSearchResultsHolder = searchControl.getMultiSearchResults(selectedTranslations, searchText)

            withContext(Dispatchers.Main) {
                val resultCount = mSearchResultsHolder?.size ?: 0
                supportActionBar?.title = getString(R.string.multi_search_results, resultCount, selectedTranslations.size)
                updateDocumentSelectorTitle()
                Toast.makeText(
                    this@SearchResults,
                    getString(R.string.search_result_count, resultCount),
                    Toast.LENGTH_SHORT
                ).show()
            }
            isOk = true
        } catch (e: Exception) {
            Log.e(TAG, "Error processing search query", e)
            isOk = false
            Dialogs.showErrorMsg(R.string.error_executing_search) { onBackPressed() }
        }
        return@Main isOk
    }

    private fun populateViewResultsAdapter() {
        mCurrentlyDisplayedResults = if (isScriptureResultsCurrentlyShown) {
            mSearchResultsHolder?.mainSearchResults ?: emptyList()
        } else {
            mSearchResultsHolder?.otherSearchResults ?: emptyList()
        }
        mSearchAdapter?.clear()
        mSearchAdapter?.addAll(mCurrentlyDisplayedResults)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        // Clicks are handled by the adapter (verse clicks and expand/collapse)
    }

    private val scriptureToggleClickListener = View.OnClickListener {
        isScriptureResultsCurrentlyShown = !isScriptureResultsCurrentlyShown
        populateViewResultsAdapter()
        mSearchAdapter?.notifyDataSetChanged()
        searchResultsActionBarManager.setScriptureShown(isScriptureResultsCurrentlyShown)
    }

    companion object {
        private const val TAG = "SearchResults"
    }
}
