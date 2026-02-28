/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import android.widget.ImageButton
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import net.bible.android.activity.R

class StepSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.seekBarPreferenceStyle,
    defStyleRes: Int = 0,
) : SeekBarPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource = R.layout.preference_seekbar_stepper
        isSingleLineTitle = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        (holder.findViewById(android.R.id.summary) as? TextView)?.apply {
            isSingleLine = false
            maxLines = Int.MAX_VALUE
            ellipsize = null
        }

        val decrementButton = holder.findViewById(R.id.seekbar_decrement_button) as? ImageButton
        val incrementButton = holder.findViewById(R.id.seekbar_increment_button) as? ImageButton
        decrementButton?.setOnClickListener { adjustByStep(-1) }
        incrementButton?.setOnClickListener { adjustByStep(1) }

        decrementButton?.isEnabled = isEnabled && value > min
        incrementButton?.isEnabled = isEnabled && value < max
    }

    private fun adjustByStep(direction: Int) {
        val step = seekBarIncrement.takeIf { it > 0 } ?: 1
        val nextValue = (value + (step * direction)).coerceIn(min, max)
        if (nextValue != value && callChangeListener(nextValue)) {
            value = nextValue
        }
    }
}
