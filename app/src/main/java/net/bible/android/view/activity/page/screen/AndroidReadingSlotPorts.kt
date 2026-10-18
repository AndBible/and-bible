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
package net.bible.android.view.activity.page.screen

import android.content.Context
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.bible.android.view.activity.page.ReadingHostActivity
import net.bible.sharedui.reading.host.slots.ReadingQuickActions
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import net.bible.android.platform.openSystemTtsSettings
import net.bible.sharedui.reading.host.slots.SpeakSettingsPlatform

/**
 * Android [SpeakSettingsPlatform]: the system TTS settings screen, or the general settings where
 * that screen is missing (the same code [net.bible.android.platform.AndroidSpeakPlatform] uses).
 * [context] is read per call because the host's activity can be recreated.
 */
class AndroidSpeakSettingsPlatform(private val context: () -> Context) : SpeakSettingsPlatform {
    override fun openSystemTtsSettings() = openSystemTtsSettings(context())
}

/**
 * Android [ReadingQuickActions]: each member is the body the matching full-screen chooser result
 * runs, through the hosting activity's `ReadingCommands`. [activity] is read per call because the
 * host's activity can be recreated.
 */
class AndroidReadingQuickActions(private val activity: () -> ReadingHostActivity) : ReadingQuickActions {
    /**
     * The quick sheet never pauses the activity, so nothing else flushes the outgoing workspace's
     * windows/page-managers/history before switching: use the save-then-switch entry point, never
     * plain `switchToWorkspace`.
     */
    override fun quickSwitchToWorkspace(id: String) {
        val host = activity()
        host.lifecycleScope.launch { host.readingCommands.quickSwitchToWorkspace(id) }
    }

    override fun applyChosenDocument(bookStr: String?) = activity().readingCommands.applyChosenDocument(bookStr)

    override fun applyChosenGenBookKey(book: Book?, key: Key) =
        activity().readingCommands.applyChosenGenBookKey(book, key)
}
