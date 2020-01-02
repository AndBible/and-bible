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

import android.util.Log

import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.versification.VersificationConverter
import net.bible.service.common.CommonUtils
import net.bible.service.readingplan.OneDaysReadingsDto
import net.bible.service.readingplan.ReadingPlanDao
import net.bible.service.readingplan.ReadingPlanInfoDto

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key

import java.util.ArrayList
import java.util.Calendar
import java.util.Date

import javax.inject.Inject
import kotlin.math.roundToLong


/** Control status of reading plans
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class ReadingPlanControl @Inject constructor(
		private val speakControl: SpeakControl,
		private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider)
{

    private val readingPlanDao = ReadingPlanDao()
    private var readingStatus: ReadingStatus? = null

    /** allow front end to determine if a plan needs has been selected
     */
    val isReadingPlanSelected: Boolean
        get() = StringUtils.isNotEmpty(currentPlanCode)

    /** get a list of plans so the user can choose one
     */
    val readingPlanList: List<ReadingPlanInfoDto>
        get() = readingPlanDao.readingPlanList

    /**
     * Check if any user plans in jsword/readingplan have same file
     * name as one of the default plans
     */
    val readingPlanUserDuplicates: Boolean
    get() {
        val userPlanList = readingPlanDao.userPlanCodes(false) ?: return false
        val internalPlanList = readingPlanDao.internalPlanCodes
        userPlanList.forEach { userPlan ->
            if (internalPlanList.find { internalPlan -> internalPlan == userPlan } != null)
                return true
        }
        return false
    }

    /** get list of days and readings for a plan so user can see the plan in advance
     */
    val currentPlansReadingList: List<OneDaysReadingsDto>
        get() = readingPlanDao.getReadingList(currentPlanCode)

    var currentPlanDay: Int
        get() {
            val planCode = currentPlanCode
            return if (readingPlanDao.getReading(planCode, 1).isDateBasedPlan) {
                val todayDate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                readingPlanDao.getReadingList(planCode).find {
                    it.readingDate == todayDate.time
                }?.day ?: 1
            } else {
                readingPlanDao.getReadingPlanInfoDto(planCode)
                    .rAdapter.getReadingCurrentDay(planCode)
            }
        }
        private set(day) {
            val planCode = currentPlanCode
            if (readingPlanDao.getReading(planCode, 1).isDateBasedPlan) return
            readingPlanDao.getReadingPlanInfoDto(planCode)
                .rAdapter.setReadingCurrentDay(planCode, day)
        }

    val shortTitle: String
        get() = StringUtils.left(currentPlanCode, 8)

    val currentDayDescription: String
        get() = if (isReadingPlanSelected) {
            getDaysReading(currentPlanDay).dayDesc
        } else {
            ""
        }

    /** keep track of which plan the user has currently.  This can be safely changed and reverted to without losing track
     */
    private val currentPlanCode: String
        get() {
            val prefs = CommonUtils.sharedPreferences
            return prefs.getString(READING_PLAN, "") as String
        }

    val currentPageManager: CurrentPageManager
        get() = activeWindowPageManagerProvider.activeWindowPageManager

    /** User has chosen to start a plan
     */
    fun startReadingPlan(plan: ReadingPlanInfoDto) {
        // set default plan
        setReadingPlan(plan.planCode)

        // tell the plan to set a start date
        plan.start()
    }

    /** Adjust the plan start date
     */
    fun setStartDate(plan: ReadingPlanInfoDto, startDate: Date) {
        // tell the plan to set a start date
        plan.setStartDate(startDate)
    }

    /** change default plan
     */
    fun setReadingPlan(planCode: String) {
        // set default plan to this
        val prefs = CommonUtils.sharedPreferences
        prefs.edit()
                .putString(READING_PLAN, planCode)
                .apply()
    }

    /** get read status of this days readings
     */
    fun getReadingStatus(day: Int): ReadingStatus {
        val planCode = currentPlanCode
        var readingStatus = readingStatus
        if (readingStatus == null || readingStatus.planCode != planCode || readingStatus.day != day) {
            val oneDaysReadingsDto = readingPlanDao.getReading(planCode, day)
            // if Historic then return historic status that returns read=true for all passages
            readingStatus = if (!oneDaysReadingsDto.isDateBasedPlan && day < currentPlanDay) {
                HistoricReadingStatus(planCode, day, oneDaysReadingsDto.numReadings)
            } else {
                ReadingStatus(planCode, day, oneDaysReadingsDto.numReadings)
            }
			this.readingStatus = readingStatus.apply { reloadStatus() }
        }
        return readingStatus
    }

    private fun getDueDay(planInfo: ReadingPlanInfoDto): Long {
        val today = CommonUtils.truncatedDate
        val startDate = planInfo.startdate ?: return 0
        // on final day, after done the startDate will be null

        // should not need to round as we use truncated dates, but safety first
        // later found that rounding is necessary (due to DST I think) because
        // when the clocks went forward the difference became 88.95833 but should have been 89
        val diffInDays = (today.time - startDate.time) / (1000.0 * 60.0 * 60.0 * 24.0)
        val diffInWholeDays = diffInDays.roundToLong()
        Log.d(TAG, "Days diff between today and start:$diffInWholeDays")

        // if diff is zero then we are on day 1 so add 1
        return diffInWholeDays + 1
    }

    /** mark this day as complete unless it is in the future
     * if last day then reset plan
     */
    fun done(planInfo: ReadingPlanInfoDto, day: Int, force: Boolean): Int {
        // which day to show next -1 means the user is up to date and can close Reading Plan
        var nextDayToShow = -1

        // force Done to work for whatever day is passed in, otherwise Done only works for current plan day and ignores other days
        if (force) {
            // for Done to work for non plan day
            currentPlanDay = day

            // normal reading status update is circumvented so mark all as read here
            getReadingStatus(day).setAllRead()
        }

        // was this the next reading plan day due whether on schedule or not
        if (currentPlanDay == day) {
            // do not leave prefs for historic days - we show all historic readings as 'read'
            getReadingStatus(day).delete(planInfo)

            // was this the last day in the plan
            if (readingPlanDao.getNumberOfPlanDays(currentPlanCode) == day) {
                // last plan day is just Done so clear all plan status
                reset(planInfo)
                nextDayToShow = -1
            } else {
                // move to next plan day
                var nextDay = incrementCurrentPlanDay()

                // if there are no readings scheduled for the next day then mark it as Done and carry on to next next day
                val nextReadings = getDaysReading(nextDay)
                if (nextReadings.numReadings == 0) {
                    nextDay = done(planInfo, nextDay, force)
                }

                nextDayToShow = nextDay
            }
        } else {
            if (planInfo.numberOfPlanDays > day) {
                nextDayToShow = day + 1
            }
        }

        //if user is not behind then do not show Daily Reading screen
        if (!isDueToBeRead(planInfo, nextDayToShow)) {
            nextDayToShow = -1
        }


        return nextDayToShow
    }


    private fun isDueToBeRead(planInfo: ReadingPlanInfoDto, day: Int): Boolean {
        return getDueDay(planInfo) >= day
    }

    /** increment current day
     */
	private fun incrementCurrentPlanDay(): Int {
        val nextDay = currentPlanDay + 1
        currentPlanDay = nextDay

        return nextDay
    }

    /** get readings due for current plan on specified day
     */
    fun getDaysReading(day: Int): OneDaysReadingsDto {
        return readingPlanDao.getReading(currentPlanCode, day)
    }

    /** User wants to read a passage from the daily reading
     * Also mark passage as read
     */
    fun read(day: Int, readingNo: Int, readingKey: Key?) {
        if (readingKey != null) {
            // mark reading as 'read'
            getReadingStatus(day).setRead(readingNo)

            ABEventBus.getDefault().post(BeforeCurrentPageChangeEvent())

            // show the current bible
            val currentPageManager = currentPageManager
            val bible = currentPageManager.currentBible.currentPassageBook

            // convert the verse to the v11n of the current bible
            val keyList = convertReadingVersification(readingKey, bible)
            val firstKey = keyList[0]

            // go to correct passage
            currentPageManager.setCurrentDocumentAndKey(bible, firstKey)
        }
    }

    /**
     * Speak 1 reading and mark as read.  Also convert from ReadingPlan v11n type to v11n type of current Bible.
     */
    fun speak(day: Int, readingNo: Int, readingKey: Key) {
        val bible = currentPageManager.currentBible.currentPassageBook
        val keyList = convertReadingVersification(readingKey, bible)

        speakControl.speakKeyList(bible, keyList, true, false)

        getReadingStatus(day).setRead(readingNo)
    }

    /** User wants all passages from the daily reading spoken using TTS
     * Also mark passages as read
     */
    fun speak(day: Int, allReadings: List<Key>) {
        val bible = currentPageManager.currentBible.currentPassageBook
        val allReadingsWithCorrectV11n = ArrayList<Key>()
        for (key in allReadings) {
            val keyList = convertReadingVersification(key, bible)
            allReadingsWithCorrectV11n.addAll(keyList)
        }
        speakControl.speakKeyList(bible, allReadingsWithCorrectV11n, true, false)

        // mark all readings as read
        for (i in allReadings.indices) {
            getReadingStatus(day).setRead(i)
        }
    }

    fun reset(plan: ReadingPlanInfoDto) {
        // if resetting default plan then remove default
        if (plan.planCode == currentPlanCode) {
            CommonUtils.sharedPreferences.edit().remove(READING_PLAN).apply()
        }

        plan.rAdapter.resetPlan(plan.planCode)
    }

    private fun convertReadingVersification(readingKey: Key, bibleToBeUsed: AbstractPassageBook): List<Key> {
        val documentV11n = bibleToBeUsed.versification

        val v11nConverter = VersificationConverter()
        val convertedPassage = v11nConverter.convert(readingKey, documentV11n)

        val keyList = ArrayList<Key>()
        keyList.add(convertedPassage)
        return keyList
    }

    companion object {

        private const val READING_PLAN = "reading_plan"

        private const val TAG = "ReadingPlanControl"
    }

}
