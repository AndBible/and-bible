/*
 * Copyright (c) 2019 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

class ReadingPlanEntities {
    @Entity(tableName = "readingplan", indices = [Index(value=["plan_code"])])
    data class ReadingPlan(
        @PrimaryKey @ColumnInfo(name="_id") val id: Int?,
        @ColumnInfo(name = "plan_code") val planCode: String,
        @ColumnInfo(name = "plan_start_date") val planStartDate: Int,
        @ColumnInfo(name = "plan_current_day", defaultValue = "1") val planCurrentDay: Int = 1
    )

    @Entity(tableName = "readingplan_status",
        indices = [Index(name="code_day", value = ["plan_code", "plan_day"])]
    )
    data class ReadingPlanStatus(
        @PrimaryKey @ColumnInfo(name="_id") val id: Int?,
        @ColumnInfo(name = "plan_code") val planCode: String,
        @ColumnInfo(name = "plan_day") val planDay: Int,
        @ColumnInfo(name = "reading_status") val readingStatus: String
    )
}
