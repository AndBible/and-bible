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

package net.bible.service.db.readingplan

import net.bible.sharedcore.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.readingplan.ReadingPlanInfoDto
import java.util.Calendar
import java.util.Date
import kotlin.math.max

class ReadingPlanRepository(
    private val daoProvider: () -> ReadingPlanDao = { DatabaseContainer.instance.readingPlanDb.readingPlanDao() },
    private val today: () -> Date = { CommonUtils.truncatedDate },
) {
    private val readingPlanDao: ReadingPlanDao get() = daoProvider()
    val scope = CoroutineScope(Dispatchers.Default)

    fun getReadingStatus(planCode: String, planDay: Int): String? = runBlocking {
        readingPlanDao.getStatus(planCode, planDay)?.readingStatus }

    /**
     * The plan's start date, healing a row the pre-fix INSERT corrupted (it stored the day number
     * as the start date: see ReadingPlanDao.updatePlan). Healed to "today is the day you are on",
     * written back once; progress is untouched.
     */
    fun getStartDate(planCode: String): Date? = runBlocking {
        val plan = readingPlanDao.getPlan(planCode) ?: return@runBlocking null
        if (plan.planStartDate.time >= CORRUPT_START_DATE_LIMIT_MS) return@runBlocking plan.planStartDate
        val healed = healedStartDate(today(), plan.planCurrentDay)
        Log.i(TAG, "Healing corrupt start date ${plan.planStartDate.time} of plan $planCode -> $healed")
        plan.planStartDate = healed
        readingPlanDao.updatePlan(plan)
        healed
    }

    /**
     * All reading statuses will be deleted that are before the [day] parameter given.
     * Date-based plan statuses are never deleted
     * @param day The current day, all day statuses before this day will be deleted
     */
    fun deleteOldStatuses(planInfo: ReadingPlanInfoDto, day: Int) = scope.launch {
        if (!planInfo.isDateBasedPlan)
            readingPlanDao.deleteStatusesBeforeDay(planInfo.planCode, day)
    }

    @Synchronized
    fun setReadingStatus(planCode: String, dayNo: Int, status: String) = scope.launch {
        readingPlanDao.addPlanStatus(
            ReadingPlanStatus(planCode, dayNo, status)
        )
    }

    @Synchronized
    fun startPlan(planCode: String, date: Date = today()) = runBlocking {
        var readPlan = readingPlanDao.getPlan(planCode)
        readPlan = readPlan?.apply { planStartDate = date } ?: ReadingPlan(planCode, date)

        readingPlanDao.updatePlan(readPlan)
    }

    fun resetPlan(planCode: String) = scope.launch {
        Log.i(TAG, "Now resetting plan $planCode in database. Removing start date, current day, and read statuses")
        readingPlanDao.deleteStatusesForPlan(planCode)
        readingPlanDao.deletePlanInfo(planCode)
    }

    fun getCurrentDay(planCode: String): Int = runBlocking {
        max(readingPlanDao.getPlan(planCode)?.planCurrentDay ?: 0,1)
    }

    @Synchronized
    fun setCurrentDay(planCode: String, dayNo: Int) = scope.launch {
        var readPlan = readingPlanDao.getPlan(planCode)
        readPlan = readPlan?.apply { planCurrentDay = dayNo } ?:
            ReadingPlan(planCode, today(), dayNo)

        readingPlanDao.updatePlan(readPlan)
    }

    companion object {
        private val TAG = ReadingPlanRepository::class.simpleName ?: "ReadingPlanRepository"

        /** No real start date is before 2 Jan 1970; the corrupt rows hold a day number (ms). */
        const val CORRUPT_START_DATE_LIMIT_MS = 100_000_000L

        internal fun healedStartDate(today: Date, currentDay: Int): Date =
            Calendar.getInstance().apply {
                time = today
                add(Calendar.DAY_OF_YEAR, -(max(currentDay, 1) - 1))
            }.time
    }
}
