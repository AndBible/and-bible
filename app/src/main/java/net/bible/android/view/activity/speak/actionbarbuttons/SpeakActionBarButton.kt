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
package net.bible.android.view.activity.speak.actionbarbuttons

import android.util.Log
import android.view.MenuItem
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.android.control.ApplicationScope
import javax.inject.Inject
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.document.DocumentControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.activity.R
import java.lang.Exception

/**
 * Start speaking current page
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class SpeakActionBarButton @Inject constructor(
    speakControl: SpeakControl,
    private val documentControl: DocumentControl
) : SpeakActionBarButtonBase(speakControl) {
    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        try {
            speakControl.toggleSpeak()
            update(menuItem)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speech", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
        return true
    }

    override fun getTitle(): String {
        return getResourceString(R.string.speak)
    }

    override fun getIcon(): Int {
        return if (speakControl.isSpeaking) {
            android.R.drawable.ic_media_pause
        } else if (speakControl.isPaused) {
            android.R.drawable.ic_media_play
        } else {
            R.drawable.ic_baseline_headphones_24
        }
    }

    override fun canShow(): Boolean {
        // show if speakable or already speaking (to pause), and only if plenty of room
        return (super.canSpeak() || isSpeakMode) &&
                (isWide || !documentControl.isStrongsInBook || isSpeakMode)
    }

    companion object {
        private const val TAG = "SpeakActionBarButtonBas"
    }
}
