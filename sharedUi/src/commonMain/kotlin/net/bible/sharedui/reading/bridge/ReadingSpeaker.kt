/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedui.reading.bridge

import net.bible.android.control.speak.SpeakControl
import net.bible.service.sword.BookAndKey
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse

/** The part of speech control the reading bridge uses; a seam so the speak calls are testable without TTS. */
interface ReadingSpeaker {
    val isSpeaking: Boolean
    fun pauseForNextSpeech()
    fun speakBible(book: SwordBook, verse: Verse)
    fun speakMemorizationLoop(book: SwordBook, startVerse: Verse, endVerse: Verse)
    fun speakGeneric(bookAndKey: BookAndKey)
}

/** [ReadingSpeaker] backed by the app's [SpeakControl]. */
fun SpeakControl.asReadingSpeaker(): ReadingSpeaker = object : ReadingSpeaker {
    override val isSpeaking get() = this@asReadingSpeaker.isSpeaking
    override fun pauseForNextSpeech() = pause(willContinueAfterThis = true, toast = false)
    override fun speakBible(book: SwordBook, verse: Verse) = this@asReadingSpeaker.speakBible(book, verse)
    override fun speakMemorizationLoop(book: SwordBook, startVerse: Verse, endVerse: Verse) =
        this@asReadingSpeaker.speakMemorizationLoop(book, startVerse, endVerse)
    override fun speakGeneric(bookAndKey: BookAndKey) = this@asReadingSpeaker.speakGeneric(bookAndKey)
}
