/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.page.window;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.WindowLayout.WindowState;

import org.crosswire.jsword.book.Book;

/**
 * Window used when user selects a link
 */
public class LinksWindow extends Window {

	// must be -ve so as not to interfere with incrementing window number sequence
	protected static final int DEDICATED_LINK_WINDOW_SCREEN_NO = -999;

	public LinksWindow(WindowState windowState, CurrentPageManager currentPageManager) {
		super(DEDICATED_LINK_WINDOW_SCREEN_NO, windowState, currentPageManager);
		setSynchronised(false);
		currentPageManager.setWindow(this);
	}

	@Override
	public boolean isLinksWindow() {
		return true;
	}

	/**
	 * Page state should reflect active window when links window is being used after being closed.  
	 * Not enough to select default bible because another module type may be selected in link.
	 */
	protected void initialisePageStateIfClosed(Window activeWindow) {
		// set links window state from active window if it was closed 
		if (getWindowLayout().getState().equals(WindowState.CLOSED) && !activeWindow.isLinksWindow()) {
			// initialise links window documents from active window
			getPageManager().restoreState(activeWindow.getPageManager().getStateJson());
		}
	}
}
