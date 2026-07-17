/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.readingplan

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.Screen
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.service.common.CommonUtils
import net.bible.service.db.ReadingPlansUpdatedViaSyncEvent
import net.bible.service.device.ScreenSettings
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.readingplan.OneDaysReadingsDto
import net.bible.sharedcore.readingplan.DailyReadingController
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.readingplan.DailyReadingScreen
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.versification.BookName
import org.koin.android.ext.android.inject
import java.util.Calendar

/** Compose host for the reading-plan "one day" screen — new-path twin of the classic [DailyReading]. */
class DailyReadingComposeActivity : ActivityBase() {
    private val readingPlanControl: ReadingPlanControl by inject()
    private val speakControl: SpeakControl by inject()

    private var dayLoaded: Int = 0
    private var planCodeLoaded: String? = null
    private lateinit var readingsDto: OneDaysReadingsDto

    override val integrateWithHistoryManager: Boolean = true

    /** allow activity to enhance intent to correctly restore state  */
    override val intentForHistoryList: Intent get() = intent.apply {
        putExtra(DailyReading.PLAN, readingsDto.readingPlanInfo.planCode)
        putExtra(DailyReading.DAY, readingsDto.day)
    }

    private val controller: DailyReadingController by lazy {
        DailyReadingController(
            onToggleRead = { readingNo ->
                val status = readingPlanControl.getReadingStatus(dayLoaded)
                if (status.isRead(readingNo)) status.setUnread(readingNo) else status.setRead(readingNo)
                pushUi()
            },
            onRead = { readingNo ->
                val key = readingsDto.getReadingKey(readingNo)
                readingPlanControl.read(dayLoaded, readingNo, key)
                isIntegrateWithHistoryManager = true
                finish()
            },
            onSpeak = { readingNo ->
                readingPlanControl.speak(dayLoaded, readingNo, readingsDto.getReadingKey(readingNo))
                pushUi()
            },
            onSpeakAll = {
                readingPlanControl.speak(dayLoaded, readingsDto.getReadingKeys)
                pushUi()
            },
            onDone = { onDone() },
            onPauseSpeak = { if (speakControl.isPaused) speakControl.continueAfterPause() else speakControl.pause() },
            onStopSpeak = { speakControl.stop() },
            onChangePlan = {
                selectReadingPlan.launch(ScreenLauncher.intentFor(this, Screen.ReadingPlanSelector))
            },
            onChangeDay = {
                selectReadingDay.launch(ScreenLauncher.intentFor(this, Screen.DailyReadingList))
            },
            onSetCurrentDay = { doSetCurrentDay() },
            onReset = {
                val code = planCodeLoaded
                if (code.isNullOrEmpty()) controller.showError() else { readingPlanControl.reset(code); finish() }
            },
            onSetStartDate = { showStartDatePicker() },
            onImportPlan = { importPlanLauncher.launch("application/zip") },
        )
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!readingPlanControl.isReadingPlanSelected || !readingPlanControl.currentPlanExists) {
            selectReadingPlan.launch(ScreenLauncher.intentFor(this, Screen.ReadingPlanSelector))
        } else {
            loadDailyReading(null, null)
        }

        ABEventBus.register(this) {
            onMain<ReadingPlansUpdatedViaSyncEvent> { recreate() }
            onMain<SpeakEvent> { pushSpeakState() }
        }

        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val ui by controller.ui.collectAsState()
                    val speakState by controller.speakState.collectAsState()
                    val error by controller.error.collectAsState()
                    val confirm by controller.confirm.collectAsState()
                    DailyReadingScreen(
                        ui = ui,
                        speakState = speakState,
                        error = error,
                        confirm = confirm,
                        onToggleRead = controller::toggleRead,
                        onRead = controller::read,
                        onSpeak = controller::speak,
                        onSpeakAll = controller::speakAll,
                        onDone = controller::done,
                        onPauseSpeak = controller::pauseSpeak,
                        onStopSpeak = controller::stopSpeak,
                        onChangePlan = controller::changePlan,
                        onChangeDay = controller::changeDay,
                        onSetCurrentDay = controller::requestSetCurrentDay,
                        onSetStartDate = controller::setStartDate,
                        onReset = controller::requestReset,
                        onImportPlan = controller::importPlan,
                        onConfirm = controller::confirm,
                        onDismissConfirm = controller::dismissConfirm,
                        onDismissError = controller::dismissError,
                        onNavigateUp = { finish() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    /** Load a day and push the snapshot. Mirrors the classic loadDailyReading (minus the View wiring). */
    private fun loadDailyReading(planToLoad: String?, dayToLoad: Int?) {
        try {
            val extras = intent.extras
            dayLoaded = when {
                planToLoad != null -> {
                    readingPlanControl.setReadingPlan(planToLoad)
                    dayToLoad ?: readingPlanControl.currentPlanDay
                }
                extras != null && (extras.containsKey(DailyReading.PLAN) || extras.containsKey(DailyReading.DAY)) -> {
                    extras.getString(DailyReading.PLAN)?.let { readingPlanControl.setReadingPlan(it) }
                    if (extras.containsKey(DailyReading.DAY)) extras.getInt(DailyReading.DAY)
                    else readingPlanControl.currentPlanDay
                }
                else -> readingPlanControl.currentPlanDay
            }
            planCodeLoaded = readingPlanControl.currentPlanCode
            readingsDto = readingPlanControl.getDaysReading(dayLoaded)
            pushUi()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing daily readings", e)
            controller.showError()
        }
    }

    /** Build the DailyReadingUi from the current dto + ReadingStatus (passage names formatted host-side). */
    private fun pushUi() {
        val status = readingPlanControl.getReadingStatus(dayLoaded)
        val readings = synchronized(BookName::class.java) {
            val save = BookName.isFullBookName()
            BookName.setFullBookName(!CommonUtils.isPortrait)
            try {
                (1..readingsDto.numReadings).map { i ->
                    ReadingItem(i, readingsDto.getReadingKey(i).name, status.isRead(i))
                }
            } finally {
                BookName.setFullBookName(save)
            }
        }
        controller.setUi(
            DailyReadingUi(
                planName = readingsDto.readingPlanInfo.planName ?: "",
                dayDesc = readingsDto.dayDesc,
                dateString = readingsDto.readingDateString,
                readings = readings,
                showSpeakAll = readingsDto.numReadings > 1,
                allRead = status.isAllRead,
                isDateBasedPlan = readingsDto.isDateBasedPlan,
            )
        )
    }

    private fun pushSpeakState() {
        controller.pushSpeakState(
            when {
                speakControl.isPaused -> SpeakState.PAUSED
                speakControl.isSpeaking -> SpeakState.SPEAKING
                else -> SpeakState.NONE
            }
        )
    }

    private fun onDone() {
        try {
            val nextDayToShow = readingPlanControl.done(readingsDto.readingPlanInfo, dayLoaded, false)
            if (nextDayToShow > 0) loadDailyReading(planCodeLoaded, nextDayToShow) else finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error when Done daily reading", e)
            controller.showError()
        }
    }

    private fun doSetCurrentDay() {
        try {
            val planStartDate = Calendar.getInstance()
            planStartDate.add(Calendar.DATE, -(dayLoaded - 1))
            readingPlanControl.setStartDate(readingsDto.readingPlanInfo, planStartDate.time)
            readingPlanControl.done(readingsDto.readingPlanInfo, dayLoaded - 1, true)
            loadDailyReading(planCodeLoaded, dayLoaded)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting current day", e)
            controller.showError()
        }
    }

    private fun showStartDatePicker() {
        val nowTime = Calendar.getInstance()
        val planStartDate = Calendar.getInstance()
        planStartDate.time = readingsDto.readingPlanInfo.startDate ?: nowTime.time
        val picker = DatePickerDialog(this, { _, year, month, day ->
            planStartDate.set(year, month, day)
            readingPlanControl.setStartDate(readingsDto.readingPlanInfo, planStartDate.time)
            loadDailyReading(planCodeLoaded, dayLoaded)
        }, planStartDate.get(Calendar.YEAR), planStartDate.get(Calendar.MONTH), planStartDate.get(Calendar.DAY_OF_MONTH))
        picker.datePicker.maxDate = nowTime.timeInMillis
        picker.show()
    }

    private val selectReadingPlan = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.action?.let { planCode ->
            loadDailyReading(planCode, null)
        } ?: if (!readingPlanControl.isReadingPlanSelected) finish() else {}
    }

    private val selectReadingDay = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.action?.let { day -> loadDailyReading(planCodeLoaded, day.toInt()) }
    }

    private val importPlanLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        installZipLauncher.launch(Intent(Intent.ACTION_VIEW, uri, this, InstallZip::class.java))
    }

    private val installZipLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // parity with classic: imported plan is not auto-loaded (InstallZip does not yet return the code)
    }

    companion object { private const val TAG = "DailyReadingCompose" }
}
