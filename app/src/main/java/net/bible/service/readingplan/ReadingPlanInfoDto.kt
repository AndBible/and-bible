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

package net.bible.service.readingplan

import org.crosswire.jsword.versification.Versification

import java.util.Date

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ReadingPlanInfoDto(var planCode: String) {
    var planName: String? = null
    var planDescription: String? = null
    var versification: Versification? = null
    var numberOfPlanDays: Int = 0
    var isDateBasedPlan: Boolean = false
    var startDate: Date? = null

    override fun toString(): String {
        return "$planName"
    }

}
