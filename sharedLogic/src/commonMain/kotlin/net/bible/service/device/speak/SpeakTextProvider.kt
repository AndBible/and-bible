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

package net.bible.service.device.speak

import net.bible.android.control.speak.SpeakSettingsChange
import net.bible.android.database.bookmarks.SpeakSettings
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

interface SpeakTextProvider {
    var isSpeaking: Boolean
    val numItemsToTts: Int
    suspend fun getStatusText(showFlag: Int): String
    suspend fun getStatusTexts(): Map<Int, String> = (0..3).associateWith { getStatusText(it) }
    fun isMoreTextToSpeak(): Boolean
    /** Revalidate the manager request immediately before publishing a privately calculated cursor. */
    suspend fun getNextSpeakCommand(utteranceId: String, isCurrent: Boolean = false, isValid: () -> Boolean = { true }): SpeakCommand
    fun getTotalChars(): Long
    fun getSpokenChars(): Long
    fun pause()
    fun stop()
    suspend fun rewind(amount: SpeakSettings.RewindAmount?, isValid: () -> Boolean = { true })
    suspend fun forward(amount: SpeakSettings.RewindAmount?, isValid: () -> Boolean = { true })
    fun getText(utteranceId: String): String
    fun finishedUtterance(utteranceId: String)
    fun startUtterance(utteranceId: String)
    fun reset()
    fun persistState()
    fun restoreState(): Boolean
    fun clearPersistedState()
    suspend fun loadBookmarkState() {}

    fun prepareForStartSpeaking()
    fun savePosition(fractionCompleted: Double)
    fun updateSettings(change: SpeakSettingsChange) {}
    fun getCurrentlyPlayingKey(): Key? = null
    fun getCurrentlyPlayingBook(): Book? = null
}
