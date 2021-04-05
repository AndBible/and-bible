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
import kotlinx.android.synthetic.main.font_family_widget.view.*
import kotlinx.android.synthetic.main.text_size_widget.view.*
import kotlinx.android.synthetic.main.text_size_widget.view.dialogMessage
import kotlinx.android.synthetic.main.value_slider_widget.view.*
import net.bible.android.activity.R
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.ProvidedFont
import java.util.*

class FontDefinition(val providedFont: ProvidedFont? = null, val fontFamily: String? = null){
    val realFontFamily: String get() = fontFamily?: providedFont!!.name
    val name: String get() = fontFamily?.replace("-", " ")?.capitalize(Locale.getDefault()) ?: providedFont!!.name

    override fun toString(): String {
        return name
    }
}

val availableFonts:Array<FontDefinition> get() {
    val standard = arrayOf (
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
    return AndBibleAddons.providedFonts.values.map { FontDefinition(providedFont = it) }.toTypedArray() + standard.map { FontDefinition(fontFamily = it) }
}

fun getTypeFace(fontDefinition: FontDefinition): Typeface {
    return when {
        fontDefinition.fontFamily != null -> Typeface.create(fontDefinition.fontFamily, Typeface.NORMAL)
        fontDefinition.providedFont != null -> Typeface.createFromFile(fontDefinition.providedFont.file)
        else -> throw RuntimeException("Illegal value")
    }
}

class FontAdapter(context: Context, resource: Int, private val fontTypes: Array<FontDefinition>) :
    ArrayAdapter<FontDefinition>(context, resource, fontTypes) {


    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        view.typeface = getTypeFace(fontTypes[position])
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent) as TextView
        view.typeface = getTypeFace(fontTypes[position])
        return view

    }
}

class FontSizeWidget(context: Context, attributeSet: AttributeSet?): LinearLayout(context, attributeSet)
{
    var value = WorkspaceEntities.TextDisplaySettings.default.fontSize!!
    var fontFamily: String = ""
    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.text_size_widget, this, true)
        dialogMessage.setText(R.string.prefs_text_size_sample_text)

        fontSizeSlider.max = 60
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            fontSizeSlider.min = 1
        }
        fontSizeSlider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                value = progress
                if(fromUser) updateValue()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })
    }
    
    fun updateValue() {
        val availableFonts = availableFonts
        val fontSize = value
        dialogMessage.textSize = fontSize.toFloat()
        fontSizeValue.text = context.getString(R.string.font_size_pt, fontSize)
        val fontDefinition = availableFonts.find { it.realFontFamily == fontFamily }?:return
        dialogMessage.typeface = getTypeFace(fontDefinition)
        fontSizeSlider.progress = fontSize
    }
    
    companion object {
        fun dialog(context: Context, fontFamily: String, value: Int, resetCallback: (() -> Unit)? = null, callback: (value: Int) -> Unit) {
            AlertDialog.Builder(context).apply{
                val layout = FontSizeWidget(context, null)
                layout.fontFamily = fontFamily
                layout.value = value
                layout.updateValue()
                setTitle(R.string.font_size_title)
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


class TopMarginWidget(context: Context, attributeSet: AttributeSet?): LinearLayout(context, attributeSet)
{
    var value = WorkspaceEntities.TextDisplaySettings.default.topMargin!!
    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.value_slider_widget, this, true)

        valueSlider.max = 60
        valueSlider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                value = progress
                if(fromUser) updateValue()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })
    }

    fun updateValue() {
        val newValue = value
        valueString.text = context.getString(R.string.value_mm, newValue)
        valueSlider.progress = newValue
    }

    companion object {
        fun dialog(context: Context, value: Int, resetCallback: (() -> Unit)? = null, callback: (value: Int) -> Unit) {
            AlertDialog.Builder(context).apply{
                val layout = TopMarginWidget(context, null)
                layout.value = value
                layout.updateValue()
                setTitle(R.string.prefs_top_margin_title)
                setView(layout)
                setPositiveButton(R.string.okay) { dialog, which ->
                    dialog.dismiss()
                    callback(layout.value)
                }
                if(resetCallback != null) {
                    setNeutralButton(R.string.reset_generic) { _, _ -> resetCallback.invoke() }
                }
                setNegativeButton(R.string.cancel) { dialog, which ->
                    dialog.cancel()
                }
                create().show()
            }

        }
    }
}


class FontFamilyWidget(context: Context, attributeSet: AttributeSet?): LinearLayout(context, attributeSet)
{
    var value = WorkspaceEntities.TextDisplaySettings.default.fontFamily!!
    var fontSize: Int = 16
    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.font_family_widget, this, true)
        dialogMessage.setText(R.string.prefs_text_size_sample_text)

        val adapter = FontAdapter(context, R.layout.fontfamily_list_item, availableFonts)
        fontFamily.adapter = adapter

        fontFamily.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                fontFamily.setSelection(0)
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                value = availableFonts[position].realFontFamily
                updateValue()
            }
        }
    }

    fun updateValue() {
        val availableFonts = availableFonts
        val fontFamilyVal = value
        dialogMessage.textSize = fontSize.toFloat()
        val fontDefinition = availableFonts.find { it.realFontFamily == fontFamilyVal }?:return
        dialogMessage.typeface = getTypeFace(fontDefinition)
        fontFamily.setSelection(availableFonts.indexOf(fontDefinition))
    }

    companion object {
        fun dialog(context: Context, fontSize: Int, value: String, resetCallback: (() -> Unit)? = null, callback: (value: String) -> Unit) {
            AlertDialog.Builder(context).apply{
                val layout = FontFamilyWidget(context, null)
                layout.fontSize = fontSize
                layout.value = value
                layout.updateValue()
                setTitle(R.string.pref_font_family_label)
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
