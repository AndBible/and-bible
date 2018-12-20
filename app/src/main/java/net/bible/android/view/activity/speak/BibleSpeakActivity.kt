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

package net.bible.android.view.activity.speak

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.speak_bible.*
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.*
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent

@ActivityScope
class BibleSpeakActivity : AbstractSpeakActivity() {
    companion object {
        const val TAG = "BibleSpeakActivity"
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.speak_bible)
        super.buildActivityComponent().inject(this)
        ABEventBus.getDefault().register(this)

        speakSpeed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speedStatus.text = "$progress %"
                if(fromUser) {
                    updateSettings()
                }
            }
        })

        resetView(SpeakSettings.load())
    }

    override fun onDestroy() {
        ABEventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun resetView(settings: SpeakSettings) {
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
        speakChapterChanges.isChecked = settings.playbackSettings.speakChapterChanges
        speakTitles.isChecked = settings.playbackSettings.speakTitles
        speakFootnotes.isChecked = settings.playbackSettings.speakFootnotes
        speakSpeed.progress = settings.playbackSettings.speed
        speedStatus.text = "${settings.playbackSettings.speed} %"
        sleepTimer.isChecked = settings.sleepTimer > 0
        sleepTimer.text = if(settings.sleepTimer>0) getString(R.string.sleep_timer_set, settings.sleepTimer) else getString(R.string.conf_speak_sleep_timer)
        speakPauseButton.setImageResource(
                if(speakControl.isSpeaking)
                    android.R.drawable.ic_media_pause
                else
                android.R.drawable.ic_media_play
        )
        bookmarkButton.visibility = if(settings.autoBookmark) View.VISIBLE else View.GONE
    }

    fun onEventMainThread(ev: SpeakProgressEvent) {
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
    }


    fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
        currentSettings = ev.speakSettings;
        resetView(ev.speakSettings)
    }

    fun onEventMainThread(ev: SpeakEvent) {
        speakPauseButton.setImageResource(
                if(ev.isSpeaking)
                    android.R.drawable.ic_media_pause
                else
                android.R.drawable.ic_media_play
        )
    }

    fun onHelpButtonClick(widget: View) {
        val htmlMessage = (
                "<b>${getString(R.string.speak)}</b><br><br>"
                + "<b><a href=\"https://youtu.be/_wWnS-pjv2A\">"
                + "${getString(R.string.watch_tutorial_video)}</a></b>"
                )

        val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(htmlMessage)
        }

        val d = AlertDialog.Builder(this)
                .setMessage(spanned)
                .setPositiveButton(android.R.string.ok) { _, _ ->  }
                .create()

        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    fun onSettingsChange(widget: View) = updateSettings()

    fun updateSettings() {
        SpeakSettings.load().apply {
            playbackSettings = PlaybackSettings(
                    speakChapterChanges = speakChapterChanges.isChecked,
                    speakTitles = speakTitles.isChecked,
                    speakFootnotes = speakFootnotes.isChecked,
                    speed = speakSpeed.progress
            )
            sleepTimer = currentSettings.sleepTimer
            lastSleepTimer = currentSettings.lastSleepTimer
            save(updateBookmark = true)
        }
    }

    fun onButtonClick(button: View) {
        try {
            when (button) {
                prevButton -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
                nextButton -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
                rewindButton -> speakControl.rewind()
                stopButton -> speakControl.stop()
                speakPauseButton ->
                    if (speakControl.isPaused) {
                        speakControl.continueAfterPause()
                    } else if (speakControl.isSpeaking) {
                        speakControl.pause()
                    } else {
                        speakControl.speakBible()
                    }
                forwardButton -> speakControl.forward()
            }
        } catch (e: Exception) {
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
            Log.e(TAG, "Error: ", e)
        }
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
    }

    fun openMoreSettings(button: View) {
        startActivity(Intent(this, SpeakSettingsActivity::class.java))
    }
    fun openSystemSettings(button: View) {
        startActivity(Intent("com.android.settings.TTS_SETTINGS"))
    }
}
