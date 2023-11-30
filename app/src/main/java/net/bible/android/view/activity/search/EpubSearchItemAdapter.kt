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

import android.content.Context
import net.bible.android.control.search.SearchControl
import android.widget.ArrayAdapter
import android.view.ViewGroup
import android.view.LayoutInflater
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.TwoLineListItem
import net.bible.service.common.htmlToSpan
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.KeyAndText
import org.crosswire.jsword.passage.Key
import org.jdom2.Element
import org.jdom2.Text
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern



class EpubSearchItemAdapter(
    searchResultsActivity: EpubSearchResults,
    private val resource: Int,
    _items: List<KeyAndText>,
) : ArrayAdapter<KeyAndText>(
    searchResultsActivity, resource, _items
) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)

        // Pick up the TwoLineListItem defined in the xml file
        val view: TwoLineListItem = if (convertView == null) {
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(resource, parent, false) as TwoLineListItem
        } else {
            convertView as TwoLineListItem
        }

        // Set value for the first text field
        if (view.text1 != null) {
            val key = item!!.key.name
            view.text1.text = key
        }

        // set value for the second text field
        if (view.text2 != null) {
            view.text2.text = htmlToSpan(item!!.text)
        }
        return view
    }
}
