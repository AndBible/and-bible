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

package net.bible.android.database.readingplan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus

/**
 * Dao for readingplan and readingplan_status tables
 */
@Dao
interface ReadingPlanDao {

    //region ReadingPlanStatus
    @Query("SELECT * FROM readingplan_status WHERE plan_code = :planCode AND plan_day = :planDay")
    suspend fun getStatus(planCode: String, planDay: Int): ReadingPlanStatus?

    @Query("DELETE FROM readingplan_status WHERE plan_code = :planCode AND plan_day < :planDay")
    suspend fun deleteStatusesBeforeDay(planCode: String, planDay: Int)

    @Query("DELETE FROM readingplan_status WHERE plan_code = :planCode")
    suspend fun deleteStatusesForPlan(planCode: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlanStatus(status: ReadingPlanStatus)
    //endregion

    //region ReadingPlan
    @Query("SELECT * FROM readingplan WHERE plan_code = :planCode")
    suspend fun getPlan(planCode: String): ReadingPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePlan(plan: ReadingPlan)

    @Query("DELETE FROM readingplan WHERE plan_code = :planCode")
    suspend fun deletePlanInfo(planCode: String)

    //endregion

}
