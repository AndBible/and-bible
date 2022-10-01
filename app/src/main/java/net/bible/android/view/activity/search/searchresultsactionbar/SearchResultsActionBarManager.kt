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
package net.bible.android.view.activity.search.searchresultsactionbar

import net.bible.android.control.ApplicationScope
import javax.inject.Inject
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager
import net.bible.android.view.activity.base.actionbar.ActionBarManager
import android.app.Activity
import net.bible.android.view.activity.base.CurrentActivityHolder
import android.view.Menu
import android.view.View
import androidx.appcompat.app.ActionBar

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class SearchResultsActionBarManager @Inject constructor(private val scriptureToggleActionBarButton: ScriptureToggleActionBarButton) :
    DefaultActionBarManager(), ActionBarManager {
    fun registerScriptureToggleClickListener(scriptureToggleClickListener: View.OnClickListener?) {
        scriptureToggleActionBarButton.registerClickListener(scriptureToggleClickListener)
    }

    fun setScriptureShown(isScripture: Boolean) {
        scriptureToggleActionBarButton.isOn = isScripture
    }

    /* (non-Javadoc)
	 * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#prepareOptionsMenu(android.app.Activity, android.view.Menu, android.support.v7.app.ActionBar, net.bible.android.view.activity.page.MenuCommandHandler)
	 */
    override fun prepareOptionsMenu(activity: Activity, menu: Menu, actionBar: ActionBar) {
        super.prepareOptionsMenu(activity, menu, actionBar)
        scriptureToggleActionBarButton.addToMenu(menu)
    }

    /* (non-Javadoc)
	 * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#updateButtons()
	 */
    override fun updateButtons() {
        super.updateButtons()

        // this can be called on end of speech in non-ui thread
        CurrentActivityHolder.getInstance()
            .runOnUiThread { scriptureToggleActionBarButton.update() }
    }
}
