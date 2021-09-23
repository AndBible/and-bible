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

package net.bible.android.view.activity.readingplan

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuCompat
import net.bible.android.BibleApplication

import net.bible.android.activity.R
import net.bible.android.activity.databinding.ReadingPlanOneDayBinding
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanActionBarManager
import net.bible.service.common.CommonUtils
import net.bible.service.readingplan.OneDaysReadingsDto

import org.crosswire.jsword.versification.BookName

import java.util.ArrayList
import java.util.Calendar

import javax.inject.Inject

/** Allow user to enter search criteria
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DailyReading : CustomTitlebarActivityBase(R.menu.reading_plan) {

    private lateinit var binding: ReadingPlanOneDayBinding

    private var imageTickList: MutableList<ImageView> = ArrayList()

    private var dayLoaded: Int = 0
    private var planCodeLoaded: String? = null

    private lateinit var readingsDto: OneDaysReadingsDto

    @Inject lateinit var readingPlanControl: ReadingPlanControl
    @Inject lateinit var readingPlanActionBarManager: ReadingPlanActionBarManager

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, true)
        super.buildActivityComponent().inject(this)

        if (!readingPlanControl.isReadingPlanSelected || !readingPlanControl.currentPlanExists) {
            val intent = Intent(this, ReadingPlanSelectorList::class.java)
            startActivity(intent)
            finish()
            return
        }

        Log.i(TAG, "Displaying one day reading plan")
        binding = ReadingPlanOneDayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        super.setActionBarManager(readingPlanActionBarManager)

        try {
            // may not be for current day if user presses forward or backward
            dayLoaded = readingPlanControl.currentPlanDay
            val extras = intent.extras
            if (extras != null) {
                val plan = extras.getString(PLAN)
                if(plan != null) readingPlanControl.setReadingPlan(plan)
                dayLoaded = extras.getInt(DAY, dayLoaded)
            }

            planCodeLoaded = readingPlanControl.currentPlanCode

            // get readings for chosen day
            readingsDto = readingPlanControl.getDaysReading(dayLoaded)

            binding.apply {
                // Populate view
                description.text = readingsDto.readingPlanInfo.planName

                // date display
                day.text = readingsDto.dayDesc
                date.text = readingsDto.readingDateString
                doneButton.setOnClickListener { onDone() }
            }

            val layout = findViewById<View>(R.id.reading_container) as TableLayout

            // show short book name to save space if Portrait
            synchronized(BookName::class) {
                val fullBookNameSave = BookName.isFullBookName()
                BookName.setFullBookName(!CommonUtils.isPortrait)

                for (i in 1..readingsDto.numReadings) {
                    val child = layoutInflater.inflate(R.layout.reading_plan_one_reading, null)

                    // Ticks
                    val mImageTick = child.findViewById<View>(R.id.tick) as ImageView
                    imageTickList.add(mImageTick)
                    // Allow check box to be clicked to mark off the day
                    mImageTick.setOnClickListener {
                        val status = readingPlanControl.getReadingStatus(dayLoaded)
                        if (status.isRead(i)) {
                            status.setUnread(i)
                        } else {
                            status.setRead(i)
                        }
                        updateTicksAndDone()
                    }

                    // Passage description
                    val rdgText = child.findViewById<View>(R.id.passage) as TextView
                    val key = readingsDto.getReadingKey(i)
                    rdgText.text = key.name

                    // handle read button clicks
                    val readBtn = child.findViewById<View>(R.id.readButton) as Button
                    readBtn.setOnClickListener { onRead(i) }

                    // handle speak button clicks
                    val speakBtn = child.findViewById<View>(R.id.speakButton) as Button
                    speakBtn.setOnClickListener { onSpeak(i) }

                    layout.addView(child, i - 1)
                }

                // restore full book name setting
                BookName.setFullBookName(fullBookNameSave)
            }
            updateTicksAndDone()

            // Speak All
            if (readingsDto.numReadings > 1) {
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
                speakBtn.setOnClickListener { onSpeakAll() }
                layout.addView(child, readingsDto.numReadings)
            }
            // end All

            Log.d(TAG, "Finished displaying Reading view")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing daily readings", e)
            Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        MenuCompat.setGroupDividerEnabled(menu, true)
        return super.onCreateOptionsMenu(menu)
    }

    /** user pressed read button by 1 reading
     */
    private fun onRead(readingNo: Int) {
        Log.i(TAG, "Read $readingNo")
        val readingKey = readingsDto.getReadingKey(readingNo)
        readingPlanControl.read(dayLoaded, readingNo, readingKey)

        finish()
    }

    /** user pressed speak button by 1 reading
     */
    private fun onSpeak(readingNo: Int) {
        Log.i(TAG, "Speak $readingNo")
        val readingKey = readingsDto.getReadingKey(readingNo)
        readingPlanControl.speak(dayLoaded, readingNo, readingKey)

        updateTicksAndDone()
    }

    /** user pressed speak button by All
     */
    private fun onSpeakAll() {
        Log.i(TAG, "Speak all")
        readingPlanControl.speak(dayLoaded, readingsDto.getReadingKeys)

        updateTicksAndDone()
    }

    /** user pressed Done button so must have read currently displayed readings
     */
    fun onDone() {
        Log.i(TAG, "Done")
        try {
            // do not add to History list because it will just redisplay same page
            isIntegrateWithHistoryManager = false

            // all readings must be ticked for this to be enabled
            val nextDayToShow = readingPlanControl.done(readingsDto.readingPlanInfo, dayLoaded, false)

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
            Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
        }

    }

    /** allow activity to enhance intent to correctly restore state  */
    override val intentForHistoryList: Intent get() {
        val intent = intent

        intent.putExtra(PLAN, readingsDto.readingPlanInfo.planCode)
        intent.putExtra(DAY, readingsDto.day)

        return intent
    }

    private fun showDay(dayNo: Int) {
        Log.i(TAG, "ShowDay $dayNo")
        val handlerIntent = Intent(this, DailyReading::class.java)
        handlerIntent.putExtra(DAY, dayNo)
        startActivity(handlerIntent)
        finish()
    }

    private fun updateTicksAndDone() {
        val status = readingPlanControl.getReadingStatus(dayLoaded)

        for (i in imageTickList.indices) {
            val imageTick = imageTickList[i]
            if (status.isRead(i+1)) {
                imageTick.setImageResource(R.drawable.btn_check_buttonless_on)
            } else {
                imageTick.setImageResource(R.drawable.btn_check_buttonless_off)
            }
        }

        binding.doneButton.isEnabled = status.isAllRead
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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (readingsDto.isDateBasedPlan) {
            menu.findItem(R.id.setCurrentDay).isVisible = false
            menu.findItem(R.id.setStartDate).isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * on Click handlers
     */
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.setCurrentDay -> {
            // selected to allow jump to a certain day
            Log.i(TAG, "Set current day")
            try {
                Dialogs.instance.showMsg(R.string.msg_set_current_day_reading_plan, true)
                {
                    // set previous day as finish, so that today's reading status will not be changed
                    readingPlanControl.done(readingsDto.readingPlanInfo, dayLoaded - 1, true)
                    updateTicksAndDone()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error when Done daily reading", e)
                Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
            }

            true
        }
        R.id.reset -> {
            val code = planCodeLoaded
            if (code.isNullOrEmpty()) {
                Log.e(TAG, "Could not reset plan because no plan is properly loaded")
                Dialogs.instance.showErrorMsg(R.string.error_occurred)
            } else {
                Dialogs.instance.showMsg(R.string.reset_plan_question, true)
                {
                    readingPlanControl.reset(code)
                    finish()
                }
            }

            true
        }
        R.id.setStartDate -> {

            val nowTime = Calendar.getInstance()
            val planStartDate = Calendar.getInstance()
            planStartDate.time = readingsDto.readingPlanInfo.startDate ?: nowTime.time
            val yearSet = planStartDate.get(Calendar.YEAR)
            val monthSet = planStartDate.get(Calendar.MONTH)
            val daySet = planStartDate.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(this, DatePickerDialog.OnDateSetListener { _, year, month, day_ ->
                planStartDate.set(year, month, day_)
                readingPlanControl.setStartDate(readingsDto.readingPlanInfo, planStartDate.time)

                // refetch readings for chosen day
                readingsDto = readingPlanControl.getDaysReading(dayLoaded)

                // update date and day no
                binding.date.text = readingsDto.readingDateString
                binding.day.text = readingsDto.dayDesc
            }, yearSet, monthSet, daySet)
            datePicker.datePicker.maxDate = nowTime.timeInMillis
            datePicker.show()

            true
        }
        R.id.import_reading_plan -> {
            importPlanLauncher.launch("application/zip")
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    val importPlanLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uriResult ->
        Log.d(TAG, "Importing plan. Result uri is${if (uriResult != null) " not" else ""} null")
        val uri = uriResult ?: return@registerForActivityResult

        val intent = Intent(Intent.ACTION_VIEW, uri, this, InstallZip::class.java)
        installZipLauncher.launch(intent)
    }

    val installZipLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // TODO load imported plan if result is OK
    }

    companion object {

        private const val TAG = "DailyReading"
        private val app = BibleApplication.application

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
            val planCode: String,
            val planName: String,
            val planDescription: String
        )

        val PLAN = "net.bible.android.view.activity.readingplan.Plan"
        val DAY = "net.bible.android.view.activity.readingplan.Day"
    }
}
