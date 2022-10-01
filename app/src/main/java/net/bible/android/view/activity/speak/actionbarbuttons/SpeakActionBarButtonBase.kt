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

import net.bible.android.view.activity.base.actionbar.QuickActionButton
import androidx.core.view.MenuItemCompat

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class SpeakActionBarButtonBase :  QuickActionButton(MenuItemCompat.SHOW_AS_ACTION_ALWAYS) {
    /**  return true if Speak button can be shown  */
    val canSpeak get() = speakControl.isCurrentDocSpeakAvailable

    companion object {
        protected const val SPEAK_START_PRIORITY = 10
    }
}
