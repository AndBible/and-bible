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

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.reading_plan_one_day.*
import net.bible.android.BibleApplication

import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.versification.VersificationConverter
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanActionBarManager
import net.bible.service.db.readingplan.ReadingPlanDBAdapter
import net.bible.service.db.readingplan.ReadingPlanDatabaseDefinition
import net.bible.service.db.readingplan.ReadingPlanInformationDB
import net.bible.service.db.readingplan.ReadingPlanOneDayDB
import net.bible.service.readingplan.event.ReadingPlanDayChangeEvent
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateUtils
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key

import org.crosswire.jsword.versification.BookName

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date

import javax.inject.Inject

/** Allow user to enter search criteria
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DailyReading : CustomTitlebarActivityBase(R.menu.reading_plan) {

    private val dbAdapter = ReadingPlanDBAdapter()
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    @Inject lateinit var speakControl: SpeakControl

    private val currentPageManager: CurrentPageManager
        get() = activeWindowPageManagerProvider.activeWindowPageManager

    private var mReadingPlanMetaID: Int? = null
    private var mReadingPlanDayNumber: Int? = null
    lateinit private var readingPlanOneDay: ReadingPlanOneDayDB

    private var mImageTickList: MutableList<ImageView> = ArrayList()

    //@Inject lateinit var readingPlanControl: ReadingPlanControl // Might use later yet
    @Inject lateinit var readingPlanActionBarManager: ReadingPlanActionBarManager

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, false)
        Log.i(TAG, "Displaying one day reading plan")
        setContentView(R.layout.reading_plan_one_day)

        super.buildActivityComponent().inject(this)
        super.setActionBarManager(readingPlanActionBarManager)

        ABEventBus.getDefault().register(this)
        Log.d(TAG, "Registered to ABEventBus")

        var readingPlanInfo: ReadingPlanInformationDB? = null
        var readingPlanOneDay: ReadingPlanOneDayDB? = null
        try {
            readingPlanInfo = ReadingPlanInformationDB(mReadingPlanMetaID)

            try {
                readingPlanOneDay = ReadingPlanOneDayDB(readingPlanInfo)
            } finally { }
        } catch (e: Exception) {
            Log.e(TAG,"No current active reading plan. Will load plan selection screen.")
        }

        if (readingPlanInfo == null) {
            // Load plan selection screen
            this.startActivityForResult(
                Intent(this, ReadingPlanSelectorList::class.java),
                REQUEST_CODE_READING_PLAN_LIST
            )

        } else {

            loadPlanOneDay(readingPlanOneDay, readingPlanInfo)
        }


    }

    override fun onDestroy() {
        super.onDestroy()
        ABEventBus.getDefault().unregister(this)
        Log.d(TAG, "Unregistered from ABEventBus")
    }

    /**
     * Function loads reading plan information screen with correct data
     * @param readingPlanOneDayParam Allows nulls, but the ONLY time
     * null should be passed is when no reading plan has been started.
     * @param readingPlanInformationParam Is REQUIRED ONLY when the
     * [readingPlanOneDayParam] is passed as NULL
     */
    private fun loadPlanOneDay(
        readingPlanOneDayParam: ReadingPlanOneDayDB?,
        readingPlanInformationParam: ReadingPlanInformationDB? = null,
        forceReload: Boolean = false
        ) {

        val layout: TableLayout = findViewById(R.id.reading_container)

        if (!forceReload && readingPlanOneDayParam == null && readingPlanInformationParam?.readingPlanIsDateBased == true) {
            // We have a started plan, but it is date-based and there's no reading for this day
            clearReadingInfo(layout)
            description.text = readingPlanInformationParam.readingPlanName
            date.text = SimpleDateFormat.getDateInstance().format(Calendar.getInstance().time)
            status_message.text = app.getString(R.string.this_day_has_no_readings)
            day.text = ""

        } else if (!forceReload && readingPlanOneDayParam != null) {
            readingPlanOneDay = readingPlanOneDayParam
        } else if (forceReload && readingPlanInformationParam != null) {
            readingPlanOneDay.readingPlanInfo = readingPlanInformationParam
        }

        if (readingPlanOneDayParam != null || forceReload) {
            // Load plan to screen
            val readingPlanInfo: ReadingPlanInformationDB = readingPlanOneDay.readingPlanInfo
            val previousReadingPlanShownId = mReadingPlanMetaID

            // Reload plan meta info if plan has changed or just now starting reading plan screen
            if (readingPlanInfo.readingPlanMetaID != mReadingPlanMetaID || forceReload) {
                mReadingPlanMetaID = readingPlanInfo.readingPlanMetaID

                dbAdapter.metaCurrentActiveReadingPlanID = mReadingPlanMetaID

                // Populate view
                description.text = readingPlanInfo.readingPlanName
                status_message.text = ""


            }

            // Reload plan day info if day has changed or if it's just now starting reading plan screen
            if (readingPlanInfo.readingPlanMetaID != previousReadingPlanShownId || readingPlanOneDay.dayNumber != mReadingPlanDayNumber || forceReload) {
                mReadingPlanDayNumber = readingPlanOneDay.dayNumber

                try {
                    // date display
                    day.text = when (readingPlanInfo.readingPlanIsDateBased) {
                        true -> ""
                        false -> readingPlanOneDay.dayAndNumberDescription
                    }

                    date.text = readingPlanOneDay.dateString

                    if (readingPlanActionBarManager.readingPlanTitle.isActivityInitialized()) {
                        readingPlanActionBarManager.readingPlanTitle.update(StringUtils.left(readingPlanInfo.readingPlanFileName, 8), readingPlanOneDay.dayAndNumberDescription)
                    }

                    // show short book name to save space if Portrait
                    val fullBookNameSave = BookName.isFullBookName()
                    BookName.setFullBookName(!isPortrait)

                    // Clear needed items to reload
                    clearReadingInfo(layout)

                    for (i in 0 until readingPlanOneDay.numberOfReadings) {
                        val child = layoutInflater.inflate(R.layout.reading_plan_one_reading, null)

                        // Ticks
                        val mImageTick = child.findViewById<View>(R.id.tick) as ImageView
                        mImageTickList.add(mImageTick)
                        // Allow check box to be clicked to mark off the day
                        mImageTick.setOnClickListener {
                            val status = readingPlanOneDay.readingStatus
                            if (status.isRead(i)) {
                                status.setUnread(i, mReadingPlanDayNumber!!)
                            } else {
                                status.setRead(i, mReadingPlanDayNumber!!)
                            }
                            updateTicksAndDone(readingPlanOneDay)
                        }

                        // Passage description
                        val rdgText = child.findViewById<View>(R.id.passage) as TextView
                        val key = readingPlanOneDay.getReadingKey(i)
                        rdgText.text = key.name

                        // handle read button clicks
                        val readBtn = child.findViewById<View>(R.id.readButton) as Button
                        readBtn.setOnClickListener { onRead(i) }

                        // handle speak button clicks
                        val speakBtn = child.findViewById<View>(R.id.speakButton) as Button
                        speakBtn.setOnClickListener { onSpeak(i) }

                        layout.addView(child, i)
                    }

                    // restore full book name setting
                    BookName.setFullBookName(fullBookNameSave)

                    updateTicksAndDone(readingPlanOneDay)

                    // Speak All
                    if (readingPlanOneDay.numberOfReadings > 1) {
                        val child = layoutInflater.inflate(R.layout.reading_plan_one_reading, null)

                        // hide the tick
                        val tick = child.findViewById<View>(R.id.tick) as ImageView
                        tick.visibility = View.INVISIBLE

                        // Passage description
                        val rdgText = child.findViewById<View>(R.id.passage) as TextView
                        rdgText.text = resources.getString(R.string.all)

                        val passageBtn = child.findViewById<View>(R.id.readButton) as Button
                        passageBtn.visibility = View.INVISIBLE

                        val speakBtn = child.findViewById<View>(R.id.speakButton) as Button
                        speakBtn.setOnClickListener { onSpeakAll(null) }
                        layout.addView(child, readingPlanOneDay.numberOfReadings)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error showing daily readings", e)
                    Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
                }


            }
            Log.d(TAG, "Finished displaying Reading view")

        }

    }

    fun clearReadingInfo(layout: TableLayout) {
        layout.removeAllViews()
        mImageTickList.clear()
    }

    fun onEvent(event: ReadingPlanDayChangeEvent) {
        loadPlanOneDay(event.readingPlanOneDay, event.readingPlanInfo)
    }

    /** user pressed read button by 1 reading
     */
    fun onRead(readingNumber: Int) {
        Log.i(TAG, "Read $readingNumber")
        val readingKey = readingPlanOneDay.getReadingKey(readingNumber)
        read(mReadingPlanDayNumber!!, readingNumber, readingKey)

        finish()
    }

    /** user pressed speak button by 1 reading
     */
    fun onSpeak(readingNumber: Int) {
        Log.i(TAG, "Speak $readingNumber")
        val readingKey = readingPlanOneDay.getReadingKey(readingNumber)
        speak(mReadingPlanDayNumber!!, readingNumber, readingKey)

        updateTicksAndDone(readingPlanOneDay)
    }

    /** user pressed speak button by All
     */
    fun onSpeakAll(view: View?) {
        Log.i(TAG, "Speak all")
        speak(mReadingPlanDayNumber!!, readingPlanOneDay.readingChaptersKeyArray!!)

        updateTicksAndDone(readingPlanOneDay)
    }

    /** User wants to read a passage from the daily reading
     * Also mark passage as read
     */
    fun read(day: Int, readingNumber: Int, readingKey: Key?) {
        if (readingKey != null) {
            isIntegrateWithHistoryManager = true

            // mark reading as 'read'
            readingPlanOneDay.readingStatus.setRead(readingNumber, mReadingPlanDayNumber!!)

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

    private fun convertReadingVersification(readingKey: Key, bibleToBeUsed: AbstractPassageBook): List<Key> {
        val documentV11n = bibleToBeUsed.versification

        val v11nConverter = VersificationConverter()
        val convertedPassage = v11nConverter.convert(readingKey, documentV11n)

        val keyList = java.util.ArrayList<Key>()
        keyList.add(convertedPassage)
        return keyList
    }

    /**
     * Speak 1 reading and mark as read.  Also convert from ReadingPlan v11n type to v11n type of current Bible.
     */
    fun speak(day: Int, readingNo: Int, readingKey: Key) {
        val bible = currentPageManager.currentBible.currentPassageBook
        val keyList = convertReadingVersification(readingKey, bible)

        speakControl.speakKeyList(bible, keyList, true, false)

        readingPlanOneDay.readingStatus.setRead(readingNo, mReadingPlanDayNumber!!)
    }

    /** User wants all passages from the daily reading spoken using TTS
     * Also mark passages as read
     */
    fun speak(day: Int, allReadings: List<Key>) {
        val bible = currentPageManager.currentBible.currentPassageBook
        val allReadingsWithCorrectV11n = java.util.ArrayList<Key>()
        for (key in allReadings) {
            val keyList = convertReadingVersification(key, bible)
            allReadingsWithCorrectV11n.addAll(keyList)
        }
        speakControl.speakKeyList(bible, allReadingsWithCorrectV11n, true, false)

        // mark all readings as read
        for (i in allReadings.indices) {
            readingPlanOneDay.readingStatus.setRead(i, mReadingPlanDayNumber!!)
        }
    }

    /** user pressed Done button so must have read currently displayed readings
     */
    fun onDone(view: View) {
        Log.i(TAG, "Done")
        try {
            // do not add to History list because it will just redisplay same page
            isIntegrateWithHistoryManager = false

            // all readings must be ticked for Done button to be enabled
            val nextDayToShow = done( false)
            Log.d(TAG, "Next day to show is $nextDayToShow")

            //if user is behind then go to next days readings
            if (nextDayToShow > 0 && !readingPlanOneDay.readingPlanInfo.readingPlanIsDateBased) {
                loadPlanOneDay(ReadingPlanOneDayDB(readingPlanOneDay.readingPlanInfo))
            } else {
                // else exit
                finish()
            }

            // if we move away then add to history list
//            isIntegrateWithHistoryManager = true
        } catch (e: Exception) {
            Log.e(TAG, "Error when Done daily reading", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }

    }

    /** mark this day as complete unless it is in the future
     * if last day then reset plan
     */
    fun done(force: Boolean): Int {
        // which day to show next -1 means the user is up to date and can close Reading Plan
        var nextDayToShow = -1

        val planID: Int = mReadingPlanMetaID!!
        val dayNumber: Int = mReadingPlanDayNumber!!
        // force Done to work for whatever day is passed in, otherwise Done only works for current plan day and ignores other days
        if (force) {
            // for Done to work for non plan day
            dbAdapter.setMetaCurrentDayNumber(planID, dayNumber)

            // normal reading status update is circumvented so mark all days as read up to here
            dbAdapter.setAllDaysReadUpTo(mReadingPlanDayNumber!!)
            readingPlanOneDay.readingStatus.setAllRead(mReadingPlanDayNumber!!)
        }

        // was this the next reading plan day due whether on schedule or not
        Log.d(TAG, "The current day number for the plan is ${dbAdapter.getMetaCurrentDayNumber(planID)}")
        Log.d(TAG, "The loaded day number on the screen is $dayNumber")
        if (dbAdapter.getMetaCurrentDayNumber(planID) == dayNumber) {
            // was this the last day in the plan
            if (readingPlanOneDay.readingPlanInfo.readingPlanTotalDays == mReadingPlanDayNumber) {
                // TODO: Give user the option to reset plan since it's done now.
                nextDayToShow = dayNumber
            } else {
                // move to next plan day
                nextDayToShow = dbAdapter.incrementCurrentPlanDay()

            }
        } else {
            if (readingPlanOneDay.readingPlanInfo.readingPlanTotalDays > mReadingPlanDayNumber ?: 0) {
                nextDayToShow = mReadingPlanDayNumber ?: 0 + 1
            }
        }

        //if user is not behind then do not show Daily Reading screen
        if (!readingPlanOneDay.readingPlanInfo.readingPlanIsDateBased && !isDueToBeRead(mReadingPlanMetaID!!,mReadingPlanDayNumber!!)) {
            nextDayToShow = -1
        }


        return nextDayToShow
    }

    private fun isDueToBeRead(readingPlanMetaId: Int, day: Int): Boolean {
        return dbAdapter.getDueDayToBeRead(readingPlanMetaId) >= day
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "requestCode=$requestCode -- resultCode=$resultCode")
        // If no reading plan is selected, and there is none selected from before, exit DailyReading
        if (requestCode == REQUEST_CODE_READING_PLAN_LIST && resultCode != RESULT_OK) {
            if (dbAdapter.metaCurrentActiveReadingPlanID == null) {
                finish()
            }
        }
    }

    private fun updateTicksAndDone(readingPlanOneDay: ReadingPlanOneDayDB) {
        val status = readingPlanOneDay.readingStatus

        for (i in mImageTickList.indices) {
            val imageTick = mImageTickList[i]
            if (status.isRead(i)) {
                imageTick.setImageResource(R.drawable.btn_check_buttonless_on)
            } else {
                imageTick.setImageResource(R.drawable.btn_check_buttonless_off)
            }
        }

        doneButton.isEnabled = status.isAllRead()
    }

    /**
     * on Click handlers
     */
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
                        Log.d(TAG, "Resetting plan id ${mReadingPlanMetaID}.")
                        dbAdapter.resetPlan(mReadingPlanMetaID!!)
                        finish()
                    }

                isHandled = true
            }
            R.id.setStartToJan1 -> {
                dbAdapter.setMetaReadingPlanStartDate(mReadingPlanMetaID!!, DateUtils.truncate(Date(), Calendar.YEAR))

                loadPlanOneDay(null, ReadingPlanInformationDB(mReadingPlanMetaID), true)

                isHandled = true
            }
            R.id.importNewPlan -> {

                var permissionGranted: Boolean = false
                val permission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)

                if (permission != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission to access external storage denied")
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        101)

                } else {
                    permissionGranted = true
                }

                if (permissionGranted) {
                    Log.i(TAG, "Permission is granted to access external files.")
                    ReadingPlanDatabaseDefinition.Operations.importReadingPlansToDatabase(ReadingPlanDBAdapter.dbHelper.writableDatabase)

                    // TODO: Convert to string resource
                    Toast.makeText(this, "Reading plans have been imported.", Toast.LENGTH_SHORT).show()
                }
                isHandled = true
            }
        }

        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }

        return isHandled
    }

    companion object {

        private val TAG = "DailyReading"
        private val app = BibleApplication.application
        const val REQUEST_CODE_READING_PLAN_LIST = 101

        // Link AB distributed reading plan file names with plan name/description resource strings
        val ABDistributedPlanDetailArray: Array<PlanDetails> = arrayOf(
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
