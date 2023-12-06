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
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo

import net.bible.android.activity.R
import net.bible.android.activity.databinding.EpubSearchBinding
import net.bible.android.control.page.PageControl
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.service.common.CommonUtils

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.index.search.SearchType

import javax.inject.Inject

class EpubSearch : CustomTitlebarActivityBase(R.menu.search_actionbar_menu) {

    private lateinit var binding: EpubSearchBinding
    override val integrateWithHistoryManager: Boolean = true

    @Inject lateinit var pageControl: PageControl

    private val documentToSearch: Book
        get() = pageControl.currentPageManager.currentPage.currentDocument!!

    private val searchType: SearchType
        get() {
            return when (binding.wordsGroup.checkedRadioButtonId) {
                R.id.allWords -> SearchType.ALL_WORDS
                R.id.anyWord -> SearchType.ANY_WORDS
                R.id.phrase -> SearchType.PHRASE
                else -> {
                    Log.e(TAG, "Unexpected radio selection")
                    SearchType.ANY_WORDS
                }
            }
        }


    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Search view")
        binding = EpubSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CommonUtils.settings.setLong("search-last-used", System.currentTimeMillis())
        buildActivityComponent().inject(this)

        title = getString(R.string.search_in, documentToSearch.abbreviation)
        binding.searchText.setOnEditorActionListener {v, actionId, event ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    onSearch()
                    true
                }
                else -> false
        }}

        binding.submit.setOnClickListener { onSearch() }
        binding.searchText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                onSearch()
                return@setOnKeyListener true
            }
            false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val rv = super.onCreateOptionsMenu(menu)
        menu.findItem(R.id.help).isVisible = false
        return rv
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.rebuildIndex -> {
                startActivity(Intent(this, SearchIndex::class.java))
                return true
            }
            R.id.help -> {
                CommonUtils.showHelp(this, listOf(R.string.help_search_title))
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        binding.searchText.requestFocus()
    }

    private fun onSearch() {
        val text = binding.searchText.text.toString()
        if (!StringUtils.isEmpty(text)) {
            val intent = Intent(this, EpubSearchResults::class.java)
            intent.putExtra("searchText", text)
            intent.putExtra("searchType", searchType.name)
            intent.putExtra("searchDocument", documentToSearch.initials)
            startActivity(intent)
            finish()
        }
    }

    companion object {
        private const val TAG = "EpubSearch"
    }
}
