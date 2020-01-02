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

package net.bible.android.view.activity.navigation.biblebookactionbar;

import androidx.core.view.MenuItemCompat;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.navigation.NavigationControl;
import net.bible.android.view.activity.base.actionbar.QuickActionButton;

import javax.inject.Inject;

/** 
 * Toggle sort of Bible books by name or canonically
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class SortActionBarButton extends QuickActionButton {

	private final NavigationControl navigationControl;

	@Inject
	public SortActionBarButton(NavigationControl navigationControl) {
		// SHOW_AS_ACTION_ALWAYS is overriden by setVisible which depends on canShow() below
		// because when visible this button is ALWAYS on the Actionbar
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

		this.navigationControl = navigationControl;
	}

	
	@Override
	protected int getIcon() {
		return android.R.drawable.ic_menu_sort_by_size;
	}


	@Override
	protected String getTitle() {
		return navigationControl.getBibleBookSortOrderButtonDescription();
	}

	/** 
	 * return true if Strongs are relevant to this doc & screen
	 * Don't show with speak button on narrow screen to prevent over-crowding 
	 */
	@Override
	protected boolean canShow() {
		return  true;
	}
}
