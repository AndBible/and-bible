/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import net.bible.android.database.IdType
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import java.util.Date

/**
 * Dao for readingplan and readingplan_status tables
 */
@Dao
interface ReadingPlanDao {

    //region ReadingPlanStatus
    @Query("SELECT * FROM ReadingPlanStatus WHERE planCode = :planCode AND planDay = :planDay")
    suspend fun getStatus(planCode: String, planDay: Int): ReadingPlanStatus?

    @Query("DELETE FROM ReadingPlanStatus WHERE planCode = :planCode AND planDay < :planDay")
    suspend fun deleteStatusesBeforeDay(planCode: String, planDay: Int)

    @Query("DELETE FROM ReadingPlanStatus WHERE planCode = :planCode")
    suspend fun deleteStatusesForPlan(planCode: String)


    @Query("""
        INSERT INTO ReadingPlanStatus VALUES (:planCode, :planDay, :readingStatus, :id)
        ON CONFLICT(planCode,planDay) DO UPDATE SET
        planCode=:planCode, 
        planDay=:planDay, 
        readingStatus=:readingStatus
    """)
    suspend fun addPlanStatus(
        planCode: String,
        planDay: Int,
        readingStatus: String,
        id: IdType
    )
    suspend fun addPlanStatus(status: ReadingPlanStatus) =
        addPlanStatus(status.planCode, status.planDay, status.readingStatus, status.id)
    //endregion

    //region ReadingPlan
    @Query("SELECT * FROM ReadingPlan WHERE PlanCode = :planCode")
    suspend fun getPlan(planCode: String): ReadingPlan?

    @Query("""
        INSERT INTO ReadingPlan VALUES (:planCode, :planCurrentDay, :planCurrentDay, :id)
        ON CONFLICT DO UPDATE SET
        planCode=:planCode, 
        planStartDate=:planStartDate, 
        planCurrentDay=:planCurrentDay
        WHERE id = :id
        
        """)
    suspend fun updatePlan(
        planCode: String,
        planStartDate: Date,
        planCurrentDay: Int,
        id: IdType
    )
    suspend fun updatePlan(plan: ReadingPlan) =
        updatePlan(plan.planCode, plan.planStartDate, plan.planCurrentDay, plan.id)

    @Query("DELETE FROM ReadingPlan WHERE PlanCode = :planCode")
    suspend fun deletePlanInfo(planCode: String)

    //endregion

}
