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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import net.bible.service.common.ABStringUtils
import org.apache.commons.lang3.text.WordUtils
import org.crosswire.jsword.passage.Key

/**
 * Retain similar style to TwoLineListView but for single TextView on each line
 * @author denha1m
 */
class KeyItemAdapter(
    context: Context,
    private val resource: Int,
    items: List<Key>) : ArrayAdapter<Key>(context, resource, items)
{
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)!!

        // Pick up the TwoLineListItem defined in the xml file
        val view: TextView
        view = if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(resource, parent, false) as TextView
        } else {
            convertView as TextView
        }

        // Set value for the first text field
        var key = item.osisID
        // make all uppercase in Calvin's Institutes look nicer
        if (ABStringUtils.isAllUpperCaseWherePossible(key)) {
            key = WordUtils.capitalizeFully(key)
        }
        view.text = key
        return view
    }

}
