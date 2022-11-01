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
import java.util.Date

class ReadingPlanEntities {

    /** Stores information for plan, like start date and current day user is on.
     * Plans that exist are determined by text files. Row will only exist here for plan
     * that has already been started */
    @Entity(tableName = "readingplan",
        indices = [Index(name = "index_readingplan_plan_code",value=["plan_code"], unique = true)])
    data class ReadingPlan(
        @ColumnInfo(name = "plan_code") val planCode: String,
        @ColumnInfo(name = "plan_start_date") var planStartDate: Date,
        @ColumnInfo(name = "plan_current_day", defaultValue = "1") var planCurrentDay: Int = 1,
        @PrimaryKey(autoGenerate = true) @ColumnInfo(name="_id") val id: Int? = null
    )

    @Entity(tableName = "readingplan_status",
        indices = [Index(name="code_day", value = ["plan_code", "plan_day"], unique = true)]
    )
    data class ReadingPlanStatus(
        @ColumnInfo(name = "plan_code") val planCode: String,
        @ColumnInfo(name = "plan_day") val planDay: Int,
        @ColumnInfo(name = "reading_status") val readingStatus: String,
        @PrimaryKey(autoGenerate = true) @ColumnInfo(name="_id") val id: Int? = null
    )
}
