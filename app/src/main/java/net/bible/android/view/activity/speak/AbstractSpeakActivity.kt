/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.speak

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.NumberPicker
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import javax.inject.Inject

abstract class AbstractSpeakActivity: CustomTitlebarActivityBase() {
    @Inject lateinit var speakControl: SpeakControl
    @Inject lateinit var bookmarkControl: BookmarkControl
    protected lateinit var currentSettings: SpeakSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSettings = SpeakSettings.load()
    }

    abstract val sleepTimer: CheckBox?

    fun setSleepTime() {
        if (sleepTimer?.isChecked ?: return) {
            val picker = NumberPicker(this)
            picker.minValue = 1
            picker.maxValue = 120
            picker.value = currentSettings.lastSleepTimer

            val layout = FrameLayout(this)
            layout.addView(picker)

            AlertDialog.Builder(this)
                    .setView(layout)
                    .setTitle(R.string.sleep_timer_title)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        currentSettings.sleepTimer = picker.value
                        currentSettings.lastSleepTimer = picker.value
                        currentSettings.save()
                        resetView(currentSettings)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> resetView(currentSettings) }
                    .show()
        }
        else {
            currentSettings.sleepTimer = 0
            currentSettings.save()
            resetView(currentSettings)
        }
    }

    abstract fun resetView(settings: SpeakSettings)


}

