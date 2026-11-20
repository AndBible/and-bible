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
package net.bible.sharedui.strings.keyed

import net.bible.android.control.widget.VerseSource
import net.bible.android.control.widget.VerseWidgetContent
import net.bible.android.control.widget.WidgetStrings

/** [WidgetStrings] over a [StringTable]; mirrors `AndroidWidgetStrings` (same `strings.xml` keys). */
class KeyedWidgetStrings(private val table: () -> StringTable) : WidgetStrings {
    constructor(table: StringTable) : this({ table })

    private val t: StringTable get() = table()

    override val appName: String get() = t.text("app_name_medium")
    override val speakName: String get() = t.text("widget_speak_name")
    override val speakTextName: String get() = t.text("widget_speak_text_name")
    override val listeningListName: String get() = t.text("widget_listening_list_name")
    override val verseName: String get() = t.text("widget_verse_name")
    override fun continueFrom(reference: String, translation: String) = t.fmt("widget_continue_from", reference, translation)
    override val continueListening: String get() = t.text("widget_continue_listening")
    override val paused: String get() = t.text("widget_paused")
    override fun statusLine(reference: String, percent: Int, translation: String) = t.fmt("widget_status_line", reference, percent, translation)
    override val listeningListEmptyTitle: String get() = t.text("widget_listening_list_empty_title")
    override val listeningListEmptyText: String get() = t.text("widget_listening_list_empty_text")
    override val openAppToLoad: String get() = t.text("widget_open_app_to_load")
    override val toggleSleepTimer: String get() = t.text("widget_toggle_sleep_timer")
    override fun sleepMinutes(minutes: Int) = t.fmt("widget_sleep_minutes", minutes)
    override fun verseUnavailable(reason: VerseWidgetContent.Unavailable.Reason) = t.text(
        when (reason) {
            VerseWidgetContent.Unavailable.Reason.NO_BIBLE -> "widget_verse_unavailable_no_bible"
            VerseWidgetContent.Unavailable.Reason.LABEL_MISSING -> "widget_verse_unavailable_label_missing"
            VerseWidgetContent.Unavailable.Reason.LABEL_EMPTY -> "widget_verse_unavailable_label_empty"
            VerseWidgetContent.Unavailable.Reason.NO_READING_PLAN -> "widget_verse_unavailable_no_reading_plan"
        }
    )
    override fun randomSourceName(part: VerseSource.RandomPart) = t.text(
        when (part) {
            VerseSource.RandomPart.WHOLE_BIBLE -> "widget_verse_source_random_whole_bible"
            VerseSource.RandomPart.NEW_TESTAMENT -> "widget_verse_source_random_new_testament"
            VerseSource.RandomPart.PSALMS_PROVERBS -> "widget_verse_source_random_psalms_proverbs"
        }
    )
    override val readingPlanSourceName: String get() = t.text("widget_verse_source_reading_plan")
}
