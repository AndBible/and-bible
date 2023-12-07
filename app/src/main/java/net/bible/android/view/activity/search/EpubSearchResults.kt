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
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
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
import net.bible.service.sword.epub.KeyAndText
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.index.search.SearchType
import java.util.*
import javax.inject.Inject

class EpubSearchResults : ListActivityBase(R.menu.empty_menu) {
    private lateinit var binding: ListBinding
    private lateinit var resultAdapter: EpubSearchItemAdapter
    private var searchResults: List<KeyAndText> = emptyList()
    override val integrateWithHistoryManager: Boolean = true
    private var searchDocument: Book? = null

    @Inject lateinit var linkControl: LinkControl
    @Inject lateinit var windowControl: WindowControl

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Search results view")
        binding = ListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
        if (fetchSearchResults()) {
            withContext(Dispatchers.Main) {
                resultAdapter = EpubSearchItemAdapter(this@EpubSearchResults, LIST_ITEM_TYPE, searchResults)
                listAdapter = resultAdapter
                resultAdapter.notifyDataSetChanged()
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

    private suspend fun fetchSearchResults(): Boolean = withContext(Dispatchers.IO) Main@ {
        Log.i(TAG, "Preparing search results")
        var isOk: Boolean
        try {
            val searchText = intent.getStringExtra("searchText") ?: ""
            val searchType = intent.getStringExtra("searchType")?.let { SearchType.valueOf(it) }
            val searchDocument = (intent.getStringExtra("searchDocument")?: "").run {
                if (StringUtils.isEmpty(this))
                    windowControl.activeWindowPageManager.currentBible.currentDocument!!.initials
                else this
            }
            Log.i(TAG, "Searching $searchText in $searchDocument")

            val doc = Books.installed().getBook(searchDocument)
            if(!doc.isEpub) {
                Log.e(TAG, "Document ${doc.name} not epub!")
                return@Main false
            }
            if (doc.epubBackend?.state?.isIndexed != true) {
                startActivity(Intent(this@EpubSearchResults, SearchIndex::class.java))
                return@Main false
            }
            this@EpubSearchResults.searchDocument = doc
            val results = doc.epubBackend?.state!!.search(adjustSearchText(searchType, searchText))
            searchResults = results

            val msg: String = if (results.size >= SearchControl.MAX_SEARCH_RESULTS) {
                getString(R.string.search_showing_first, SearchControl.MAX_SEARCH_RESULTS)
            } else {
                getString(R.string.search_result_count, results.size)
            }
            withContext(Dispatchers.Main) {
                var resultAmount = results.size.toString()
                if(results.size > SearchControl.MAX_SEARCH_RESULTS) {
                    resultAmount += "+"
                }
                supportActionBar?.title = getString(R.string.search_with_results2, resultAmount, doc.abbreviation)
                Toast.makeText(this@EpubSearchResults, msg, Toast.LENGTH_SHORT).show()
            }
            isOk = true
        } catch (e: Exception) {
            Log.e(TAG, "Error processing search query", e)
            isOk = false
            Dialogs.showErrorMsg(R.string.error_executing_search) { onBackPressed() }
        }
        return@Main isOk
    }

    private fun adjustSearchText(searchType: SearchType?, searchText: String): String = when(searchType) {
        SearchType.PHRASE -> "\"$searchText\""
        SearchType.ALL_WORDS -> searchText.split(' ').joinToString(" AND ")
        SearchType.ANY_WORDS -> searchText.split(' ').joinToString(" OR ")
        null -> searchText
        else -> searchText
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            intent.putExtra("listPosition", l.firstVisiblePosition)
            resultSelected(searchResults[position])
        } catch (e: Exception) {
            Log.e(TAG, "Selection error", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun resultSelected(key: KeyAndText?) {
        Log.i(TAG, "chose:$key")
        if (key != null) {
            val targetBook = this.searchDocument
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(targetBook, key.key)
            val intent = Intent(this, MainBibleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    companion object {
        private const val TAG = "SearchResults"
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_2
    }
}
