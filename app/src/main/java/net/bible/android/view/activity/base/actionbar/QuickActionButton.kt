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

import net.bible.service.common.CommonUtils.getResourceInteger
import android.view.Menu
import android.view.MenuItem
import android.view.View
import net.bible.android.control.speak.SpeakControl
import net.bible.android.activity.R
import javax.inject.Inject
import java.lang.ref.WeakReference

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class QuickActionButton(private val showAsActionFlags: Int) : MenuItem.OnMenuItemClickListener
{
    private var menuItem: MenuItem? = null

    // weak to prevent ref from this (normally static) menu preventing gc of book selector
    private var weakOnClickListener: WeakReference<View.OnClickListener>? = null
    protected abstract val title: String?
    protected abstract val canShow: Boolean
    private val thisItemId = nextItemId++
    @Inject lateinit var speakControl: SpeakControl
    fun addToMenu(menu: Menu) {
        var _menuItem = menuItem
        if (_menuItem == null || menu.findItem(thisItemId) == null) {
            _menuItem = menu.add(Menu.NONE, thisItemId, Menu.NONE, "")
            _menuItem.setShowAsAction(showAsActionFlags)
            _menuItem.setOnMenuItemClickListener(this)
            update(_menuItem)
        }
    }

    fun update() {
        if (menuItem != null) {
            update(menuItem)
        }
    }

    protected open fun update(menuItem: MenuItem?) {
        // canShow means must show because we rely on AB logic
        menuItem!!.isVisible = canShow
        menuItem.title = title
        val iconResId = icon
        if (iconResId != icon) {
            menuItem.setIcon(iconResId)
        }
    }

    /**
     * Provide the possibility of handling clicks outside of the button e.g. in Activity
     */
    fun registerClickListener(onClickListener: View.OnClickListener) {
        weakOnClickListener = WeakReference(onClickListener)
    }

    /**
     * This is sometimes overridden but can be used to handle clicks in the Activity
     */
    override fun onMenuItemClick(item: MenuItem): Boolean {
        val onClickListener = weakOnClickListener!!.get()
        onClickListener?.onClick(null)
        update()
        return true
    }

    protected val isWide: Boolean
        get() = 4 < getResourceInteger(R.integer.number_of_quick_buttons)
    protected val isSpeakMode: Boolean
        get() = speakControl.isSpeaking || speakControl.isPaused

    open val icon: Int get() = 0
    companion object {
        var nextItemId = 100
    }
}
