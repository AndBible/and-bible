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

package net.bible.service.device.speak

import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.speak.SpeakSettingsChangedEvent

interface SpeakTextProvider {
    val numItemsToTts: Int
    fun getStatusText(showFlag: Int): String
    fun isMoreTextToSpeak(): Boolean
    fun getNextSpeakCommand(utteranceId: String, isCurrent: Boolean = false): SpeakCommand
    fun getTotalChars(): Long
    fun getSpokenChars(): Long
    fun pause()
    fun stop()
    fun rewind(amount: SpeakSettings.RewindAmount?)
    fun forward(amount: SpeakSettings.RewindAmount?)
    fun getText(utteranceId: String): String
    fun finishedUtterance(utteranceId: String)
    fun startUtterance(utteranceId: String)
    fun reset()
    fun persistState()
    fun restoreState(): Boolean
    fun clearPersistedState()
    fun prepareForStartSpeaking()
    fun savePosition(fractionCompleted: Float)
    fun updateSettings(speakSettingsChangedEvent: SpeakSettingsChangedEvent) {}
}
