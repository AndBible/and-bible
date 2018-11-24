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

package net.bible.android.view.activity.page.screen;

import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;

import javax.inject.Inject;

@ApplicationScope
public class WindowMenuCommandHandler {
	
	private final WindowControl windowControl;

	@Inject
	public WindowMenuCommandHandler(WindowControl windowControl) {
		this.windowControl = windowControl;
	}
	
	/** 
     * on Click handlers
     */
    public boolean handleMenuRequest(MenuItem menuItem) {
        boolean isHandled = false;
        
        // Handle item selection
        Window activeWindow = windowControl.getActiveWindow();
		switch (menuItem.getItemId()) {
		case R.id.windowNew:
			windowControl.addNewWindow();
			isHandled = true;
			break;
		case R.id.windowMaximise:
			if (activeWindow.isMaximised()) {
				windowControl.unmaximiseWindow(activeWindow);
				menuItem.setChecked(false);
			} else {
				windowControl.maximiseWindow(activeWindow);
				menuItem.setChecked(true);
			}
			isHandled = true;
			break;
		case R.id.windowMinimise:
			windowControl.minimiseCurrentWindow();
			isHandled = true;
			break;
		case R.id.windowClose:
			windowControl.closeCurrentWindow();
			isHandled = true;
			break;
		case R.id.windowMoveFirst:
			windowControl.moveCurrentWindowToFirst();
			isHandled = true;
			break;
		case R.id.windowSynchronise:
			if (activeWindow.isSynchronised()) {
				windowControl.unsynchroniseCurrentWindow();
				menuItem.setChecked(false);
			} else {
				windowControl.synchroniseCurrentWindow();
				menuItem.setChecked(true);
			}
			isHandled = true;
			break;
        }
        
        return isHandled;
	}
}
