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

package net.bible.android.view.activity.base

import android.content.res.Configuration
import android.view.Menu

import net.bible.android.control.page.PageControl
import net.bible.android.view.activity.base.actionbar.ActionBarManager
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager

import javax.inject.Inject


/**
 * Base class for activities with a custom title bar
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class CustomTitlebarActivityBase(private val optionsMenuId: Int = NO_OPTIONS_MENU) : ActivityBase() {

    private var actionBarManager: ActionBarManager = DefaultActionBarManager()

    @Inject lateinit var pageControl: PageControl

    /**
     * load the default menu items from xml config
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (optionsMenuId != NO_OPTIONS_MENU) {
            // Inflate the menu
            menuInflater.inflate(optionsMenuId, menu)
        }

        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Allow some menu items to be hidden or otherwise altered
     */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        actionBarManager.prepareOptionsMenu(this, menu, supportActionBar)

        // must return true for menu to be displayed
        return true
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // the title bar has different widths depending on the orientation
        updateActions()
    }

    /** update the quick links in the title bar
     */
    open fun updateActions() {
        actionBarManager.updateButtons()
    }

    protected fun setActionBarManager(actionBarManager: ActionBarManager) {
        this.actionBarManager = actionBarManager
    }

    companion object {

        protected const val NO_OPTIONS_MENU = 0

        private const val TAG = "CTActivityBase"
    }
}
