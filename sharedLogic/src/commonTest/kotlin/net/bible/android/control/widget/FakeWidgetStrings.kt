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
package net.bible.android.control.widget

/** English [WidgetStrings] for tests; every member returns a distinct, recognisable text. */
internal class FakeWidgetStrings(private val tag: String = "") : WidgetStrings {
    override val appName = "AndBible$tag"
    override val speakName = "Speak$tag"
    override val speakTextName = "Speak with text$tag"
    override val listeningListName = "Listening list$tag"
    override val verseName = "Verse$tag"
    override fun continueFrom(reference: String, translation: String) = "Continue from $reference · $translation$tag"
    override val continueListening = "Continue listening$tag"
    override val paused = "Paused$tag"
    override fun statusLine(reference: String, percent: Int, translation: String) = "$reference · $percent % · $translation$tag"
    override val listeningListEmptyTitle = "No speak bookmarks yet$tag"
    override val listeningListEmptyText = "Bookmarks with the Speak label appear here.$tag"
    override val openAppToLoad = "Open AndBible to load verses$tag"
    override val toggleSleepTimer = "Toggle sleep timer$tag"
    override fun sleepMinutes(minutes: Int) = "$minutes min$tag"
    override fun verseUnavailable(reason: VerseWidgetContent.Unavailable.Reason) = "unavailable ${reason.name}$tag"
    override fun randomSourceName(part: VerseSource.RandomPart) = "Random ${part.name}$tag"
    override val readingPlanSourceName = "Today's reading$tag"
}
