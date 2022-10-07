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
package net.bible.android.view.activity.base.actionbar

import net.bible.android.view.util.UiUtils.setActionBarColor
import android.app.Activity
import android.view.Menu
import androidx.appcompat.app.ActionBar

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class DefaultActionBarManager : ActionBarManager {
    private var actionBar: ActionBar? = null
    override fun prepareOptionsMenu(activity: Activity, menu: Menu, actionBar: ActionBar) {
        this.actionBar = actionBar
        setActionBarColor(actionBar)
    }

    override fun updateButtons() {
        setActionBarColor(actionBar)
    }
}
