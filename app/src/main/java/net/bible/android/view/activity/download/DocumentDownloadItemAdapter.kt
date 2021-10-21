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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import net.bible.android.activity.R
import net.bible.android.activity.databinding.DocumentListItemBinding
import net.bible.android.control.download.DownloadControl
import net.bible.android.view.activity.base.DocumentSelectionBase
import net.bible.android.view.activity.base.RecommendedDocuments
import net.bible.service.common.Ref
import org.crosswire.jsword.book.Book

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentDownloadItemAdapter(
    val activity: DocumentSelectionBase,
    private val downloadControl: DownloadControl,
    private val recommendedDocuments: Ref<RecommendedDocuments>
) : ArrayAdapter<Book>(activity, R.layout.document_list_item, ArrayList<Book>())
{
    private lateinit var bindings: DocumentListItemBinding

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val document = getItem(position)!!

        bindings = if (convertView == null) {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            DocumentListItemBinding.inflate(inflater, parent, false)
        } else {
            DocumentListItemBinding.bind(convertView)
        }

        val view = (convertView?: bindings.root) as DocumentListItem
        view.binding = bindings

        // remember which item is being shown
        view.document = document
        view.recommendedDocuments = recommendedDocuments.value

        view.setIcons(downloadScreen = true)
        bindings.undoButton.setOnClickListener {
            downloadControl.cancelDownload(document)
        }

        bindings.aboutButton.setOnClickListener {
            activity.handleAbout(listOf(document))
        }

        view.updateControlState(downloadControl.getDocumentStatus(document))

        // Set value for the first text field
        // eBible repo uses abbreviation for initials and initials now contains the repo name!!!
        // but helpfully JSword uses initials if abbreviation does not exist, as will be the case for all other repos.
        val initials = document.abbreviation
        bindings.documentAbbreviation.text = initials

        // set value for the second text field
        bindings.documentName.text = document.name
        return view
    }
}
