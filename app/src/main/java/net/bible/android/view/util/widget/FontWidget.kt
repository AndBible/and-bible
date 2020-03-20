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
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.text_size_widget.view.*
import net.bible.android.activity.R
import net.bible.android.database.WorkspaceEntities


val availableFonts = arrayOf(
    "sans-serif-thin",
    "sans-serif-light",
    "sans-serif",
    "sans-serif-medium",
    "sans-serif-black",
    "sans-serif-condensed-light",
    "sans-serif-condensed",
    "sans-serif-condensed-medium",
    "sans-serif-condensed",
    "serif",
    "monospace",
    "serif-monospace",
    "casual",
    "cursive",
    "sans-serif-smallcaps"
)


class FontAdapter(context: Context, resource: Int, private val fontTypes: Array<String>) :
    ArrayAdapter<String>(context, resource, fontTypes) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        val tf = Typeface.create(fontTypes[position], Typeface.NORMAL)
        view.typeface = tf
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent) as TextView
        val tf = Typeface.create(fontTypes[position], Typeface.NORMAL)
        view.typeface = tf
        return view

    }
}

class FontWidget(context: Context, attributeSet: AttributeSet?): LinearLayout(context, attributeSet)
{
    var value = WorkspaceEntities.TextDisplaySettings.default.font!!
    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.text_size_widget, this, true)
        dialogMessage.setText(R.string.prefs_text_size_sample_text)

        val adapter = FontAdapter(context, R.layout.fontfamily_list_item, availableFonts)
        fontFamily.adapter = adapter

        fontFamily.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                fontFamily.setSelection(0)
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                value.fontFamily = availableFonts[position]
                updateValue()
            }
        }
        fontSizeSlider.max = 60
        fontSizeSlider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                value.fontSize = progress
                updateValue()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })
    }
    
    fun updateValue() {
        val fontSize = value.fontSize!!
        val fontFamilyVal = value.fontFamily!!
        dialogMessage.textSize = fontSize.toFloat()
        fontSizeValue.text = context.getString(R.string.font_size_pt, fontSize)
        val tf = Typeface.create(fontFamilyVal, Typeface.NORMAL)
        dialogMessage.typeface = tf
        fontSizeSlider.progress = fontSize
        fontFamily.setSelection(availableFonts.indexOf(fontFamilyVal))

    }
    
    companion object {
        fun dialog(context: Context, value: WorkspaceEntities.Font, resetCallback: (() -> Unit)? = null, callback: (value: WorkspaceEntities.Font) -> Unit) {
            AlertDialog.Builder(context).apply{
                val layout = FontWidget(context, null)
                layout.value = value
                layout.updateValue()
                setTitle(R.string.prefs_text_size_title)
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
