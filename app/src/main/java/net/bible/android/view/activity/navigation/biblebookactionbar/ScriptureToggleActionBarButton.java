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

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.navigation.NavigationControl;
import net.bible.android.view.activity.base.actionbar.ToggleActionBarButton;
import net.bible.service.common.CommonUtils;

import javax.inject.Inject;

/** Toggle between 66 Bible books and deuterocanonical books
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class ScriptureToggleActionBarButton extends ToggleActionBarButton {

	private final NavigationControl navigationControl;

	@Inject
	public ScriptureToggleActionBarButton(NavigationControl navigationControl) {
		super(R.drawable.ic_action_new, R.drawable.ic_action_undo);

		this.navigationControl = navigationControl;
	}

	@Override
	protected String getTitle() {
		if (isOn()) {
			return CommonUtils.INSTANCE.getResourceString(R.string.deuterocanonical);
		} else {
			return CommonUtils.INSTANCE.getResourceString(R.string.bible);
		}
	}

	@Override
	protected boolean canShow() {
		return navigationControl.getBibleBooks(false).size()>0;
	}
}
