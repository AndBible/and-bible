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
package net.bible.android.view.activity.download

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import net.bible.android.activity.R
import net.bible.android.control.download.DownloadControl
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.service.common.CommonUtils.getResourceColor
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.versification.system.SystemKJV

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentDownloadItemAdapter(_context: Context?, private val downloadControl: DownloadControl, private val resource: Int, items: List<Book?>?, private val actionModeActivity: ActionModeActivity) : ArrayAdapter<Book?>(_context!!, resource, items!!) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val document = getItem(position)

        // Pick up the TwoLineListItem defined in the xml file
        val view: DocumentDownloadListItem
        view = if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(resource, parent, false) as DocumentDownloadListItem
        } else {
            convertView as DocumentDownloadListItem
        }

        // remember which item is being shown
        view.document = document
        view.updateControlState(downloadControl.getDocumentStatus(document))

        // Set value for the first text field
        if (view.text1 != null) {
            // eBible repo uses abbreviation for initials and initials now contains the repo name!!!
            // but helpfully JSword uses initials if abbreviation does not exist, as will be the case for all other repos.
            val initials = document!!.abbreviation
            view.text1.text = initials
        }

        // set value for the second text field
        if (view.text2 != null) {
            var name = document!!.name
            if (document is AbstractPassageBook) {
                val bible = document
                // display v11n name if not KJV
                if (SystemKJV.V11N_NAME != bible.versification.name) {
                    name += " (" + bible.versification.name + ")"
                }
            }
            view.text2.text = name
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            if (actionModeActivity.isItemChecked(position)) {
                view.setBackgroundColor(ACTIVATED_COLOUR)
            } else {
                view.setBackgroundColor(Color.TRANSPARENT)
            }
        }
        return view
    }

    companion object {
        private val ACTIVATED_COLOUR = getResourceColor(R.color.list_item_activated)
    }

}
