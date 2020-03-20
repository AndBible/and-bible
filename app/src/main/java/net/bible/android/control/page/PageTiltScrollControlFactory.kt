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
import net.bible.android.control.page.window.Window
import java.util.*
import javax.inject.Inject

/**
 * Retain the link between a window and its associated PageTiltScrollControl.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class PageTiltScrollControlFactory @Inject constructor() {
    private val windowPageTiltScrollControlMap: MutableMap<Window, PageTiltScrollControl> = HashMap()
    fun getPageTiltScrollControl(window: Window): PageTiltScrollControl {
        return windowPageTiltScrollControlMap[window] ?: synchronized(windowPageTiltScrollControlMap) {
			synchronized(windowPageTiltScrollControlMap) {
				windowPageTiltScrollControlMap[window] ?: PageTiltScrollControl()
			}.also {
				windowPageTiltScrollControlMap[window] = it
			}
		}
    }
}
