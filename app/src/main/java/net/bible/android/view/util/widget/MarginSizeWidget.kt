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

package net.bible.android.view.util.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import net.bible.android.activity.R
import net.bible.android.activity.databinding.MarginSizeWidgetBinding
import net.bible.android.database.WorkspaceEntities

fun createListener(func: (progress: Int, fromUser: Boolean) -> Unit): SeekBar.OnSeekBarChangeListener {
    return object: SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            func(progress, fromUser)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
        }
    }
}

class MarginSizeWidget(context: Context, attributeSet: AttributeSet?): LinearLayout(context, attributeSet)
{
    lateinit var value: WorkspaceEntities.MarginSize
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val bindings = MarginSizeWidgetBinding.inflate(inflater, this, true)
    init {
        bindings.apply {
            leftMargin.max = 30
            rightMargin.max = 30
            maxWidth.max = 500

            leftMargin.setOnSeekBarChangeListener(createListener { it, fromUser ->
                value.marginLeft = it
                if (fromUser) updateValue()
            })

            rightMargin.setOnSeekBarChangeListener(createListener { it, fromUser ->
                value.marginRight = it
                if (fromUser) updateValue()
            })
            maxWidth.setOnSeekBarChangeListener(createListener { it, fromUser ->
                value.maxWidth = it
                if (fromUser) updateValue()
            })
        }
    }
    
    fun updateValue() = bindings.apply {
        leftMarginLabel.text = context.getString(R.string.pref_left_margin_label_mm, value.marginLeft)
        rightMarginLabel.text = context.getString(R.string.pref_right_margin_label_mm, value.marginRight)
        maxTextWidthLabel.text = context.getString(R.string.pref_maximum_width_of_text_label_mm, value.maxWidth)
        leftMargin.progress = value.marginLeft!!
        rightMargin.progress = value.marginRight!!
        maxWidth.progress = value.maxWidth ?: WorkspaceEntities.TextDisplaySettings.default.marginSize!!.maxWidth!!
    }
    
    companion object {
        fun dialog(context: Context, value: WorkspaceEntities.MarginSize, resetCallback: (() -> Unit)? = null, callback: (value: WorkspaceEntities.MarginSize) -> Unit) {
            AlertDialog.Builder(context).apply{
                val layout = MarginSizeWidget(context, null)
                layout.value = value
                layout.updateValue()
                setTitle(R.string.prefs_margin_size_title)
                setView(layout)
                setPositiveButton(R.string.okay) { dialog, which ->
                    dialog.dismiss()
                    callback(layout.value)
                }
                if(resetCallback != null) {
                    setNeutralButton(R.string.reset_fontsize) { _, _ -> resetCallback.invoke() }
                }
                setNegativeButton(R.string.cancel) { dialog, which ->
                    dialog.cancel()
                }
                create().show()
            }

        }
    }
}
