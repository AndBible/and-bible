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
package net.bible.android.platform

import android.content.Context
import net.bible.android.control.widget.VerseSource
import net.bible.android.control.widget.VerseWidgetContent
import net.bible.android.control.widget.WidgetStrings
import net.bible.android.activity.R

/** [WidgetStrings] from `strings.xml`, resolved per call so an in-app language change shows on the next redraw. */
class AndroidWidgetStrings(private val context: Context) : WidgetStrings {
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    override val appName get() = s(R.string.app_name_medium)
    override val speakName get() = s(R.string.widget_speak_name)
    override val speakTextName get() = s(R.string.widget_speak_text_name)
    override val listeningListName get() = s(R.string.widget_listening_list_name)
    override val verseName get() = s(R.string.widget_verse_name)
    override fun continueFrom(reference: String, translation: String) = s(R.string.widget_continue_from, reference, translation)
    override val continueListening get() = s(R.string.widget_continue_listening)
    override val paused get() = s(R.string.widget_paused)
    override fun statusLine(reference: String, percent: Int, translation: String) = s(R.string.widget_status_line, reference, percent, translation)
    override val listeningListEmptyTitle get() = s(R.string.widget_listening_list_empty_title)
    override val listeningListEmptyText get() = s(R.string.widget_listening_list_empty_text)
    override val openAppToLoad get() = s(R.string.widget_open_app_to_load)
    override val toggleSleepTimer get() = s(R.string.widget_toggle_sleep_timer)
    override fun sleepMinutes(minutes: Int) = s(R.string.speak_sleep_timer_minutes, minutes)
    override fun verseUnavailable(reason: VerseWidgetContent.Unavailable.Reason) = s(
        when (reason) {
            VerseWidgetContent.Unavailable.Reason.NO_BIBLE -> R.string.widget_verse_unavailable_no_bible
            VerseWidgetContent.Unavailable.Reason.LABEL_MISSING -> R.string.widget_verse_unavailable_label_missing
            VerseWidgetContent.Unavailable.Reason.LABEL_EMPTY -> R.string.widget_verse_unavailable_label_empty
            VerseWidgetContent.Unavailable.Reason.NO_READING_PLAN -> R.string.widget_verse_unavailable_no_reading_plan
        }
    )
    override fun randomSourceName(part: VerseSource.RandomPart) = s(
        when (part) {
            VerseSource.RandomPart.WHOLE_BIBLE -> R.string.widget_verse_source_random_whole_bible
            VerseSource.RandomPart.NEW_TESTAMENT -> R.string.widget_verse_source_random_new_testament
            VerseSource.RandomPart.PSALMS_PROVERBS -> R.string.widget_verse_source_random_psalms_proverbs
        }
    )
    override val readingPlanSourceName get() = s(R.string.widget_verse_source_reading_plan)
}
