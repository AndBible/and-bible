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
package net.bible.android.view.activity.bookmark

import android.content.Context
import android.os.Build
import android.text.Html
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BookmarkListItemBinding
import net.bible.android.common.toV11n
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.service.sword.SwordContentFacade

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkItemAdapter(
    context: Context,
    items: List<Bookmark>,
    private val bookmarkControl: BookmarkControl,
    private val swordContentFacade: SwordContentFacade,
    private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
) : ArrayAdapter<Bookmark>(context, R.layout.bookmark_list_item, items) {
    private lateinit var bindings: BookmarkListItemBinding

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)!!

        val bindings = when(convertView) {
            null -> {
                val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                BookmarkListItemBinding.inflate(inflater, parent, false)
            }
            else -> BookmarkListItemBinding.bind(convertView)
        }

        val labels = bookmarkControl.labelsForBookmark(item)
        val isSpeak = labels.contains(bookmarkControl.speakLabel)
        if (isSpeak) {
            bindings.speakIcon.visibility = View.VISIBLE
        } else {
            bindings.speakIcon.visibility = View.GONE
        }
        bindings.bookmarkIcons.removeAllViews()
        for (it in labels.filterNot { it.isSpeakLabel }) {
            val v = ImageView(bindings.bookmarkIcons.context)
            v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            v.setImageResource(R.drawable.ic_label_24dp)
            v.setColorFilter(it.color)
            bindings.bookmarkIcons.addView(v)
        }

        // Set value for the first text field
        val versification = activeWindowPageManagerProvider.activeWindowPageManager.currentBible.versification
        val verseName = item.verseRange.toV11n(versification).name
        val book = item.speakBook
        if (isSpeak && book != null) {
            bindings.verseText.text = context.getString(R.string.something_with_parenthesis, verseName, book.abbreviation)
        } else {
            bindings.verseText.text = verseName
        }
        if(item.notes !== null) {
            bindings.notesText.visibility = View.VISIBLE
            try {
                val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(item.notes, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    Html.fromHtml(item.notes)
                }

                bindings.notesText.text = spanned
            } catch (e: Exception) {
                Log.e(TAG, "Error loading label verse text", e)
                bindings.notesText.visibility = View.GONE
            }
        } else {
            bindings.notesText.visibility = View.GONE
        }

        // Set value for the date text field
        val sDt = DateFormat.format("yyyy-MM-dd HH:mm", item.createdAt).toString()
        bindings.dateText.text = sDt

        // set value for the second text field
        try {
            val verseText = swordContentFacade.getBookmarkVerseText(item)
            bindings.verseContentText.text = verseText
        } catch (e: Exception) {
            Log.e(TAG, "Error loading label verse text", e)
            bindings.verseContentText.text = ""
        }
        return convertView ?: bindings.root
    }

    companion object {
        private const val TAG = "BookmarkItemAdapter"
    }

}
