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
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.reading_plan_one_day.*

import net.bible.android.activity.R
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanActionBarManager
import net.bible.service.readingplan.OneDaysReadingsDto

import org.crosswire.jsword.versification.BookName

import java.util.ArrayList

import javax.inject.Inject

/** Allow user to enter search criteria
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DailyReading : CustomTitlebarActivityBase(R.menu.reading_plan) {

    private var mImageTickList: MutableList<ImageView> = ArrayList()

    private var mDay: Int = 0

    private lateinit var mReadings: OneDaysReadingsDto

    @Inject lateinit var readingPlanControl: ReadingPlanControl
    @Inject lateinit var readingPlanActionBarManager: ReadingPlanActionBarManager

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, true)
        Log.i(TAG, "Displaying one day reading plan")
        setContentView(R.layout.reading_plan_one_day)

        super.buildActivityComponent().inject(this)
        super.setActionBarManager(readingPlanActionBarManager)

        try {
            // may not be for current day if user presses forward or backward
            mDay = readingPlanControl.currentPlanDay
            val extras = intent.extras
            if (extras != null) {
                if (extras.containsKey(PLAN)) {
                    readingPlanControl.setReadingPlan(extras.getString(PLAN))
                }
                if (extras.containsKey(DAY)) {
                    mDay = extras.getInt(DAY, mDay)
                }
            }

            // get readings for chosen day
            mReadings = readingPlanControl.getDaysReading(mDay)

            // Populate view
            description.text = mReadings.readingPlanInfo.description

            // date display
            day.text = mReadings.dayDesc
            date.text = mReadings.readingDateString

            // show short book name to save space if Portrait
            val fullBookNameSave = BookName.isFullBookName()
            BookName.setFullBookName(!isPortrait)

            val layout = findViewById<View>(R.id.reading_container) as TableLayout
            for (i in 0 until mReadings.numReadings) {
                val child = layoutInflater.inflate(R.layout.reading_plan_one_reading, null)

                // Ticks
                val mImageTick = child.findViewById<View>(R.id.tick) as ImageView
                mImageTickList.add(mImageTick)
                // Allow check box to be clicked to mark off the day
                mImageTick.setOnClickListener {
                    val status = readingPlanControl.getReadingStatus(mDay)
                    if (status.isRead(i)) {
                        status.setUnread(i)
                    } else {
                        status.setRead(i)
                    }
                    updateTicksAndDone()
                }

                // Passage description
                val rdgText = child.findViewById<View>(R.id.passage) as TextView
                val key = mReadings.getReadingKey(i)
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

            updateTicksAndDone()

            // Speak All
            if (mReadings.numReadings > 1) {
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
                layout.addView(child, mReadings.numReadings)
            }
            // end All

            Log.d(TAG, "Finished displaying Reading view")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing daily readings", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }

    }

    /** user pressed read button by 1 reading
     */
    fun onRead(readingNo: Int) {
        Log.i(TAG, "Read $readingNo")
        val readingKey = mReadings.getReadingKey(readingNo)
        readingPlanControl.read(mDay, readingNo, readingKey)

        finish()
    }

    /** user pressed speak button by 1 reading
     */
    fun onSpeak(readingNo: Int) {
        Log.i(TAG, "Speak $readingNo")
        val readingKey = mReadings.getReadingKey(readingNo)
        readingPlanControl.speak(mDay, readingNo, readingKey)

        updateTicksAndDone()
    }

    /** user pressed speak button by All
     */
    fun onSpeakAll(view: View?) {
        Log.i(TAG, "Speak all")
        readingPlanControl.speak(mDay, mReadings.readingKeys)

        updateTicksAndDone()
    }

    // the button that called this has been removed
    fun onNext(view: View) {
        Log.i(TAG, "Next")
        if (mDay < mReadings.readingPlanInfo.numberOfPlanDays) {
            showDay(mDay + 1)
        }
    }

    // the button that called this has been removed
    fun onPrevious(view: View) {
        Log.i(TAG, "Previous")
        if (mDay > 1) {
            showDay(mDay - 1)
        }
    }

    /** user pressed Done button so must have read currently displayed readings
     */
    fun onDone(view: View) {
        Log.i(TAG, "Done")
        try {
            // do not add to History list because it will just redisplay same page
            isIntegrateWithHistoryManager = false

            // all readings must be ticked for this to be enabled
            val nextDayToShow = readingPlanControl.done(mReadings.readingPlanInfo, mDay, false)

            //if user is behind then go to next days readings
            if (nextDayToShow > 0) {
                showDay(nextDayToShow)
            } else {
                // else exit
                finish()
            }

            // if we move away then add to history list
            isIntegrateWithHistoryManager = true
        } catch (e: Exception) {
            Log.e(TAG, "Error when Done daily reading", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }

    }

    /** allow activity to enhance intent to correctly restore state  */
    override fun getIntentForHistoryList(): Intent {
        val intent = intent

        intent.putExtra(DailyReading.PLAN, mReadings.readingPlanInfo.code)
        intent.putExtra(DailyReading.DAY, mReadings.day)

        return intent
    }


    /** I don't think this is used because of hte finish() in onSearch()
     */
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            returnToPreviousScreen()
        }
    }

    private fun showDay(dayNo: Int) {
        Log.i(TAG, "ShowDay $dayNo")
        val handlerIntent = Intent(this, DailyReading::class.java)
        handlerIntent.putExtra(DAY, dayNo)
        startActivity(handlerIntent)
        finish()
    }

    private fun updateTicksAndDone() {
        val status = readingPlanControl.getReadingStatus(mDay)

        for (i in mImageTickList.indices) {
            val imageTick = mImageTickList[i]
            if (status.isRead(i)) {
                imageTick.setImageResource(R.drawable.btn_check_buttonless_on)
            } else {
                imageTick.setImageResource(R.drawable.btn_check_buttonless_off)
            }
        }

        doneButton.isEnabled = status.isAllRead
    }


    override fun onScreenTurnedOn() {
        super.onScreenTurnedOn()
        // use reload to ensure colour is correct
        reload()
    }

    /** Could possibly push this reload up to a higher level
     * See: http://stackoverflow.com/questions/1397361/how-do-i-restart-an-android-activity
     */
    private fun reload() {
        // do not save current page to history because it is being reloaded
        val wasIntegrateWithhistory = isIntegrateWithHistoryManager
        isIntegrateWithHistoryManager = false

        // reload page to refresh if screen colour change
        val intent = intent
        finish()
        startActivity(intent)

        isIntegrateWithHistoryManager = wasIntegrateWithhistory
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
                    readingPlanControl.done(mReadings.readingPlanInfo, mDay, true)
                    updateTicksAndDone()
                } catch (e: Exception) {
                    Log.e(TAG, "Error when Done daily reading", e)
                    Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
                }

            }
            R.id.reset -> {
                readingPlanControl.reset(mReadings.readingPlanInfo)
                finish()
                isHandled = true
            }
            R.id.setStartToJan1 -> {
                readingPlanControl.setStartToJan1(mReadings.readingPlanInfo)

                // refetch readings for chosen day
                mReadings = readingPlanControl.getDaysReading(mDay)

                // update date and day no
                date.text = mReadings.readingDateString
                day.text = mReadings.dayDesc

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

        val PLAN = "net.bible.android.view.activity.readingplan.Plan"
        val DAY = "net.bible.android.view.activity.readingplan.Day"
    }
}
