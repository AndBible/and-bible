/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.readingplan

import java.util.BitSet

import net.bible.service.common.CommonUtils
import android.content.SharedPreferences

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class ReadingStatus(
		val planCode: String,
		val day: Int,
		private val numReadings: Int) {

    // there won't be any more than 10 readings per day in any plan
    private val status = BitSet(4)
    val isAllRead: Boolean
        get() {
            for (i in 0 until numReadings) {
                if (!isRead(i)) {
                    return false
                }
            }
            return true
        }

    private val prefsKey: String
        get() = planCode + "_" + day

    init {
        reloadStatus()
    }

    open fun setRead(readingNo: Int) {
        status.set(readingNo)
        saveStatus()
    }

    open fun isRead(readingNo: Int): Boolean {
        return status.get(readingNo)
    }

    fun setAllRead() {
        for (i in 0 until numReadings) {
            setRead(i)
        }
        saveStatus()
    }

    /** do not leave prefs around for historic days
     */
    open fun delete() {
        val prefs = CommonUtils.getSharedPreferences()
        if (prefs.contains(prefsKey)) {
            prefs.edit()
                    .remove(prefsKey)
                    .commit()
        }
    }

    /** read status from prefs string
     */
    open fun reloadStatus() {
        val prefs = CommonUtils.getSharedPreferences()
        val gotStatus = prefs.getString(prefsKey, "")
        for (i in 0 until gotStatus!!.length) {
            if (gotStatus[i] == ONE) {
                status.set(i)
            } else {
                status.clear(i)
            }
        }
    }

    /** serialize read status to prefs in a string
     */
    private fun saveStatus() {
        val strStatus = StringBuffer()
        for (i in 0 until status.length()) {
            if (status.get(i)) {
                strStatus.append(ONE)
            } else {
                strStatus.append(ZERO)
            }
        }
        val prefs = CommonUtils.getSharedPreferences()
        prefs.edit()
                .putString(prefsKey, strStatus.toString())
                .commit()
    }

    companion object {
        private val ONE = '1'
        private val ZERO = '0'
    }

}
