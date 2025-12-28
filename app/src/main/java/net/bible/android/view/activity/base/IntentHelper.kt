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
package net.bible.android.view.activity.base

/**
 * Save and fetch a verse range from/to intent extras and othr intent fucntionality
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object IntentHelper {
    // request codes passed to and returned from sub-activities
    const val REFRESH_DISPLAY_ON_FINISH = 2
    const val UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH = 3
}
