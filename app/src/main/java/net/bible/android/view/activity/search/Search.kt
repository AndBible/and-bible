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
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.RadioButton
import android.widget.RadioGroup

import net.bible.android.activity.R
import net.bible.android.activity.databinding.SearchBinding
import net.bible.android.control.page.PageControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.search.SearchControl.SearchBibleSection
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.search.SearchType

import javax.inject.Inject

/** Allow user to enter search criteria
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class Search : CustomTitlebarActivityBase(R.menu.search_actionbar_menu) {

    private lateinit var binding: SearchBinding

    private var wordsRadioSelection = R.id.allWords
    private var sectionRadioSelection = R.id.searchAllBible
    private lateinit var currentBookName: String
    override val integrateWithHistoryManager: Boolean = true

    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var pageControl: PageControl

    private val documentToSearch: SwordBook
        get() = pageControl.currentPageManager.currentPage.currentDocument as SwordBook

    /** get all, any, phrase query limitation
     */
    private val searchType: SearchType
        get() {
            return when (wordsRadioSelection) {
                R.id.allWords -> SearchType.ALL_WORDS
                R.id.anyWord -> SearchType.ANY_WORDS
                R.id.phrase -> SearchType.PHRASE
                else -> {
                    Log.e(TAG, "Unexpected radio selection")
                    SearchType.ANY_WORDS
                }
            }
        }

    /** get OT, NT, or all query limitation
     *
     * @return
     */
    private val bibleSection: SearchBibleSection
        get() {
            return when (sectionRadioSelection) {
                R.id.searchAllBible -> SearchBibleSection.ALL
                R.id.searchOldTestament -> SearchBibleSection.OT
                R.id.searchNewTestament -> SearchBibleSection.NT
                R.id.searchCurrentBook -> SearchBibleSection.CURRENT_BOOK
                else -> {
                    Log.e(TAG, "Unexpected radio selection")
                    SearchBibleSection.ALL
                }
            }
        }

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Search view")
        binding = SearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CommonUtils.settings.setLong("search-last-used", System.currentTimeMillis())
        buildActivityComponent().inject(this)

        if (!searchControl.validateIndex(documentToSearch)) {
            Dialogs.showErrorMsg(R.string.error_occurred) { finish() }
        }

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
            // If the event is a key-down event on the "enter" button
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                // Perform action on key press
                onSearch()
                return@setOnKeyListener true
            }
            false
        }

        // pre-load search string if passed in
        val extras = intent.extras
        if (extras != null) {
            val text = extras.getString(SEARCH_TEXT_SAVE)
            if (StringUtils.isNotEmpty(text)) {
                binding.searchText.setText(text)
            }
        }

        val wordsRadioGroup = findViewById<View>(R.id.wordsGroup) as RadioGroup
        wordsRadioGroup.setOnCheckedChangeListener { group, checkedId -> wordsRadioSelection = checkedId }
        if (extras != null) {
            val wordsSelection = extras.getInt(WORDS_SELECTION_SAVE, -1)
            if (wordsSelection != -1) {
                wordsRadioGroup.check(wordsSelection)
            }
        }

        val sectionRadioGroup = findViewById<View>(R.id.bibleSectionGroup) as RadioGroup
        sectionRadioGroup.setOnCheckedChangeListener { group, checkedId -> sectionRadioSelection = checkedId }
        if (extras != null) {
            val sectionSelection = extras.getInt(SECTION_SELECTION_SAVE, -1)
            if (sectionSelection != -1) {
                sectionRadioGroup.check(sectionSelection)
            }
        }

        // set text for current bible book on appropriate radio button
        val currentBookRadioButton = findViewById<View>(R.id.searchCurrentBook) as RadioButton

        // set current book to default and allow override if saved - implies returning via Back button
        currentBookName = searchControl.currentBookName
        if (extras != null) {
            val currentBibleBookSaved = extras.getString(CURRENT_BIBLE_BOOK_SAVE)
            if (currentBibleBookSaved != null) {
                currentBookName = currentBibleBookSaved
            }
        }
        currentBookRadioButton.text = currentBookName

        Log.i(TAG, "Finished displaying Search view")
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


    fun onRebuildIndex(v: View?) {
        startActivity(Intent(this, SearchIndex::class.java))
        finish()
    }

    fun onCancel(v: View?) = finish()

    private fun onSearch() {
        Log.i(TAG, "CLICKED")
        var text = binding.searchText.text.toString()
        if (!StringUtils.isEmpty(text)) {

            // update current intent so search is restored if we return here via history/back
            // the current intent is saved by HistoryManager
            intent.putExtra(SEARCH_TEXT_SAVE, text)
            intent.putExtra(WORDS_SELECTION_SAVE, wordsRadioSelection)
            intent.putExtra(SECTION_SELECTION_SAVE, sectionRadioSelection)
            intent.putExtra(CURRENT_BIBLE_BOOK_SAVE, currentBookName)

            text = searchControl.decorateSearchString(text, searchType, bibleSection, currentBookName)
            Log.i(TAG, "Search text:$text")

            // specify search string and doc in new Intent;
            // if doc is not specifed a, possibly invalid, doc may be used when returning to search via history list e.g. search bible, select dict, history list, search results
            val intent = Intent(this, SearchResults::class.java)
            intent.putExtra(SearchControl.SEARCH_TEXT, text)
            val currentDocInitials = documentToSearch.initials
            intent.putExtra(SearchControl.SEARCH_DOCUMENT, currentDocInitials)
            intent.putExtra(SearchControl.TARGET_DOCUMENT, currentDocInitials)
            startActivityForResult(intent, 1)

            // Back button is now handled by HistoryManager - Back will cause a new Intent instead of just finish
            finish()
        }
    }

    companion object {

        private const val SEARCH_TEXT_SAVE = "Search"
        private const val WORDS_SELECTION_SAVE = "Words"
        private const val SECTION_SELECTION_SAVE = "Selection"
        private const val CURRENT_BIBLE_BOOK_SAVE = "BibleBook"

        private const val TAG = "Search"
    }
}
