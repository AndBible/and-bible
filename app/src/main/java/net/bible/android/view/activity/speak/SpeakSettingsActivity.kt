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

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import android.view.View
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SpeakSettingsBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.*
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.view.activity.ActivityScope

@ActivityScope
class SpeakSettingsActivity : AbstractSpeakActivity() {
    companion object {
        const val TAG = "SpeakSettingsActivity"
    }

    private lateinit var binding: SpeakSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SpeakSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        super.buildActivityComponent().inject(this)
        ABEventBus.getDefault().register(this)
        resetView(SpeakSettings.load())
    }

    override fun onDestroy() {
        ABEventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun resetView(settings: SpeakSettings) {
        binding.apply {
            synchronize.isChecked = settings.synchronize
            replaceDivineName.isChecked = settings.replaceDivineName
            restoreSettingsFromBookmarks.isChecked = settings.restoreSettingsFromBookmarks

            autoBookmark.isChecked = settings.autoBookmark
            if (!autoBookmark.isChecked) {
                restoreSettingsFromBookmarks.isChecked = false
                restoreSettingsFromBookmarks.isEnabled = false
            } else {
                restoreSettingsFromBookmarks.isEnabled = true
            }
        }
    }

    fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
        currentSettings = ev.speakSettings
        resetView(ev.speakSettings)
    }

    fun onSettingsChange(widget: View) = updateSettings()

    fun updateSettings() {
        val settings = SpeakSettings.load().apply {
            sleepTimer = currentSettings.sleepTimer
            lastSleepTimer = currentSettings.lastSleepTimer
        }
        binding.apply {
            settings.synchronize = synchronize.isChecked
            settings.autoBookmark = autoBookmark.isChecked
            settings.replaceDivineName = replaceDivineName.isChecked
            settings.restoreSettingsFromBookmarks = restoreSettingsFromBookmarks.isChecked
        }
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
}
