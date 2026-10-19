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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.session

import net.bible.sharedui.reading.host.ReadingHostState

/**
 * Platform-free pieces of a reading window's session assembly that the iOS host (and any later host) shares:
 * the document offsets, the active-languages list, the verse chooser and the asker guard of the chooser results.
 */

/**
 * The WebView's document offsets in CSS px (= dp on iOS), the counterpart of Android's `ReadingInsets` ledger
 * (`BibleView.topOffset` / `bottomOffset`).
 *
 * [top] is 0: the toolbar is an in-flow row above the panes and pads the status bar itself, so no pane extends under it
 * (Android's `topOffset2` is a constant 0 for the same reason). [bottom] is what overlays the panes' lower edge: the
 * system bottom inset (home indicator) unless the speak bar is up (it sits below the panes and pads that inset
 * itself, `bottomOffsetForWebView`'s F111 rule), the agent log's collapsed height and the search sheet's height.
 *
 * Every setter fires [onChanged] only when its value really changed, so a benign recomposition does not spam `set_offsets`.
 */
@kotlin.native.HiddenFromObjC
class ReadingOffsets(private val onChanged: () -> Unit = {}) {
    private var systemBottomInset = 0f
    private var searchSheet = 0f
    private var agentLog = 0f
    private var speakBarVisible = false

    val top: Float get() = 0f

    val bottom: Float get() = (if (speakBarVisible) 0f else systemBottomInset) + agentLog + searchSheet

    fun setSystemBottomInset(dp: Float) = change { if (systemBottomInset != dp) { systemBottomInset = dp; true } else false }

    fun setSearchSheet(visible: Boolean, heightPx: Int, density: Float) {
        val value = if (visible && density > 0f) heightPx / density else 0f
        change { if (searchSheet != value) { searchSheet = value; true } else false }
    }

    fun setAgentLog(visible: Boolean, heightDp: Float) {
        val value = if (visible) heightDp else 0f
        change { if (agentLog != value) { agentLog = value; true } else false }
    }

    fun setSpeakBarVisible(visible: Boolean) = change { if (speakBarVisible != visible) { speakBarVisible = visible; true } else false }

    private inline fun change(apply: () -> Boolean) {
        if (apply()) onChanged()
    }
}

/** Android's `BibleJavascriptInterface.getActiveLanguages`: the distinct language [codes] as a JSON list. */
@kotlin.native.HiddenFromObjC
fun activeLanguagesJson(codes: List<String>): String =
    codes.distinct().joinToString(",", "[", "]") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }

/**
 * [BibleWindowSession.verseChooser] over the hosted Grid sheet (Android: `openVerseChooserSheetForResult().await()`): the
 * chosen verse's OSIS id, or null when the sheet was dismissed or superseded.
 */
@kotlin.native.HiddenFromObjC
fun readingVerseChooser(hostState: ReadingHostState): suspend () -> String? = { hostState.openVerseChooserSheetForResult().await() }

/**
 * Guards a chooser's asker -> target mapping: an asker whose window is no longer [isOpen] (closed while the chooser was
 * up) yields null, so the answer is dropped instead of being applied to a dead page manager.
 */
@kotlin.native.HiddenFromObjC
fun <T : Any> guardedChooserTarget(isOpen: (asker: Any?) -> Boolean, build: (asker: Any?) -> T?): (asker: Any?) -> T? =
    { asker -> if (asker != null && isOpen(asker)) build(asker) else null }
