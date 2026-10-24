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
package net.bible.android.control

import androidx.annotation.VisibleForTesting
import net.bible.android.control.page.window.Window
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events

/** when a bible passage is changed there are lots o things to update and they should be done in a helpful order
 * This helps to control screen updates after a passage change
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object PassageChangeMediator {
    private var source = EventSource<PageChange>()

    /** Page changes of every window; see [PageChange]. */
    val changes: Events<PageChange> get() = source

    /** the document has changed so ask the view to refresh itself */
    fun onCurrentPageChanged(window: Window) {
        window.updateText()
        source.emit(PageChange.VerseChanged(window))
    }

    /** this is triggered on scroll */
    fun onCurrentVerseChanged(window: Window) {
        source.emit(PageChange.VerseChanged(window))
    }

    fun onBibleVerseSelected() {
        source.emit(PageChange.BibleVerseChanged)
    }

    /** finished fetching html so should hide hourglass */
    fun contentChangeFinished() {
        source.emit(PageChange.ContentLoaded)
    }

    @VisibleForTesting
    fun resetSubscribersForTest() { source = EventSource() }
}
