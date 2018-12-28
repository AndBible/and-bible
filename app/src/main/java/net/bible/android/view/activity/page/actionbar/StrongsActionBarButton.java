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

package net.bible.android.view.activity.page.actionbar;

import androidx.core.view.MenuItemCompat;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.actionbar.QuickActionButton;
import net.bible.service.common.CommonUtils;

import javax.inject.Inject;

/** 
 * Toggle Strongs numbers on/off
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class StrongsActionBarButton extends QuickActionButton {

	private final DocumentControl documentControl;

	private final PageControl pageControl;

	@Inject
	public StrongsActionBarButton(DocumentControl documentControl, PageControl pageControl) {
		// SHOW_AS_ACTION_ALWAYS is overriden by setVisible which depends on canShow() below
		// because when visible this button is ALWAYS on the Actionbar
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS|MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
		this.documentControl = documentControl;
		this.pageControl = pageControl;
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem arg0) {
		// update the show-strongs pref setting according to the ToggleButton
		CommonUtils.getSharedPreferences().edit().putBoolean("show_strongs_pref", !isStrongsVisible()).commit();
		// redisplay the current page; this will also trigger update of all menu items
		PassageChangeMediator.getInstance().forcePageUpdate();
		
		return true;
	}

	private boolean isStrongsVisible() {
		return pageControl.isStrongsShown();
	}

	@Override
	protected String getTitle() {
		return CommonUtils.getResourceString(isStrongsVisible() ? R.string.strongs_toggle_button_on : R.string.strongs_toggle_button_off);
	}

	@Override
	protected int getIcon() {
		return R.drawable.ic_code_white_24dp;
	}

	/**
	 * return true if Strongs are relevant to this doc & screen
	 * Don't show with speak button on narrow screen to prevent over-crowding 
	 */
	@Override
	protected boolean canShow() {
		return  documentControl.isStrongsInBook() &&
				(isWide() || !isSpeakMode());
	}
}
