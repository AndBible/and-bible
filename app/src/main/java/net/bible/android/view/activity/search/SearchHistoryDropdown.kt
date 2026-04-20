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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView

internal fun AutoCompleteTextView.setupSearchHistoryDropdown(
    context: Context,
    history: List<String>
) {
    val adapter = ArrayAdapter(
        context,
        android.R.layout.simple_dropdown_item_1line,
        history
    )
    setAdapter(adapter)
    setOnItemClickListener { _, _, _, _ ->
        // AutoCompleteTextView has already set selected text at this point.
        setSelection(text.length)
    }
    setOnClickListener {
        if (adapter.count > 0) {
            showDropDown()
        }
    }
    setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus && adapter.count > 0 && text.isNullOrEmpty()) {
            post { showDropDown() }
        }
    }
}
