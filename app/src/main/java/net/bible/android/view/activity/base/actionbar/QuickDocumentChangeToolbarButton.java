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

package net.bible.android.view.activity.base.actionbar;

import androidx.core.view.MenuItemCompat;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.TitleSplitter;

import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract public class QuickDocumentChangeToolbarButton extends QuickActionButton implements OnMenuItemClickListener {

	private ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private Book mSuggestedDocument;

	protected abstract Book getSuggestedDocument();
	
	private TitleSplitter titleSplitter = new TitleSplitter();
	
	private static int ACTION_BUTTON_MAX_CHARS = CommonUtils.INSTANCE.getResourceInteger(R.integer.action_button_max_chars);
	
	/**
	 * SHOW_AS_ACTION_ALWAYS is overriden by setVisible which depends on canShow() below
	 */
	public QuickDocumentChangeToolbarButton() {
		this(MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
	}
	
	public QuickDocumentChangeToolbarButton(int showAsActionFlags) {
		super(showAsActionFlags);
	}

	@Override
	public void update(MenuItem menuItem) {
        mSuggestedDocument = getSuggestedDocument();
        super.update(menuItem);
	}

	@Override
	public boolean onMenuItemClick(MenuItem arg0) {
    	getCurrentPageManager().setCurrentDocument(mSuggestedDocument);
    	return true;
	}

	@Override
	protected boolean canShow() {
		return mSuggestedDocument!=null;
	}
	
	@Override
	protected String getTitle() {
		if (mSuggestedDocument!=null) {
			return titleSplitter.shorten(mSuggestedDocument.getAbbreviation(), ACTION_BUTTON_MAX_CHARS);
		} else {
			return "";
		}
	}

	protected CurrentPageManager getCurrentPageManager() {
		return activeWindowPageManagerProvider.getActiveWindowPageManager();
	}

	@Inject
	void setActiveWindowPageManagerProvider(ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}
}
