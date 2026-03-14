/*
 * Copyright (c) 2022-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.DialogPreference

/**
 * A custom preference that works inversely to a standard MultiSelectListPreference.
 * It stores values that are NOT selected instead of those that are selected.
 * This is useful when we want most items to be selected by default and only
 * store exceptions.
 */
class InverseMultiSelectListPreference : DialogPreference {
    private var mEntries: Array<CharSequence>? = null
    private var mEntryValues: Array<CharSequence>? = null
    private var mValues = mutableSetOf<String>()
    private var mSelectedItems: BooleanArray? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }
    
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }
    
    constructor(context: Context) : super(context) {
        init(context, null)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        attrs?.let {
            val a = context.obtainStyledAttributes(
                attrs,
                androidx.preference.R.styleable.MultiSelectListPreference
            )

            try {
                // Replace TypedArrayUtils.getTextArray with direct attribute access
                if (a.hasValue(androidx.preference.R.styleable.MultiSelectListPreference_entries)) {
                    mEntries = a.getTextArray(androidx.preference.R.styleable.MultiSelectListPreference_entries)
                } else if (a.hasValue(androidx.preference.R.styleable.MultiSelectListPreference_android_entries)) {
                    mEntries = a.getTextArray(androidx.preference.R.styleable.MultiSelectListPreference_android_entries)
                }

                if (a.hasValue(androidx.preference.R.styleable.MultiSelectListPreference_entryValues)) {
                    mEntryValues = a.getTextArray(androidx.preference.R.styleable.MultiSelectListPreference_entryValues)
                } else if (a.hasValue(androidx.preference.R.styleable.MultiSelectListPreference_android_entryValues)) {
                    mEntryValues = a.getTextArray(androidx.preference.R.styleable.MultiSelectListPreference_android_entryValues)
                }
            } finally {
                a.recycle()
            }
        }
    }

    /**
     * Sets the values that are NOT selected (inverse logic)
     */
    fun setValues(selectedValues_: Set<String>?) {
        mValues = selectedValues_?.toMutableSet() ?: mutableSetOf()
        persistStringSet(mValues)
    }

    var entries: Array<CharSequence>?
        get() = mEntries
        set(value) { mEntries = value }

    var entryValues: Array<CharSequence>?
        get() = mEntryValues
        set(value) { mEntryValues = value }

    override fun onClick() {
        val entryValues = mEntryValues ?: return
        val entries = mEntries ?: return

        val allValues = entryValues.map { it.toString() }.toSet()
        val selectedValues = allValues.minus(mValues)

        mSelectedItems = BooleanArray(entryValues.size) { i ->
            selectedValues.contains(entryValues[i].toString())
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(dialogTitle)
            .setMultiChoiceItems(
                entries, mSelectedItems
            ) { _: DialogInterface, which: Int, isChecked: Boolean ->
                mSelectedItems!![which] = isChecked
            }
            .setPositiveButton(
                android.R.string.ok
            ) { _: DialogInterface, _: Int ->
                val newSelectedValues = mutableSetOf<String>()
                for (i in entryValues.indices) {
                    if (mSelectedItems!![i]) {
                        newSelectedValues.add(entryValues[i].toString())
                    }
                }

                // Apply inverse logic - store values that are NOT selected
                val newValues = allValues.minus(newSelectedValues).toMutableSet()

                if (callChangeListener(newValues)) {
                    mValues = newValues
                    persistStringSet(newValues)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)

        builder.create().show()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        val defaultValues = a.getTextArray(index)
        val result = mutableSetOf<String>()
        
        defaultValues?.forEach {
            result.add(it.toString())
        }
        
        return result
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        setValues(getPersistedStringSet(defaultValue as? Set<String>))
    }
}
