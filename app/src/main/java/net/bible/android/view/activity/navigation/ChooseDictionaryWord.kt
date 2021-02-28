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
package net.bible.android.view.activity.navigation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ChooseDictionaryPageBinding
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActivityBase
import org.crosswire.jsword.passage.Key
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
    private lateinit var mMatchingKeyList: MutableList<Key>
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ChooseDictionaryPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        // ensure there is actually a dictionary
        if (activeWindowPageManagerProvider
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
        Log.d(TAG, "Initialising")
        // setting up an initially empty list of matches
        mMatchingKeyList = ArrayList()
        listAdapter = ArrayAdapter(this@ChooseDictionaryWord,
            LIST_ITEM_TYPE,
            mMatchingKeyList)

        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                binding.loadingIndicator.visibility = View.VISIBLE
            }
            try {
                // getting all dictionary keys is slow so do in another thread in order to show hourglass
                //TODO need to optimise this using binary search of globalkeylist without caching

                //already checked a dictionary exists
                mDictionaryGlobalList = activeWindowPageManagerProvider
                    .activeWindowPageManager
                    .currentDictionary
                    .cachedGlobalKeyList
                Log.d(TAG, "Finished Initialising")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating dictionary key list")
                instance.showErrorMsg(R.string.error_occurred, e)
            } finally {
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                }
            }
        }
    }

    /** user has typed something so show keys starting with user's text
     * @param _searchText
     */
    private fun showPossibleDictionaryKeys(_searchText: String) {
        var searchText = _searchText
        Log.d(TAG, "Search for:$searchText")
        try {
            if (mDictionaryGlobalList != null) {
                searchText = searchText.toLowerCase(Locale.getDefault())
                val iter = mDictionaryGlobalList!!.iterator()
                mMatchingKeyList.clear()
                while (iter.hasNext()) {
                    val key = iter.next()
                    if (key.name.toLowerCase(Locale.getDefault()).contains(searchText)) {
                        mMatchingKeyList.add(key)
                    }
                }
                Log.d(TAG, "matches found:" + mMatchingKeyList.size)
                notifyDataSetChanged()
                Log.d(TAG, "Finished searching for:$searchText")
            } else {
                Log.d(TAG, "Cached global key list is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding matching keys", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        itemSelected(mMatchingKeyList[position])
    }

    private fun itemSelected(selectedKey: Key?) {
        try {
            if (selectedKey != null) {
                Log.i(TAG, "chose:$selectedKey")
                activeWindowPageManagerProvider.activeWindowPageManager.currentDictionary.setKey(selectedKey)
                activeWindowPageManagerProvider.activeWindowPageManager.setCurrentDocument(activeWindowPageManagerProvider.activeWindowPageManager.currentDictionary.currentDocument)
                doFinish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Key not found", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun doFinish() {
        val resultIntent = Intent()
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_1
        private const val TAG = "ChooseDictionaryWord"
    }
}
