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

package net.bible.android.view.activity.speak

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.SeekBar
import kotlinx.android.synthetic.main.speak_general.*
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.NumPagesToSpeakDefinition
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.device.speak.event.SpeakEvent

/** Allow user to listen to text via TTS

 * @author Martin Denham [mjdenham at gmail dot com]
 * *
 */
class GeneralSpeakActivity : AbstractSpeakActivity() {
    private lateinit var numPagesToSpeakDefinitions: Array<NumPagesToSpeakDefinition>

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Speak view")

        setContentView(R.layout.speak_general)

        super.buildActivityComponent().inject(this)

        // set title of chapter/verse/page selection
        numPagesToSpeakDefinitions = speakControl.calculateNumPagesToSpeakDefinitions()

        // set a suitable prompt for the different numbers of chapters
        numPagesToSpeakDefinitions.forEach {
            val numChaptersCheckBox = findViewById<RadioButton>(it.radioButtonId)
            numChaptersCheckBox.text = it.getPrompt()
        }

        // set defaults for Queue and Repeat
        when(currentSettings.numPagesToSpeakId) {
            0 -> numChapters1.isChecked = true
            1 -> numChapters2.isChecked = true
            2 -> numChapters3.isChecked = true
            3 -> numChapters4.isChecked = true
        }
        queue.isChecked = currentSettings.queue
        repeat.isChecked = currentSettings.repeat
        speakSpeed.progress = currentSettings.playbackSettings.speed
        speedStatus.text = "${currentSettings.playbackSettings.speed} %"
        speakSpeed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speedStatus.text = "$progress %"
                currentSettings.playbackSettings.speed = progress
                currentSettings.save()
            }
        })
        resetView(this.currentSettings)
        ABEventBus.getDefault().register(this)

        Log.d(TAG, "Finished displaying Speak view")
    }

    override fun onDestroy() {
        ABEventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    fun onEventMainThread(ev: SpeakEvent) {
        speakPauseButton.setImageResource(
                if(ev.isSpeaking)
                    android.R.drawable.ic_media_pause
                else
                android.R.drawable.ic_media_play
        )
    }

    fun updateSettings(b: View) {
        currentSettings.queue = queue.isChecked
        currentSettings.repeat = repeat.isChecked
        currentSettings.numPagesToSpeakId = if(numChapters1.isChecked) 0 else if (numChapters2.isChecked) 1 else if(numChapters3.isChecked) 2 else 3
        currentSettings.save()
    }

    fun onButtonClick(button: View) {
        try {
            when (button) {
                rewindButton -> speakControl.rewind()
                stopButton -> speakControl.stop()
                speakPauseButton ->
                    when {
                        speakControl.isPaused -> speakControl.continueAfterPause()
                        speakControl.isSpeaking -> speakControl.pause()
                        else -> speakControl.speakText()
                    }
                forwardButton -> speakControl.forward()
            }
        } catch (e: Exception) {
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }
    }

    override fun resetView(settings: SpeakSettings) {
        sleepTimer.isChecked = settings.sleepTimer > 0
        sleepTimer.text = if(settings.sleepTimer>0) getString(R.string.sleep_timer_set, settings.sleepTimer) else getString(R.string.conf_speak_sleep_timer)
        speakPauseButton.setImageResource(
                if(speakControl.isSpeaking)
                    android.R.drawable.ic_media_pause
                else
                android.R.drawable.ic_media_play
        )
    }

    companion object {
        private const val TAG = "Speak"
    }
}
