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

package net.bible.android.view.util.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.line_spacing_widget.view.*
import net.bible.android.activity.R
import net.bible.android.database.WorkspaceEntities


class LineSpacingWidget(context: Context, attributeSet: AttributeSet?): LinearLayout(context, attributeSet)
{
    var value = WorkspaceEntities.TextDisplaySettings.default.lineSpacing!!
    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.line_spacing_widget, this, true)

        lineSpacing.max = 20
        lineSpacing.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                value = progress + 10
                updateValue()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })
    }
    
    fun updateValue() {
        val lineSpacingVal = value
        lineSpacingValue.text = context.getString(R.string.prefs_line_spacing_pt, lineSpacingVal.toFloat() / 10.0)
        lineSpacing.progress = lineSpacingVal - 10
    }
    
    companion object {
        fun dialog(context: Context, value: Int, resetCallback: (() -> Unit)? = null, callback: (value: Int) -> Unit) {
            AlertDialog.Builder(context).apply{
                val layout = LineSpacingWidget(context, null)
                layout.value = value
                layout.updateValue()
                setTitle(R.string.line_spacing_title)
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
