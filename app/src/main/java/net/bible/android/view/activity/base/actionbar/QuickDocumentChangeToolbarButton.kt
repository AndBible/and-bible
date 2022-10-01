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
import android.view.MenuItem
import net.bible.android.activity.R
import javax.inject.Inject
import androidx.core.view.MenuItemCompat
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.service.common.TitleSplitter
import org.crosswire.jsword.book.Book

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */

/**
 * SHOW_AS_ACTION_ALWAYS is overriden by setVisible which depends on canShow() below
 */

abstract class QuickDocumentChangeToolbarButton:
    QuickActionButton(MenuItemCompat.SHOW_AS_ACTION_ALWAYS or MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT),
    MenuItem.OnMenuItemClickListener
{
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    private var mSuggestedDocument: Book? = null

    abstract fun getSuggestedDocument(): Book?

    private val titleSplitter = TitleSplitter()
    public override fun update(menuItem: MenuItem?) {
        mSuggestedDocument = getSuggestedDocument()
        super.update(menuItem)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        activeWindowPageManagerProvider.activeWindowPageManager.setCurrentDocument(mSuggestedDocument)
        return true
    }

    override val canShow get(): Boolean = mSuggestedDocument != null

    override val title: String?
        get() = if (mSuggestedDocument != null) {
            titleSplitter.shorten(
                mSuggestedDocument!!.abbreviation,
                ACTION_BUTTON_MAX_CHARS
            )
        } else {
            ""
        }

    companion object {
        private val ACTION_BUTTON_MAX_CHARS = getResourceInteger(R.integer.action_button_max_chars)
    }
}
