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
package net.bible.android.view.activity.navigation.genbookmap

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import net.bible.android.activity.R
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.activity.page.MainBibleActivity
import org.crosswire.jsword.passage.Key
import java.util.*
import javax.inject.Inject

/** show a list of keys and allow to select an item
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class ChooseKeyBase : ListActivityBase() {
    private val mKeyList: MutableList<Key> = ArrayList()
    private var mKeyArrayAdapter: ArrayAdapter<Key>? = null

    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    abstract val currentKey: Key?
    abstract val keyList: List<Key>?
    abstract fun itemSelected(key: Key)

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Key chooser")
        setContentView(R.layout.choose_general_book_key)
        buildActivityComponent().inject(this)
        prepareList()
        mKeyArrayAdapter = KeyItemAdapter(this, LIST_ITEM_TYPE, mKeyList)
        listAdapter = mKeyArrayAdapter

        // if an item was selected previously then try to scroll to it
        val currentKey = currentKey
        if (currentKey != null && mKeyList.contains(currentKey)) {
            setSelection(mKeyList.indexOf(currentKey))
        }
        Log.d(TAG, "Finished displaying Search view")
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    protected fun prepareList() {
        Log.d(TAG, "Getting book keys")
        mKeyList.clear()
        try {
            for (key in keyList ?: emptyList()) {
                mKeyList.add(key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting key")
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            val selected = mKeyList[position]
            Log.i(TAG, "Selected:$selected")
            itemSelected(selected)
            returnToMainScreen()
        } catch (e: Exception) {
            Log.e(TAG, "Selection error", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "Activity result:$resultCode")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == FINISHED) {
            Log.i(TAG, "Leaf key selected so finish")
            returnToMainScreen()
        }
    }

    private fun returnToMainScreen() {
        // just pass control back to the main screen
        val resultIntent = Intent(this, MainBibleActivity::class.java)
        setResult(FINISHED, resultIntent)
        finish()
    }

    companion object {
        private const val FINISHED = 99
        private const val TAG = "ChooseKeyBase"
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_1
    }
}
