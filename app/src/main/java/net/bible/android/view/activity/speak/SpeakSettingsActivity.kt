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

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.view.View
import android.widget.TextView
import kotlinx.android.synthetic.main.speak_settings.*
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.*
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.service.device.speak.event.SpeakProgressEvent

@ActivityScope
class SpeakSettingsActivity : AbstractSpeakActivity() {
    companion object {
        const val TAG = "SpeakSettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.speak_settings)

        super.buildActivityComponent().inject(this)
        ABEventBus.getDefault().register(this)
        resetView(SpeakSettings.load())
    }

    override fun onDestroy() {
        ABEventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun resetView(settings: SpeakSettings) {
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
        synchronize.isChecked = settings.synchronize
        replaceDivineName.isChecked = settings.replaceDivineName
        restoreSettingsFromBookmarks.isChecked = settings.restoreSettingsFromBookmarks

        autoBookmark.isChecked = settings.autoBookmark
        if(!autoBookmark.isChecked) {
            restoreSettingsFromBookmarks.isChecked = false;
            restoreSettingsFromBookmarks.isEnabled = false;
        }
        else {
            restoreSettingsFromBookmarks.isEnabled = true;
        }
        multiTranslation.isChecked = settings.multiTranslation
        bookmarkButton.visibility = if(settings.autoBookmark) View.VISIBLE else View.GONE
    }

    fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
        currentSettings = ev.speakSettings;
        resetView(ev.speakSettings)
    }

    fun onSettingsChange(widget: View) = updateSettings()

    fun updateSettings() {
        val settings = SpeakSettings.load().apply {
            sleepTimer = currentSettings.sleepTimer
            lastSleepTimer = currentSettings.lastSleepTimer
        }
        settings.multiTranslation = multiTranslation.isChecked
        settings.synchronize = synchronize.isChecked
        settings.autoBookmark = autoBookmark.isChecked
        settings.replaceDivineName = replaceDivineName.isChecked
        settings.restoreSettingsFromBookmarks = restoreSettingsFromBookmarks.isChecked
        settings.save(updateBookmark = true)
    }

    fun onHelpButtonClick(button: View) {
        val htmlMessage = (
                "<b>${getString(R.string.conf_speak_auto_bookmark)}</b><br><br>"
                + "<b><a href=\"https://www.youtube.com/watch?v=1HFXLeTERcs\">"
                + "${getString(R.string.watch_tutorial_video)}</a></b><br><br>"
                + getString(R.string.speak_help_auto_bookmark)
                + "<br><br><b>${getString(R.string.conf_save_playback_settings_to_bookmarks)}</b><br><br>"
                + getString(R.string.speak_help_playback_settings)
                + "<br><br>"
                + getString(R.string.speak_help_playback_settings_example)
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

    fun onEventMainThread(ev: SpeakProgressEvent) {
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
    }

}
