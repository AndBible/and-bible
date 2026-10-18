/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.page

import net.bible.sharedcore.bibleview.BibleBridge
import net.bible.service.sword.SwordDocumentFacade

/**
 * The Android adapter of the JS bridge: every [BibleBridge] call is answered by the platform-free
 * `ReadingBridge` of [bibleView] (`BibleBridge by ...`); `BibleJavascriptInterfaceIsAdapterTest`
 * keeps it that way. What stays here is Android-only: the `androidBridge` endpoint's
 * [getActiveLanguages] and the [notificationsEnabled] flag that [BibleView] toggles around the
 * action mode.
 */
class BibleJavascriptInterface(
    bibleView: BibleView
) : BibleBridge by bibleView.readingBridge {
    var notificationsEnabled = false

    /** The language codes of the installed Bibles as a JSON list, for the `androidBridge` endpoint. */
    fun getActiveLanguages(): String {
        val languages = SwordDocumentFacade.bibles.map { "\"" + it.bookMetaData.language.code + "\""}
        return "[" + languages.distinct().joinToString(",") + "]"
    }
}
