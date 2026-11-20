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

/**
 * The localized texts the widgets show, resolved at call time so an in-app language change shows on the next
 * redraw. Android reads `strings.xml`, iOS the keyed string tables; the iOS widget extension gets [asMap] in the
 * snapshot instead of string tables of its own.
 */
interface WidgetStrings {
    val appName: String
    val speakName: String
    val speakTextName: String
    val listeningListName: String
    val verseName: String

    /** "Continue from Psalm 23:1 · WEB". */
    fun continueFrom(reference: String, translation: String): String
    val continueListening: String
    val paused: String

    /** "Psalm 23:1 · 34 % · WEB". */
    fun statusLine(reference: String, percent: Int, translation: String): String
    val listeningListEmptyTitle: String
    val listeningListEmptyText: String
    val openAppToLoad: String
    val toggleSleepTimer: String
    fun sleepMinutes(minutes: Int): String

    /** Explanatory text for a Verse widget that cannot show a verse. */
    fun verseUnavailable(reason: VerseWidgetContent.Unavailable.Reason): String
    fun randomSourceName(part: VerseSource.RandomPart): String
    val readingPlanSourceName: String
}

/** The keys of [WidgetStrings.asMap], in the order the iOS extension looks them up. */
object WidgetStringKeys {
    const val APP_NAME = "app_name"
    const val SPEAK_NAME = "widget_speak_name"
    const val SPEAK_TEXT_NAME = "widget_speak_text_name"
    const val LISTENING_LIST_NAME = "widget_listening_list_name"
    const val VERSE_NAME = "widget_verse_name"
    const val CONTINUE_LISTENING = "widget_continue_listening"
    const val PAUSED = "widget_paused"
    const val LISTENING_LIST_EMPTY_TITLE = "widget_listening_list_empty_title"
    const val LISTENING_LIST_EMPTY_TEXT = "widget_listening_list_empty_text"
    const val OPEN_APP_TO_LOAD = "widget_open_app_to_load"
    const val TOGGLE_SLEEP_TIMER = "widget_toggle_sleep_timer"
    const val READING_PLAN_SOURCE = "widget_verse_source_reading_plan"
    const val UNAVAILABLE_PREFIX = "widget_verse_unavailable_"
    const val RANDOM_PREFIX = "widget_verse_source_random_"

    /** Every key [asMap] writes. */
    val all: List<String> by lazy {
        listOf(
            APP_NAME, SPEAK_NAME, SPEAK_TEXT_NAME, LISTENING_LIST_NAME, VERSE_NAME, CONTINUE_LISTENING, PAUSED,
            LISTENING_LIST_EMPTY_TITLE, LISTENING_LIST_EMPTY_TEXT, OPEN_APP_TO_LOAD, TOGGLE_SLEEP_TIMER,
            READING_PLAN_SOURCE,
        ) + VerseWidgetContent.Unavailable.Reason.entries.map { UNAVAILABLE_PREFIX + it.name.lowercase() } +
            VerseSource.RandomPart.entries.map { RANDOM_PREFIX + it.name.lowercase() }
    }
}

/** All fixed (non-parameterised) texts by key, for the iOS snapshot. */
fun WidgetStrings.asMap(): Map<String, String> = buildMap {
    put(WidgetStringKeys.APP_NAME, appName)
    put(WidgetStringKeys.SPEAK_NAME, speakName)
    put(WidgetStringKeys.SPEAK_TEXT_NAME, speakTextName)
    put(WidgetStringKeys.LISTENING_LIST_NAME, listeningListName)
    put(WidgetStringKeys.VERSE_NAME, verseName)
    put(WidgetStringKeys.CONTINUE_LISTENING, continueListening)
    put(WidgetStringKeys.PAUSED, paused)
    put(WidgetStringKeys.LISTENING_LIST_EMPTY_TITLE, listeningListEmptyTitle)
    put(WidgetStringKeys.LISTENING_LIST_EMPTY_TEXT, listeningListEmptyText)
    put(WidgetStringKeys.OPEN_APP_TO_LOAD, openAppToLoad)
    put(WidgetStringKeys.TOGGLE_SLEEP_TIMER, toggleSleepTimer)
    put(WidgetStringKeys.READING_PLAN_SOURCE, readingPlanSourceName)
    VerseWidgetContent.Unavailable.Reason.entries.forEach {
        put(WidgetStringKeys.UNAVAILABLE_PREFIX + it.name.lowercase(), verseUnavailable(it))
    }
    VerseSource.RandomPart.entries.forEach {
        put(WidgetStringKeys.RANDOM_PREFIX + it.name.lowercase(), randomSourceName(it))
    }
}
