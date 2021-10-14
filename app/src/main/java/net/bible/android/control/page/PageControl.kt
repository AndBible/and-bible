/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.control.page

import net.bible.android.control.ApplicationScope
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import org.crosswire.jsword.passage.Verse
import javax.inject.Inject

/**
 * SesionFacade for CurrentPage used by View classes
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class PageControl @Inject constructor(
	private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
) {

    open val currentBibleVerse: Verse
        get() = currentPageManager.currentBible.singleKey

    val currentPageManager: CurrentPageManager
        get() = activeWindowPageManagerProvider.activeWindowPageManager

    companion object {
        private const val TAG = "PageControl"
    }

}
