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

package net.bible.service.db.readingplan

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.android.control.ApplicationScope
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.service.db.DatabaseContainer
import net.bible.service.readingplan.ReadingPlanInfoDto
import javax.inject.Inject

@ApplicationScope
class ReadingPlanRepository @Inject constructor() {
    private val readingPlanDao: ReadingPlanDao = DatabaseContainer.db.readingPlanDao()

    fun getReadingPlanStatus(planCode: String, planDay: Int): String? = runBlocking {
        readingPlanDao.getStatus(planCode, planDay)?.readingStatus }

    /**
     * All reading statuses will be deleted that are before the [day] parameter given.
     * Date-based plan statuses are never deleted
     * @param day The current day, all day statuses before this day will be deleted
     */
    fun deleteOldStatuses(planInfo: ReadingPlanInfoDto, day: Int) {
        if (!planInfo.isDateBasedPlan) GlobalScope.launch {
            readingPlanDao.deleteStatusesBeforeDay(planInfo.planCode, day)
        }
    }

    @Synchronized
    fun setReadingPlanStatus(planCode: String, dayNo: Int, status: String) {
        GlobalScope.launch {
            readingPlanDao.addPlanStatus(
                ReadingPlanStatus(planCode, dayNo, status)
            )
        }
    }

}
