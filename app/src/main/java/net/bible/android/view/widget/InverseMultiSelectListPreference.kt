/*
 * Copyright (c) 2022-2025 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.MultiSelectListPreference

/**
 * A custom MultiSelectListPreference that works inversely to the standard one.
 * It stores values that are NOT selected instead of those that are selected.
 * This is useful when we want most items to be selected by default and only
 * store exceptions.
 */
class InverseMultiSelectListPreference : MultiSelectListPreference {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    /**
     * Stores values that are NOT in the given selectedValues set
     */
    override fun setValues(selectedValues_: MutableSet<String>?) {
        var selectedValues = selectedValues_ ?: mutableSetOf()
        val allValues = entryValues.map { it.toString() }.toSet()
        val valuesToStore = allValues.minus(selectedValues).toMutableSet()
        super.setValues(valuesToStore)
    }
}
