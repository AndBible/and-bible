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
import android.content.DialogInterface
import android.content.res.TypedArray
import android.os.Bundle
import android.util.AttributeSet
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceDialogFragmentCompat
import java.util.HashSet

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
     * Returns all entry values that are NOT in the stored set
     */
    override fun getValues(): MutableSet<String> {
        val allValues = entryValues.map { it.toString() }.toSet()
        val storedValues = super.getValues()
        return allValues.minus(storedValues).toMutableSet()
    }

    /**
     * Stores values that are NOT in the given selectedValues set
     */
    override fun setValues(selectedValues: MutableSet<String>?) {
        if (selectedValues == null) {
            super.setValues(mutableSetOf())
            return
        }

        val allValues = entryValues.map { it.toString() }.toSet()
        val valuesToStore = allValues.minus(selectedValues).toMutableSet()
        super.setValues(valuesToStore)
    }

    /**
     * Gets the default value from xml
     */
    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        val defaultValues = super.onGetDefaultValue(a, index)
        // For an empty default set, we want to store an empty set
        // (which means all values are selected in the UI)
        return defaultValues
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        if (defaultValue == null) {
            // If no default value is specified, we want everything to be selected
            // which means storing an empty set
            super.setValues(mutableSetOf())
        } else if (defaultValue is Set<*>) {
            val allValues = entryValues.map { it.toString() }.toSet()
            
            // If the default is an empty set, it means all options should be selected
            // in the UI, which means we should store an empty set (no excluded values)
            if (defaultValue.isEmpty()) {
                super.setValues(mutableSetOf())
            } else {
                // Otherwise, convert the default selected values to the inverse (unselected) values
                val initialSelectedValues = defaultValue.filterIsInstance<String>().toSet()
                val valuesToStore = allValues.minus(initialSelectedValues).toMutableSet()
                super.setValues(valuesToStore)
            }
        } else {
            // Handle any other type of default value - pass to parent
            super.onSetInitialValue(defaultValue)
        }
    }

    /**
     * Custom dialog fragment to handle the inverse multi-selection logic
     */
    class InverseMultiSelectListPreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat() {
        private val preference: InverseMultiSelectListPreference
            get() = super.preference as InverseMultiSelectListPreference

        private val entryValues: Array<CharSequence>
            get() = preference.entryValues

        private lateinit var checkedItems: BooleanArray
        private val newValues = HashSet<String>()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            
            if (savedInstanceState == null) {
                val values = preference.values
                val entryValues = entryValues
                checkedItems = BooleanArray(entryValues.size)
                
                for (i in entryValues.indices) {
                    val entryValue = entryValues[i].toString()
                    checkedItems[i] = values.contains(entryValue)
                }
            } else {
                checkedItems = savedInstanceState.getBooleanArray(SAVE_STATE_CHECKED) ?: BooleanArray(0)
            }
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putBooleanArray(SAVE_STATE_CHECKED, checkedItems)
        }

        override fun onPrepareDialogBuilder(builder: androidx.appcompat.app.AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            
            builder.setMultiChoiceItems(
                preference.entries, checkedItems
            ) { _: DialogInterface?, which: Int, isChecked: Boolean ->
                checkedItems[which] = isChecked
            }
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val entryValues = entryValues
                
                for (i in entryValues.indices) {
                    if (checkedItems[i]) {
                        newValues.add(entryValues[i].toString())
                    }
                }
                
                if (preference.callChangeListener(newValues)) {
                    preference.values = newValues
                }
            }
        }

        companion object {
            private const val SAVE_STATE_CHECKED = "MultiSelectListPreferenceDialogFragment.checked"

            fun newInstance(key: String): InverseMultiSelectListPreferenceDialogFragmentCompat {
                val fragment = InverseMultiSelectListPreferenceDialogFragmentCompat()
                val bundle = Bundle(1)
                bundle.putString(ARG_KEY, key)
                fragment.arguments = bundle
                return fragment
            }
        }
    }

    companion object {
        // Method to register the preference dialog fragment in the preference manager
        fun registerPreferenceFragment() {
            // This would typically be called in your Application class or main activity
            // PreferenceManager.setDialogPreferenceFragmentFactory(
            //     InverseMultiSelectListPreference::class.java,
            //     InverseMultiSelectListPreferenceDialogFragmentCompat::class.java
            // )
        }
    }
}
