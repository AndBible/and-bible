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
package net.bible.android.view.activity.mynote

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.list_item_2_highlighted.view.*
import net.bible.android.control.mynote.MyNoteControl
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.android.view.util.widget.TwoLineListItem
import net.bible.service.db.mynote.MyNoteDto

/**
 * Display a single Note in a list row
 *
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class MyNoteItemAdapter(_context: Context?, private val resource: Int, _items: List<MyNoteDto?>?, private val actionModeActivity: ActionModeActivity, private val myNoteControl: MyNoteControl) : ArrayAdapter<MyNoteDto?>(_context!!, resource, _items!!) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)

        // Pick up the TwoLineListItem defined in the xml file
        val view: TwoLineListItem
        view = if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(resource, parent, false) as TwoLineListItem
        } else {
            convertView as TwoLineListItem
        }

        // Set value for the first text field
        if (view.text1 != null) {
            val key = myNoteControl.getMyNoteVerseKey(item!!)
            view.text1.text = key
        }

        // set value for the second text field
        if (view.text2 != null) {
            try {
                val noteText = myNoteControl.getMyNoteText(item!!, true)
                view.text2.text = noteText
            } catch (e: Exception) {
                Log.e(TAG, "Error loading label verse text", e)
                view.text2.text = ""
            }
        }
        return view
    }

    companion object {
        private const val TAG = "UserNoteItemAdapter"
    }

}
