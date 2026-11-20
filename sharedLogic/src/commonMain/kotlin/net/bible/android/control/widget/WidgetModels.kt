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

import net.bible.android.database.IdType

/** How often a Verse widget moves on to another verse. */
enum class VerseChangeFrequency { DAILY, EVERY_3_HOURS, ON_SHUFFLE }

/** Where a Verse widget takes its verses from. */
sealed interface VerseSource {
    /** The Bible bookmarks of one user label. */
    data class Label(val labelId: IdType) : VerseSource

    /** The verse range a [Random] source draws from. */
    enum class RandomPart { WHOLE_BIBLE, NEW_TESTAMENT, PSALMS_PROVERBS }

    /** A uniformly random verse of [part]. */
    data class Random(val part: RandomPart) : VerseSource

    /** The first passage of today's reading in the current reading plan. */
    data object ReadingPlan : VerseSource
}

/** Per-widget configuration of a Verse widget. */
data class VerseWidgetConfig(
    val source: VerseSource,
    val frequency: VerseChangeFrequency = VerseChangeFrequency.DAILY,
    /** Initials of the chosen Bible; null follows the active window's Bible. */
    val bibleInitials: String? = null,
    val showButtons: Boolean = true,
    val showSourceAndDate: Boolean = true,
    val seed: Long,
    val shuffleCount: Int = 0,
)

/** A verse or verse range in a Bible's versification (ordinals are in that versification). */
data class VerseRef(val bookInitials: String?, val v11nName: String, val startOrdinal: Int, val endOrdinal: Int)

/** What a Verse widget shows, or why it cannot show anything. */
sealed interface VerseWidgetContent {
    data class Verse(
        val ref: VerseRef,
        val reference: String,
        val text: String,
        val bibleAbbreviation: String,
        val sourceName: String,
        val periodLabel: String,
        /** `andBibleUrl(...)`: opens the app at this verse. */
        val link: String,
    ) : VerseWidgetContent

    data class Unavailable(val reason: Reason) : VerseWidgetContent {
        enum class Reason { NO_BIBLE, LABEL_MISSING, LABEL_EMPTY, NO_READING_PLAN }
    }
}

enum class SpeakPhase { SPEAKING, PAUSED, STOPPED }

/** One speak bookmark in the listening list. */
data class ListeningRow(
    val bookmarkId: String,
    val reference: String,
    val translation: String,
    val repeats: Boolean,
    /** Percent of the book; null for generic (non-Bible) bookmarks. */
    val percent: Int?,
    /** The row being read right now. */
    val current: Boolean,
)

/** Everything the Speak, Speak-with-text and Listening-list widgets draw. */
data class SpeakWidgetState(
    val phase: SpeakPhase,
    val title: String?,
    val reference: String?,
    val translation: String?,
    val percent: Int?,
    val verseText: String,
    val nextVerseText: String?,
    /** "Psalm 23:1 · 34 % · WEB" while speaking, "Continue from Psalm 23:1 · WEB" otherwise. */
    val statusLine: String,
    /** Whole minutes left on the sleep timer, rounded up; null when the timer is off. */
    val sleepMinutesLeft: Int?,
    val listening: List<ListeningRow>,
    val autoBookmarkOn: Boolean,
)
