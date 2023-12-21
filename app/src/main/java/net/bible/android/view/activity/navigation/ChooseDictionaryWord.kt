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
package net.bible.android.view.activity.navigation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ChooseDictionaryPageBinding
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade.readOsisFragment
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.jdom2.Element
import java.util.*
import javax.inject.Inject

/**
 * Choose a bible or commentary to use
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ChooseDictionaryWord : ListActivityBase() {

    private lateinit var binding: ChooseDictionaryPageBinding

    private var mDictionaryGlobalList: List<Key>? = null

    /**
     * Internal class to help show additional information in the
     * dictionary word picker, rather than just a key.
     */
    private class KeyInfo(_key: Key, _book: Book ) {
        val key: Key = _key;
        val book: Book = _book;

        companion object {

            private fun cleanUpSnippet(snippet: String, key: String): String {
                var noNewLines = snippet.replace('\n', ' ');
                if (noNewLines.startsWith(key)) {
                    noNewLines = noNewLines.substring(key.length)
                }
                return maxLettersWholeWords(noNewLines);
            }

            /**
             * Returns the first few words in text, up to a maximum of max letters.
             * @param text The text to trim down.
             * @param max Maximum number of letters in the returned string.
             */
            private fun maxLettersWholeWords(text: String, max: Int = 50): String {
                val words = text.split(' ').toMutableList();
                var result = "";
                while (result.length < max && words.size > 0) {
                    result += words[0] + ' ';
                    words.removeAt(0);
                }
                if (result.isNotEmpty()) {
                    // remove the extra space
                    result.dropLast(1);
                }
                val append = if (words.isNotEmpty()) "..." else "";
                return "$result$append";
            }

            private fun getEntrySnippet(text: Element, key: String): String {
                text.removeChild("title")
                val entry = text
                    .getChild("entryFree")
                if (entry == null) {
                    return cleanUpSnippet(text.value, key);
                }

                // if a greek or hebrew word, look up any orthographic entries.
                val greekOrHebrewWord =  entry
                    .getChildren("orth")
                    ?.map { it.text }
                    ?.filter { it != "" }
                    ?.joinToString(" - ")
                    ?: "";

                if (greekOrHebrewWord != "") {
                    return greekOrHebrewWord
                }

                // return the first 100 chars or so of the entry
                return cleanUpSnippet(entry.value, key);

            }
        }

        override fun toString(): String {
            val text = try  { readOsisFragment(book, key) } catch (e: OsisError) {e.xml}
            val snippet = getEntrySnippet(text, key.toString())
            return if (snippet != "")
                "$key - $snippet"
             else
                key.toString();
        }
    }
    private lateinit var mMatchingKeyList: MutableList<KeyInfo>
    @Inject lateinit var windowControl: WindowControl

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ChooseDictionaryPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        // ensure there is actually a dictionary
        if (windowControl
                .activeWindowPageManager
                .currentDictionary
                .currentDocument == null) {
            Log.e(TAG, "No Dictionary")
            finish()
            return
        }
        initialise()
        binding.searchText.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(searchText: CharSequence, arg1: Int, arg2: Int, arg3: Int) {
                showPossibleDictionaryKeys(searchText.toString())
            }

            override fun afterTextChanged(searchText: Editable) {}
            override fun beforeTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) {}
        })
        binding.searchText.requestFocus()
    }

    /**
     * init the list adapter for the current list activity
     * @return
     */
    private fun initialise() {
        Log.i(TAG, "Initialising")
        // setting up an initially empty list of matches
        mMatchingKeyList = ArrayList()
        listAdapter = ArrayAdapter(this@ChooseDictionaryWord,
            LIST_ITEM_TYPE,
            mMatchingKeyList)
        mList?.isFastScrollEnabled = true


        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                binding.loadingIndicator.visibility = View.VISIBLE
            }
            try {
                // getting all dictionary keys is slow so do in another thread in order to show hourglass
                //TODO need to optimise this using binary search of globalkeylist without caching

                //already checked a dictionary exists
                mDictionaryGlobalList = windowControl
                    .activeWindowPageManager
                    .currentDictionary
                    .cachedGlobalKeyList
                Log.i(TAG, "Finished Initialising")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating dictionary key list")
                Dialogs.showErrorMsg(R.string.error_occurred, e)
            } finally {
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    showPossibleDictionaryKeys("")
                }
            }
        }
    }

    /** user has typed something so show keys starting with user's text
     * @param _searchText
     */
    private fun showPossibleDictionaryKeys(_searchText: String) {
        var searchText = _searchText
        Log.i(TAG, "Search for:$searchText")
        try {
            if (mDictionaryGlobalList != null) {
                searchText = searchText.lowercase(Locale.getDefault())
                val iter = mDictionaryGlobalList!!.iterator()
                mMatchingKeyList.clear()
                val book = windowControl.activeWindowPageManager.currentDictionary.currentDocument!!
                while (iter.hasNext()) {
                    val key = iter.next()
                    if (key.name.lowercase(Locale.getDefault()).contains(searchText)) {
                        mMatchingKeyList.add(KeyInfo(key, book))
                    }
                }
                Log.i(TAG, "matches found:" + mMatchingKeyList.size)
                notifyDataSetChanged()
                Log.i(TAG, "Finished searching for:$searchText")
            } else {
                Log.i(TAG, "Cached global key list is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding matching keys", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        itemSelected(mMatchingKeyList[position].key)
    }

    private fun itemSelected(selectedKey: Key) {
        val myIntent = Intent(this, ChooseGeneralBookKey::class.java)
        myIntent.putExtra("key", selectedKey.osisRef)
        setResult(Activity.RESULT_OK, myIntent)
        val curDoc = windowControl.activeWindowPageManager.currentDictionary.currentDocument!!
        myIntent.putExtra("book", curDoc.initials)
        finish()
    }

    companion object {
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_1
        private const val TAG = "ChooseDictionaryWord"
    }
}
