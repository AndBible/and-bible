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

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import kotlinx.android.synthetic.main.preference_seekbar.*
import net.bible.android.activity.R


//class SeekBarDialog: PreferenceDialogFragmentCompat() {
//
//	override fun onBindDialogView(v: View?) {
//		super.onBindDialogView(v)
//		dialogMessage.text = dialogMessage
//		//val actualValue = v.findViewById<View>(R.id.actualValue) as TextView
//		//val myBar = v.findViewById<View>(R.id.myBar) as SeekBar
//		myBar.setOnSeekBarChangeListener(this)
//		myBar.max = mMax - mMin
//		myBar.progress = mValue - mMin
//		val t = mValue.toString()
//		actualValue!!.text = if (mSuffix == null) t else t + mSuffix
//	}
//	override fun onDialogClosed(positiveResult: Boolean) {
//		if (positiveResult) {
//			val value = mSeekBar!!.progress + mMin
//			if (callChangeListener(value)) {
//				setValue(value)
//			}
//		}
//	}
//}
//
///**
// * @author Martin Denham [mjdenham at gmail dot com]
// */
//open class SeekBarPreference(context: Context?, attrs: AttributeSet) : DialogPreference(context, attrs), SeekBar.OnSeekBarChangeListener {
//    private var mSeekBar: SeekBar? = null
//    var dialogMessageView: TextView? = null
//        private set
//    private var mValueText: TextView? = null
//    private val mSuffix: String?
//    private var mMax: Int
//    private var mMin: Int
//    private var mValue = 0
//
//    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
//        return a.getInt(index, 0)
//    }
//
//    override fun onSetInitialValue(restore: Boolean, defaultValue: Any?) {
//        mValue = getPersistedInt(if (defaultValue == null) 0 else (defaultValue as Int))
//    }
//
//    fun setValue(value: Int) {
//        var value = value
//        if (value > mMax) {
//            value = mMax
//        } else if (value < 0) {
//            value = 0
//        }
//        mValue = value
//        persistInt(value)
//    }
//
//    fun setMax(max: Int) {
//        mMax = max
//        if (mValue > mMax) {
//            setValue(mMax)
//        }
//    }
//
//    fun setMin(min: Int) {
//        if (min < mMax) {
//            mMin = min
//        }
//    }
//
//    /** update text displays reflecting new value
//     * called as a result of changing progresBar
//     * @param value
//     */
//    protected open fun updateScreenValue(value: Int) {
//        val t = value.toString()
//        mValueText!!.text = if (mSuffix == null) t else t + mSuffix
//    }
//
//    override fun onProgressChanged(seek: SeekBar, value: Int, fromTouch: Boolean) {
//        val newValue = value + mMin
//        updateScreenValue(newValue)
//    }
//
//    override fun onStartTrackingTouch(seek: SeekBar) {}
//    override fun onStopTrackingTouch(seek: SeekBar) {}
//
//
//
//    companion object {
//        private const val androidns = "http://schemas.android.com/apk/res/android"
//        private const val TAG = "SeekBarPreference"
//    }
//
//    init {
//        isPersistent = true
//        mSuffix = attrs.getAttributeValue(androidns, "text")
//        mMin = attrs.getAttributeIntValue(androidns, "min", 0)
//        mMax = attrs.getAttributeIntValue(androidns, "max", 100)
//        dialogLayoutResource = R.layout.preference_seekbar
//    }
//}
