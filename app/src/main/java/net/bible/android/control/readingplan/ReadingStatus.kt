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

package net.bible.android.control.readingplan

import kotlinx.serialization.Serializable
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.json
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.readingplan.ReadingPlanInfoDto
import javax.inject.Inject

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */

open class ReadingStatus(val planCode: String, val day: Int, private val numReadings: Int) {
    @Inject
    lateinit var readingPlanRepo: ReadingPlanRepository

    init {
        CommonUtils.buildActivityComponent().inject(this)
    }

    @Serializable
    private data class ChapterRead(val readingNumber: Int, var isRead: Boolean = false)

    @Serializable
    private data class ReadingStatus(var chapterReadArray: ArrayList<ChapterRead>) {
        constructor(jsonString: String) : this(toArrayList(jsonString))
        companion object {
            private fun toArrayList(jsonString: String): ArrayList<ChapterRead> {
                return json.decodeFromString(serializer(), jsonString).chapterReadArray
            }
        }

        override fun toString(): String {
            return json.encodeToString(serializer(), this)
        }
    }

    private var status = ReadingStatus(ArrayList())
    val isAllRead: Boolean
        get() {
            for (i in 1..numReadings) {
                if (!isRead(i)) {
                    return false
                }
            }
            return true
        }

    open fun setRead(readingNo: Int) {
        setStatus(readingNo, true)
    }

    open fun setUnread(readingNo: Int) {
        setStatus(readingNo, false)
    }

    fun setStatus(readingNo: Int, read: Boolean, saveStatus: Boolean = true) {
        val chapterRead = status.chapterReadArray.find { it.readingNumber == readingNo }
        if (chapterRead == null) {
            status.chapterReadArray.add(ChapterRead(readingNo, read))
        } else {
            chapterRead.isRead = read
            status.chapterReadArray.add(chapterRead)
        }
        status.chapterReadArray.sortBy { it.readingNumber }

        if (saveStatus) saveStatus()
    }

    open fun isRead(readingNo: Int): Boolean {
        return status.chapterReadArray.find { it.readingNumber == readingNo }?.isRead ?: false
    }

    fun setAllRead() {
        for (i in 1..numReadings) {
            setRead(i)
        }
    }

    /** do not leave prefs around for historic days
     */
    open fun delete(planInfo: ReadingPlanInfoDto) {
        readingPlanRepo.deleteOldStatuses(planInfo, day)
    }

    open fun reloadStatus() {
        val status: String? = readingPlanRepo.getReadingStatus(planCode, day)
        status?.let { this.status = ReadingStatus(status) }
    }

    private fun saveStatus() {
        readingPlanRepo.setReadingStatus(planCode, day, status.toString())
    }

    override fun toString(): String {
        return status.toString()
    }
}
