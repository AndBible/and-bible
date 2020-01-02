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

/** Two state actionbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public abstract class ToggleActionBarButton extends QuickActionButton {

	private boolean isOn = true;
	private int onIcon;
	private int offIcon;
	
	public ToggleActionBarButton(int onIcon, int offIcon) {
		// overridden by canShow
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		
		this.onIcon = onIcon;
		this.offIcon = offIcon;
	}

	/** 
	 * Show a different icon to represent switch to 'Appendix' or back to scripture
	 */
	@Override
	protected int getIcon() {
		return isOn() ? onIcon : offIcon;
	}
	
	public boolean isOn() {
		return isOn;
	}

	public void setOn(boolean isOn) {
		this.isOn = isOn;
	}
}
