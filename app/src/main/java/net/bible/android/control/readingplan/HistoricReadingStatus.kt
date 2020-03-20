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

package net.bible.android.control.readingplan

import net.bible.service.readingplan.ReadingPlanInfoDto

/** return isRead' for all historical readings
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class HistoricReadingStatus(planCode: String, day: Int, numReadings: Int) : ReadingStatus(planCode, day, numReadings) {

    override fun setRead(readingNo: Int) {
        // do nothing - all readings are already read
    }

    override fun setUnread(readingNo: Int) {
        // do nothing - all readings are already read
    }

    override fun isRead(readingNo: Int): Boolean {
        // all readings are already read
        return true
    }

    override fun delete(planInfo: ReadingPlanInfoDto) {
        // do nothing
    }

    override fun reloadStatus() {
        // do nothing
    }


}
