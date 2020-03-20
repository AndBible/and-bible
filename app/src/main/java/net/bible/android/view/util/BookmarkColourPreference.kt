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
package net.bible.android.view.util

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.PreferenceDialogFragmentCompat
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkStyle

/**
 * Allow selection of default Bookmark colour preference.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class BookmarkColorPreferenceDialog(private val selectedPosition: Int):
	ListPreferenceDialogFragmentCompat() {
	override fun onPrepareDialogBuilder(builder: AlertDialog.Builder?) {
		super.onPrepareDialogBuilder(builder)
		val pref = preference as BookmarkColourPreference
		val adapter = ArrayAdapter<Int>(context!!, R.layout.listitem)
		for(e in pref.entries) {
			adapter.add(1)
		}
		val fontTypeAdapter = BookmarkColourListPrefWrapperAdapter(context!!, adapter, selectedPosition)
		// Adjust the selection because resetting the adapter loses the selection.
		builder?.setAdapter(fontTypeAdapter) {dialog, value ->
			pref.value = pref.entries[value].toString()
			dialog.dismiss()
		}
	}
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog
		//val dialog = dialog as AlertDialog
		val listView = dialog.listView

		if (selectedPosition != -1) {
			listView.setItemChecked(selectedPosition, true)
			listView.setSelection(selectedPosition)
		}
		return dialog
	}

	companion object {
		fun newInstance(key: String, selectedPosition: Int): BookmarkColorPreferenceDialog {
			val f = BookmarkColorPreferenceDialog(selectedPosition)
			val b = Bundle(1)
			b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
			f.arguments = b
			return f
		}

	}
}

class BookmarkColourPreference : ListPreference {
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initialise()
    }

    constructor(context: Context?) : super(context) {
        initialise()
    }

    private fun initialise() {
        val numValues = BookmarkStyle.values().size - 1 // Do not count Speak bookmark style
        val styles = arrayOfNulls<CharSequence>(numValues)
        for (i in 0 until numValues) {
            val bookmarkStyle = BookmarkStyle.values()[i]
            styles[i] = bookmarkStyle.name
        }
        entries = styles
        entryValues = styles
        setDefaultValue(styles[0])
    }
}
