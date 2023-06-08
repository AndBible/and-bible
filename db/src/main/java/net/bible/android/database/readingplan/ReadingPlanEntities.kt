/*
 * Copyright (c) 2019-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.readingplan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.bible.android.database.IdType
import java.util.Date

class ReadingPlanEntities {

    /** Stores information for plan, like start date and current day user is on.
     * Plans that exist are determined by text files. Row will only exist here for plan
     * that has already been started */
    @Entity(indices = [Index("planCode", unique = true)])
    data class ReadingPlan(
        val planCode: String,
        var planStartDate: Date,
        @ColumnInfo(defaultValue = "1") var planCurrentDay: Int = 1,
        @PrimaryKey val id: IdType = IdType()
    )

    @Entity(indices = [Index(value = ["planCode", "planDay"], unique = true)])
    data class ReadingPlanStatus(
        val planCode: String,
        val planDay: Int,
        val readingStatus: String,
        @PrimaryKey val id: IdType = IdType()
    )
}
