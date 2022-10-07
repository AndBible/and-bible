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
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar

import net.bible.android.activity.R
import net.bible.android.activity.databinding.SearchBinding
import net.bible.android.control.page.PageControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.search.SearchControl.SearchBibleSection
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
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

    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var pageControl: PageControl

    private val documentToSearch: Book?
        get() = pageControl.currentPageManager.currentPage.currentDocument

    /** get all, any, phrase query limitation
     */

    private var fuzzySearchAccuracySelection: Int = 5
        set(value) {
            binding.fuzzySearch.text = getString(R.string.search_fuzzy, "(~" + (value * 10).toString() + "%)")
            binding.fuzzySearchAccuracy.setProgress(value)
            binding.decoratedSearchString.text = decorateSearchString(binding.searchText.text.toString())
            field = value
        }

    // I don't know how to do this properly. I want to update the value of proximityWordNumber whenever it is needed (buttons, text, initialising)
    // But part of the update is to set the text value itself. But this gets into an infinite loop of course when called when the text value is updated.
    // So i have broken it into two parts. One updates everything and one does not update the text field.
    private var proximitySearchWordsSelection: Int = 10
        set(value) {
            binding.proximityWordNumber.setText(value.toString())
            proximitySearchWordsSelectionFinal = value
            binding.decoratedSearchString.text = decorateSearchString(binding.searchText.text.toString())
            field = value
        }
    private var proximitySearchWordsSelectionFinal: Int = 10
        set(value) {
            binding.proximitySearch.text = getString(R.string.search_proximity, "(${value.toString()} ${getString(R.string.words)})")
            field = value
        }

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
        super.onCreate(savedInstanceState, true)
        Log.i(TAG, "Displaying Search view")
        binding = SearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CommonUtils.settings.setLong("search-last-used", System.currentTimeMillis())
        buildActivityComponent().inject(this)

        // set text for current bible book on appropriate radio button
        val currentBookRadioButton = findViewById<View>(R.id.searchCurrentBook) as RadioButton

        // set current book to default and allow override if saved - implies returning via Back button
        currentBookName = searchControl.currentBookName

        if (!searchControl.validateIndex(documentToSearch)) {
            Dialogs.showErrorMsg(R.string.error_occurred) { finish() }
        }

        title = getString(R.string.search_in, documentToSearch!!.abbreviation)

        // Add all listeners
        binding.searchText.setOnEditorActionListener {v, actionId, event ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    onSearch()
                    true
                }
                else -> false
        }}
        binding.searchText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.decoratedSearchString.text = decorateSearchString(binding.searchText.text.toString())
            }
        })
        val sectionRadioGroup = findViewById<View>(R.id.bibleSectionGroup) as RadioGroup
        sectionRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            sectionRadioSelection = checkedId
            CommonUtils.settings.setInt("search_bible_section_group_prompt", checkedId)
            enableSearchControls()
        }
        val wordsRadioGroup = findViewById<View>(R.id.wordsGroup) as RadioGroup
        wordsRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            wordsRadioSelection = checkedId
            CommonUtils.settings.setInt("search_words_group_prompt", checkedId)
            enableSearchControls()
        }
        binding.includeAllEndings.setOnClickListener {
            CommonUtils.settings.setBoolean("search_include_all_endings", binding.includeAllEndings.isChecked)
            if (binding.includeAllEndings.isChecked) {
                binding.proximitySearch.isChecked = false
                binding.strongsSearch.isChecked = false
                binding.fuzzySearch.isChecked = false
            }
            enableSearchControls()
        }
        binding.rememberSearchText.setOnClickListener {
            CommonUtils.settings.setBoolean("search_remember_search_text", binding.rememberSearchText.isChecked)
        }
        binding.fuzzySearch.setOnClickListener {
            if (binding.fuzzySearch.isChecked) {
                binding.proximitySearch.isChecked = false
                binding.strongsSearch.isChecked = false
                binding.includeAllEndings.isChecked = false
            }
            enableSearchControls()
        }
        binding.fuzzySearchAccuracy.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                fuzzySearchAccuracySelection = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.proximitySearch.setOnClickListener {
            if (binding.proximitySearch.isChecked) {
                binding.fuzzySearch.isChecked = false
                binding.strongsSearch.isChecked = false
                binding.includeAllEndings.isChecked = false
            }
            enableSearchControls()
        }
        binding.proximityButtonAdd.setOnClickListener {proximitySearchWordsSelection = binding.proximityWordNumber.text.toString().toInt() + 1}
        binding.proximityButtonSubtract.setOnClickListener {proximitySearchWordsSelection = binding.proximityWordNumber.text.toString().toInt() - 1}
        binding.proximityWordNumber.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {proximitySearchWordsSelectionFinal = s.toString().toInt()}
        })

        binding.strongsSearch.setOnClickListener {
            if (binding.strongsSearch.isChecked) {
                binding.includeAllEndings.isChecked = false
                binding.fuzzySearch.isChecked = false
                binding.proximitySearch.isChecked = false
            }
            enableSearchControls()
        }
        binding.greekOrHebrewGroup.setOnCheckedChangeListener { group, checkedId ->
            CommonUtils.settings.setInt("search_greek_or_hebrew", checkedId)
            enableSearchControls()
        }
        binding.decoratedTextShow.setOnCheckedChangeListener { group, checkedId ->
            // I would like to show the actual text that is being used for the search somewhere.
            // I think it would be good to show it on the list screen. Could also show it here.

            CommonUtils.settings.setBoolean("search_show_decorated_text", checkedId)
            enableSearchControls()
        }

        binding.submit.setOnClickListener { onSearch() }
        //searchText.setOnKeyListener(OnKeyListener { v, keyCode, event ->
        //    // If the event is a key-down event on the "enter" button
        //    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
        //        // Perform action on key press
        //        onSearch(null)
        //        return@OnKeyListener true
        //    }
        //    false
        //})

        // Initialise controls
        binding.includeAllEndings.isChecked = CommonUtils.settings.getBoolean("search_include_all_endings", false)
        binding.rememberSearchText.isChecked = CommonUtils.settings.getBoolean("search_remember_search_text", false)

        binding.proximitySearch.isChecked = CommonUtils.settings.getBoolean("search_proximity",false)
        proximitySearchWordsSelection = CommonUtils.settings.getInt("search_proximity_words", 10)

        binding.wordsGroup.check(CommonUtils.settings.getInt("search_words_group_prompt", 0))
        wordsRadioSelection = binding.wordsGroup.checkedRadioButtonId

        binding.bibleSectionGroup.check(CommonUtils.settings.getInt("search_bible_section_group_prompt", 0))
        sectionRadioSelection = binding.bibleSectionGroup.checkedRadioButtonId

        binding.fuzzySearch.isChecked = CommonUtils.settings.getBoolean("search_fuzzy", false)
        fuzzySearchAccuracySelection = CommonUtils.settings.getInt("search_fuzzy_accuracy", 5)

        binding.strongsSearch.isChecked = CommonUtils.settings.getBoolean("search_strongs",false)
        binding.greekOrHebrewGroup.check(CommonUtils.settings.getInt("search_greek_or_hebrew",0))

        binding.decoratedTextShow.isChecked = CommonUtils.settings.getBoolean("search_show_decorated_text",false)

        // pre-load search string if passed in
        val extras = intent.extras
        if (extras != null) {
            val text = extras.getString(SEARCH_TEXT_SAVE)
            if (StringUtils.isNotEmpty(text)) {
                binding.searchText.setText(text)
            }
        } else {
            if (binding.rememberSearchText.isChecked) binding.searchText.setText(CommonUtils.settings.getString("search_text", ""))
        }

        if (extras != null) {
            val wordsSelection = extras.getInt(WORDS_SELECTION_SAVE, -1)
            if (wordsSelection != -1) {
                wordsRadioGroup.check(wordsSelection)
            }
        }

        if (extras != null) {
            val sectionSelection = extras.getInt(SECTION_SELECTION_SAVE, -1)
            if (sectionSelection != -1) {
                sectionRadioGroup.check(sectionSelection)
            }
        }

        if (extras != null) {
            val currentBibleBookSaved = extras.getString(CURRENT_BIBLE_BOOK_SAVE)
            if (currentBibleBookSaved != null) {
                currentBookName = currentBibleBookSaved
            }
        }
        currentBookRadioButton.text = currentBookName

        binding.textClear.setOnClickListener({binding.searchText.setText("")})
        enableSearchControls()

        Log.i(TAG, "Finished displaying Search view")
    }

    fun enableSearchControls() {
        
        // Set control visibility
        binding.fuzzySearchDetailsLayout.visibility = if (binding.fuzzySearch.isChecked) View.VISIBLE else View.GONE
        binding.proximityDetailsLayout.visibility = if (binding.proximitySearch.isChecked) View.VISIBLE else View.GONE
        binding.strongsDetailLayout.visibility = if (binding.strongsSearch.isChecked) View.VISIBLE else View.GONE
        binding.decoratedSearchDetailLayout.visibility = if (binding.decoratedTextShow.isChecked) View.VISIBLE else View.GONE

        enableLayout(binding.fuzzySearchLayout, binding.allWords.id == wordsRadioSelection)
        enableLayout(binding.fuzzySearchDetailsLayout, binding.fuzzySearch.isChecked && binding.fuzzySearch.isEnabled)

        enableLayout(binding.strongsDetailLayout, (binding.strongsSearch.isChecked && binding.strongsSearch.isEnabled))

        binding.searchOldTestament.isEnabled = !binding.strongsSearch.isChecked || (binding.strongsSearch.isEnabled && binding.strongsSearch.isChecked && binding.searchHebrew.isChecked)
        binding.searchNewTestament.isEnabled = !binding.strongsSearch.isChecked || (binding.strongsSearch.isEnabled && binding.strongsSearch.isChecked && binding.searchGreek.isChecked)

        binding.decoratedSearchString.text = decorateSearchString(binding.searchText.text.toString())
    }

    fun enableLayout(layout: LinearLayout, isEnabled: Boolean) {
        for (i in 0 until layout.childCount) {
            val view: View = layout.getChildAt(i)
            if (view is LinearLayout) {enableLayout(view,isEnabled)}
            view.isEnabled = isEnabled
        }
        layout.isEnabled = isEnabled
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

    fun onSearch() {
        Log.i(TAG, "CLICKED")
        var text = binding.searchText.text.toString()
        if (!StringUtils.isEmpty(text)) {

            CommonUtils.settings.setString("search_text", text)
            CommonUtils.settings.setBoolean("search_fuzzy", binding.fuzzySearch.isChecked)
            CommonUtils.settings.setInt("search_fuzzy_accuracy", fuzzySearchAccuracySelection)
            CommonUtils.settings.setBoolean("search_proximity", binding.proximitySearch.isChecked)
            CommonUtils.settings.setInt("search_proximity_words", proximitySearchWordsSelectionFinal)
            CommonUtils.settings.setBoolean("search_strongs", binding.strongsSearch.isChecked)

            // update current intent so search is restored if we return here via history/back
            // the current intent is saved by HistoryManager
            intent.putExtra(SEARCH_TEXT_SAVE, text)
            intent.putExtra(WORDS_SELECTION_SAVE, wordsRadioSelection)
            intent.putExtra(SECTION_SELECTION_SAVE, sectionRadioSelection)
            intent.putExtra(CURRENT_BIBLE_BOOK_SAVE, currentBookName)

//            text = searchControl.decorateSearchString(text, searchType, bibleSection, currentBookName)
            text = decorateSearchString(text)
            Log.i(TAG, "Search text:$text")

            // specify search string and doc in new Intent;
            // if doc is not specifed a, possibly invalid, doc may be used when returning to search via history list e.g. search bible, select dict, history list, search results
            val intent = Intent(this, MySearchResults::class.java)
            intent.putExtra(SearchControl.SEARCH_TEXT, text)
            val currentDocInitials = documentToSearch?.initials
            intent.putExtra(SearchControl.SEARCH_DOCUMENT, currentDocInitials)
            intent.putExtra(SearchControl.TARGET_DOCUMENT, currentDocInitials)
            startActivityForResult(intent, 1)

            // Back button is now handled by HistoryManager - Back will cause a new Intent instead of just finish
            finish()
        }
    }

    private fun decorateSearchString(searchString: String): String {
        val fuzzyAccuracy = if (binding.fuzzySearch.isChecked && binding.fuzzySearch.isEnabled) fuzzySearchAccuracySelection.toDouble()/10 else null
        val proximityWords = if (binding.proximitySearch.isChecked && binding.proximitySearch.isEnabled) proximitySearchWordsSelectionFinal else null
        val strongs = if (binding.strongsSearch.isChecked && binding.strongsSearch.isEnabled) {if (binding.searchGreek.isChecked) 'G' else 'H'} else null

        return searchControl.decorateSearchString(searchString, searchType, bibleSection, currentBookName, binding.includeAllEndings.isChecked, fuzzyAccuracy, proximityWords, strongs)
    }

    companion object {

        private const val SEARCH_TEXT_SAVE = "Search"
        private const val WORDS_SELECTION_SAVE = "Words"
        private const val SECTION_SELECTION_SAVE = "Selection"
        private const val CURRENT_BIBLE_BOOK_SAVE = "BibleBook"

        private const val TAG = "Search"
    }
}
