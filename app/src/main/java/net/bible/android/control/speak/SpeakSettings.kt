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

package net.bible.android.control.speak
import android.util.Log
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.bookmarks.TAG
import net.bible.service.common.CommonUtils

const val PERSIST_SETTINGS = "SpeakSettings"

data class SpeakSettingsChangedEvent(val speakSettings: SpeakSettings, val updateBookmark: Boolean = false, val sleepTimerChanged: Boolean = false)

fun SpeakSettings.save(updateBookmark: Boolean = false) {
    if(SpeakSettings.Companion.currentSettings?.equals(this) != true) {
        CommonUtils.realSharedPreferences.edit().putString(PERSIST_SETTINGS, toJson()).apply()
        Log.i(TAG, "SpeakSettings saved! $this")
        val oldSettings = SpeakSettings.Companion.currentSettings
        SpeakSettings.Companion.currentSettings = this.makeCopy()
        ABEventBus.getDefault().post(SpeakSettingsChangedEvent(this,
                updateBookmark && oldSettings?.playbackSettings?.equals(this.playbackSettings) != true,
                 oldSettings?.sleepTimer != this.sleepTimer))
    }
}

fun SpeakSettings.Companion.load(): SpeakSettings {
    val rv = currentSettings?.makeCopy()?: {
        // Excuse of using realSharedPreferences here is that this is loaded early because of widgets. But in practice,
        // these are persisted to workspace in WindowRepository and loaded from there.
        // This code could be therefore cleaned up.
        val sharedPreferences = CommonUtils.realSharedPreferences
        val settings = fromJson(sharedPreferences.getString(PERSIST_SETTINGS, "")!!)
        settings }()
    Log.i(TAG, "SpeakSettings loaded! $rv")
    return rv
}
