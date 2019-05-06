/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.readingplan

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.reading_plan_one_day.*
import net.bible.android.BibleApplication

import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.versification.VersificationConverter
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanActionBarManager
import net.bible.service.db.CommonDatabaseHelper
import net.bible.service.db.readingplan.ReadingPlanDBAdapter
import net.bible.service.db.readingplan.ReadingPlanDbOperations
import net.bible.service.db.readingplan.ReadingPlanInformationDB
import net.bible.service.db.readingplan.ReadingPlanOneDayDB
import net.bible.service.readingplan.event.ReadingPlanDayChangeEvent
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key

import org.crosswire.jsword.versification.BookName

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar

import javax.inject.Inject

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DailyReading : CustomTitlebarActivityBase(R.menu.reading_plan) {

    private val dbAdapter = ReadingPlanDBAdapter()
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    @Inject lateinit var speakControl: SpeakControl

    private val currentPageManager
        get() = activeWindowPageManagerProvider.activeWindowPageManager

    private var readingPlanID: Int? = null
    private var readingPlanDayNumber: Int? = null
    private lateinit var readingPlanOneDay: ReadingPlanOneDayDB

    private var imageTickList: MutableList<ImageView> = ArrayList()


    @Inject lateinit var readingPlanActionBarManager: ReadingPlanActionBarManager

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, false)
        Log.i(TAG, "Displaying one day reading plan")
        setContentView(R.layout.reading_plan_one_day)

        super.buildActivityComponent().inject(this)
        super.setActionBarManager(readingPlanActionBarManager)

        ABEventBus.getDefault().register(this)
        Log.d(TAG, "Registered to ABEventBus")

        if (dbAdapter.currentActiveReadingPlanID == 0) {
            // Load plan selection screen
            startActivityForResult(
                Intent(this, ReadingPlanSelectorList::class.java),
                RCODE_READING_PLAN_LIST
            )

        } else {

            loadPlanOneDay(ReadingPlanOneDayDB(ReadingPlanInformationDB(readingPlanID)))

        }
    }

    override fun onRestart() {
        super.onRestart()

        // Needed for when current custom plan has been deleted in ReadingPlanSelectorList and returning
        // to Daily reading screen without selecting a plan.
        if (dbAdapter.currentActiveReadingPlanID == 0) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ABEventBus.getDefault().unregister(this)
        Log.d(TAG, "Unregistered from ABEventBus")
    }

    /**
     * Function loads reading plan information screen with correct data
     * @param readingPlanOneDay Allows nulls, but the ONLY time null should be
     * passed is when no reading plan has been started or there is no reading for the day.
     * @param readingPlanInfo Is REQUIRED ONLY when the
     * [readingPlanOneDay] is passed as NULL
     */
    private fun loadPlanOneDay(
        readingPlanOneDay: ReadingPlanOneDayDB?,
        readingPlanInfo: ReadingPlanInformationDB? = null
        ) {
        Log.d(TAG, "readingPlanOneDayDB=${readingPlanOneDay.toString()}")
        Log.d(TAG, "readingPlanInformationDB=${readingPlanInfo.toString()}")

        if (readingPlanOneDay == null && readingPlanInfo?.isDateBased == true) {
            // We have a started plan, but it is date-based and there's no reading for this day
            clearReadingInfo()
            descriptionTextView.text = readingPlanInfo.planName
            dateTextView.text = SimpleDateFormat.getDateInstance().format(Calendar.getInstance().time)
            statusMessageTextView.text = getString(R.string.this_day_has_no_readings)
            dayTextView.text = ""

        } else if (readingPlanOneDay != null) {
            this.readingPlanOneDay = readingPlanOneDay
        }

        if (readingPlanOneDay != null) {
            // Load plan to screen
            val readingPlanInfo = this.readingPlanOneDay.readingPlanInfo
            val previousReadingPlanShownId = readingPlanID

            // Reload plan meta info if plan has changed or just now starting reading plan screen
            if (readingPlanInfo.readingPlanID != readingPlanID) {
                readingPlanID = readingPlanInfo.readingPlanID

                dbAdapter.currentActiveReadingPlanID = readingPlanID ?: 0

                descriptionTextView.text = readingPlanInfo.planName
                statusMessageTextView.text = ""

            }

            // Reload plan day info if day has changed or if it's just now starting reading plan screen
            if (readingPlanInfo.readingPlanID != previousReadingPlanShownId ||
                this.readingPlanOneDay.dayNumber != readingPlanDayNumber
            ) {
                readingPlanDayNumber = this.readingPlanOneDay.dayNumber

                setupReadingsInformationForDay(readingPlanInfo)


            }
            Log.d(TAG, "Finished displaying Reading view")

        }

    }

    private fun clearReadingInfo() {
        readingContainerLayout.removeAllViews()
        imageTickList.clear()
    }

    private fun setupReadingsInformationForDay(readingPlanInfo: ReadingPlanInformationDB) {

        try {

            setupDayMetaInformation(readingPlanInfo)

            // show short book name to save space if Portrait
            val fullBookNameSave = BookName.isFullBookName()
            BookName.setFullBookName(!isPortrait)

            clearReadingInfo()

            for (i in 0 until readingPlanOneDay.numberOfReadings) {

                setupOneReading(i)

            }

            BookName.setFullBookName(fullBookNameSave)

            updateTicksAndDone(readingPlanOneDay)

            if (readingPlanOneDay.numberOfReadings > 1) {

                setupSpeakAllReadingsRow()

            }

        } catch (e: Exception) {
            Log.e(TAG, "Error showing daily readings", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun setupDayMetaInformation(readingPlanInfo: ReadingPlanInformationDB) {
        dayTextView.text = when (readingPlanInfo.isDateBased) {
            true -> ""
            false -> readingPlanOneDay.dayAndNumberDescription
        }

        dateTextView.text = readingPlanOneDay.dateString

        if (readingPlanActionBarManager.readingPlanTitle.isActivityInitialized) {
            readingPlanActionBarManager.readingPlanTitle.update(
                StringUtils.left(readingPlanInfo.fileName, 8),
                readingPlanOneDay.dayAndNumberDescription
            )
        }
    }

    private fun setupOneReading(readingNumber: Int) {
        val child = layoutInflater.inflate(R.layout.reading_plan_one_reading, null)
        val imageTick = child.findViewById(R.id.tickImageView) as ImageView
        val passageText = child.findViewById(R.id.passageTextView) as TextView
        val readButton = child.findViewById(R.id.readButton) as Button
        val speakButton = child.findViewById(R.id.speakButton) as Button

        passageText.text = readingPlanOneDay.getReadingKey(readingNumber).name
        readButton.setOnClickListener { onRead(readingNumber) }
        speakButton.setOnClickListener { onSpeak(readingNumber) }

        readingContainerLayout.addView(child, readingNumber)
        imageTickList.add(imageTick)

        imageTick.setOnClickListener {
            readingPlanOneDay.readingStatus.run {
                if (isRead(readingNumber)) {
                    setUnread(readingNumber, readingPlanDayNumber!!)
                } else {
                    setRead(readingNumber, readingPlanDayNumber!!)
                }
            }
            updateTicksAndDone(readingPlanOneDay)
        }
    }

    private fun setupSpeakAllReadingsRow() {
        val child = layoutInflater.inflate(R.layout.reading_plan_one_reading, null)
        val tick = child.findViewById(R.id.tickImageView) as ImageView
        val passageText = child.findViewById(R.id.passageTextView) as TextView
        val readButton = child.findViewById(R.id.readButton) as Button
        val speakButton = child.findViewById(R.id.speakButton) as Button

        tick.visibility = View.INVISIBLE
        passageText.text = resources.getString(R.string.all)
        readButton.visibility = View.INVISIBLE
        speakButton.setOnClickListener { onSpeakAll(null) }
        readingContainerLayout.addView(child, readingPlanOneDay.numberOfReadings)
    }

    fun onEvent(event: ReadingPlanDayChangeEvent) {
        loadPlanOneDay(event.readingPlanOneDay, event.readingPlanInfo)
    }

    private fun onRead(readingNumber: Int) {
        Log.i(TAG, "Read $readingNumber")
        readOnePassage(readingNumber, readingPlanOneDay.getReadingKey(readingNumber))

        finish()
    }

    private fun onSpeak(readingNumber: Int) {
        Log.i(TAG, "Speak $readingNumber")
        speak(readingNumber, readingPlanOneDay.getReadingKey(readingNumber))

        updateTicksAndDone(readingPlanOneDay)
    }

    private fun onSpeakAll(view: View?) {
        Log.i(TAG, "Speak all")
        speakAllReadings(readingPlanOneDay.readingChaptersKeyArray!!)

        updateTicksAndDone(readingPlanOneDay)
    }

    private fun readOnePassage(readingNumber: Int, readingKey: Key?) {
        if (readingKey == null) return

        isIntegrateWithHistoryManager = true

        readingPlanOneDay.readingStatus.setRead(readingNumber, readingPlanDayNumber!!)

        ABEventBus.getDefault().post(BeforeCurrentPageChangeEvent())

        // show the current bible
        val currentPageManager = currentPageManager
        val bible = currentPageManager.currentBible.currentPassageBook

        val keyList = convertReadingVersification(readingKey, bible)
        val firstKey = keyList[0]

        // go to correct passage
        currentPageManager.setCurrentDocumentAndKey(bible, firstKey)
    }

    private fun convertReadingVersification(readingKey: Key, bibleToBeUsed: AbstractPassageBook): List<Key> {
        val documentV11n = bibleToBeUsed.versification

        val v11nConverter = VersificationConverter()
        val convertedPassage = v11nConverter.convert(readingKey, documentV11n)

        return arrayListOf(convertedPassage)
    }

    fun speak(readingNo: Int, readingKey: Key) {
        val bible = currentPageManager.currentBible.currentPassageBook
        val keyList = convertReadingVersification(readingKey, bible)

        speakControl.speakKeyList(bible, keyList, true, false)

        readingPlanOneDay.readingStatus.setRead(readingNo, readingPlanDayNumber!!)
    }

    private fun speakAllReadings(allReadings: List<Key>) {
        val bible = currentPageManager.currentBible.currentPassageBook
        val allReadingsWithCorrectV11n: ArrayList<Key> = arrayListOf()
        for (key in allReadings) {
            val keyList = convertReadingVersification(key, bible)
            allReadingsWithCorrectV11n.addAll(keyList)
        }
        speakControl.speakKeyList(bible, allReadingsWithCorrectV11n, true, false)

        for (i in allReadings.indices) {
            readingPlanOneDay.readingStatus.setRead(i, readingPlanDayNumber!!)
        }
    }

    fun onDoneButtonClick(view: View) {
        Log.i(TAG, "Done")
        try {
            // do not add to History list because it will just redisplay same page
            isIntegrateWithHistoryManager = false

            // all readings must be ticked for Done button to be enabled
            val nextDayToShow = done( false)
            Log.d(TAG, "Next day to show is $nextDayToShow")

            //if user is behind then go to next days readings
            if (nextDayToShow > 0 && !readingPlanOneDay.readingPlanInfo.isDateBased) {
                loadPlanOneDay(ReadingPlanOneDayDB(readingPlanOneDay.readingPlanInfo))
            } else {
                finish()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error when Done daily reading", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }

    }

    fun done(force: Boolean): Int {
        // which day to show next -1 means the user is up to date and can close Reading Plan
        var nextDayToShow = -1

        val planID: Int = readingPlanID!!
        val dayNumber: Int = readingPlanDayNumber!!
        // force Done to work for whatever day is passed in, otherwise Done only works for current plan day and ignores other days
        if (force) {
            dbAdapter.setCurrentDayNumber(planID, dayNumber)

            dbAdapter.setAllDaysReadUpTo(readingPlanDayNumber!!)
            readingPlanOneDay.readingStatus.setAllRead(readingPlanDayNumber!!)
        }

        // was this the next reading plan day due whether on schedule or not
        Log.d(TAG, "The current day number for the plan is ${dbAdapter.getCurrentDayNumber(planID)}")
        Log.d(TAG, "The loaded day number on the screen is $dayNumber")
        if (dbAdapter.getCurrentDayNumber(planID) == dayNumber) {
            // was this the last day in the plan
            nextDayToShow =  if (readingPlanOneDay.readingPlanInfo.totalDays == readingPlanDayNumber) dayNumber
            else dbAdapter.incrementCurrentPlanDay()

        } else {
            if (readingPlanOneDay.readingPlanInfo.totalDays > readingPlanDayNumber ?: 0) {
                nextDayToShow = readingPlanDayNumber ?: 0 + 1
            }
        }

        // If user is not behind then do not show Daily Reading screen
        if (!readingPlanOneDay.readingPlanInfo.isDateBased && !isDueToBeRead(readingPlanID!!,readingPlanDayNumber!!)
        ) {
            nextDayToShow = -1
        }


        return nextDayToShow
    }

    private fun isDueToBeRead(readingPlanMetaId: Int, day: Int): Boolean {
        return dbAdapter.getDueDayToBeRead(readingPlanMetaId) >= day
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult requestCode=$requestCode, resultCode=$resultCode")
        CurrentActivityHolder.getInstance().currentActivity = this
        when (requestCode) {
            RCODE_READING_PLAN_LIST -> {
                if (resultCode != RESULT_OK || dbAdapter.currentActiveReadingPlanID == 0) {
                    finish()
                }
            }
            RCODE_PICK_PLAN_FILE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (!ReadingPlanDbOperations().importReadingPlansToDatabase(
                        CommonDatabaseHelper.getInstance().readableDatabase,
                        data.data
                    )) Dialogs.getInstance().showErrorMsg(R.string.error_importing_plan)
                }
            }
        }
    }

    private fun updateTicksAndDone(readingPlanOneDay: ReadingPlanOneDayDB) {
        val status = readingPlanOneDay.readingStatus

        for (i in imageTickList.indices) {
            imageTickList[i].setImageResource(
                if (status.isRead(i)) R.drawable.btn_check_buttonless_on
                else R.drawable.btn_check_buttonless_off
            )
        }

        doneButton.isEnabled = status.isAllRead()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = false

        when (item.itemId) {
            // selected to allow jump to a certain day
            R.id.done -> {
                Log.i(TAG, "Force Done")
                try {
                    done(true)
                    updateTicksAndDone(readingPlanOneDay)
                } catch (e: Exception) {
                    Log.e(TAG, "Error when Done daily reading", e)
                    Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
                }
            }
            R.id.reset -> {
                Dialogs.getInstance().showMsg(R.string.reset_plan_question,true)
                    {
                        Log.d(TAG, "Resetting plan id $readingPlanID")
                        dbAdapter.resetPlan(readingPlanID!!)
                        reloadDailyReading()
                    }

                isHandled = true
            }
            R.id.setStartDate -> {

                val nowTime = Calendar.getInstance()
                val planStartDate = Calendar.getInstance()
                planStartDate.time = dbAdapter.getPlanStartDate(readingPlanID!!) ?: nowTime.time
                val yearSet = planStartDate.get(Calendar.YEAR)
                val monthSet = planStartDate.get(Calendar.MONTH)
                val daySet = planStartDate.get(Calendar.DAY_OF_MONTH)

                val datePicker = DatePickerDialog(this, DatePickerDialog.OnDateSetListener {
                    _, year, month, day ->
                    planStartDate.set(year, month, day)
                    dbAdapter.setPlanStartDate(readingPlanID!!, planStartDate.time)
                    reloadDailyReading()
                }, yearSet, monthSet, daySet)
                datePicker.datePicker.maxDate = nowTime.timeInMillis
                datePicker.show()

                isHandled = true
            }
            R.id.importNewPlan -> {

                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "application/*"
                startActivityForResult(intent, RCODE_PICK_PLAN_FILE)

                isHandled = true
            }
        }

        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }

        return isHandled
    }

    private fun reloadDailyReading() {
        startActivity(Intent(app, DailyReading::class.java))
        finish()
    }

    companion object {

        private const val TAG = "DailyReading"
        private val app = BibleApplication.application
        const val RCODE_READING_PLAN_LIST = 101
        const val RCODE_PICK_PLAN_FILE = 102

        // Link AB distributed reading plan file names with plan name/description resource strings
        val ABDistributedPlanDetailArray = arrayOf(
            PlanDetails(
                "y1ntpspr",
                app.getString(R.string.plan_name_y1ntpspr),
                app.getString(R.string.plan_description_y1ntpspr)
            ),
            PlanDetails (
                "y1ot1nt1_chronological",
                app.getString(R.string.plan_name_y1ot1nt1_chronological),
                app.getString(R.string.plan_description_y1ot1nt1_chronological)
            ),
            PlanDetails(
                "y1ot1nt1_OTandNT",
                app.getString(R.string.plan_name_y1ot1nt1_OTandNT),
                app.getString(R.string.plan_description_y1ot1nt1_OTandNT)
            ),
            PlanDetails(
                "y1ot1nt1_OTthenNT",
                app.getString(R.string.plan_name_y1ot1nt1_OTthenNT),
                app.getString(R.string.plan_description_y1ot1nt1_OTthenNT)
            ),
            PlanDetails(
                "y1ot1nt2_mcheyne",
                app.getString(R.string.plan_name_y1ot1nt2_mcheyne),
                app.getString(R.string.plan_description_y1ot1nt2_mcheyne)
            ),
            PlanDetails(
                "y1ot6nt4_profHorner",
                app.getString(R.string.plan_name_y1ot6nt4_profHorner),
                app.getString(R.string.plan_description_y1ot6nt4_profHorner)
            ),
            PlanDetails(
                "y2ot1ntps2",
                app.getString(R.string.plan_name_y2ot1ntps2),
                app.getString(R.string.plan_description_y2ot1ntps2)
            )
        )
        class PlanDetails(
            val fileName: String,
            val planName: String,
            val planDescription: String
        )
    }
}
