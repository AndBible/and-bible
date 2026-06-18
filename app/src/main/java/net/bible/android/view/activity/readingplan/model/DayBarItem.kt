/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.readingplan.model

import java.util.Date

data class DayBarItem (
    val dayNumber: Int,
    val date: Date,
    /** This day's readings are being shown in daily reading */
    var dayActive: Boolean,
    var dayReadPartial: Boolean,
    var dayReadComplete: Boolean,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DayBarItem

        if (dayNumber != other.dayNumber) return false
        if (date != other.date) return false
        if (dayActive != other.dayActive) return false
        if (dayReadPartial != other.dayReadPartial) return false
        if (dayReadComplete != other.dayReadComplete) return false

        return true
    }
    override fun hashCode(): Int {
        var result = dayNumber
        result = 31 * result + date.hashCode()
        result = 31 * result + dayActive.hashCode()
        result = 31 * result + dayReadPartial.hashCode()
        result = 31 * result + dayReadComplete.hashCode()
        return result
    }
}
