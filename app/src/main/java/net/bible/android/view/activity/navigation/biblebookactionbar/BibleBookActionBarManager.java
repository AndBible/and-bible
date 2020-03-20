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

import android.app.Activity;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;
import android.view.View.OnClickListener;

import net.bible.android.control.ApplicationScope;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.actionbar.ActionBarManager;
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class BibleBookActionBarManager extends DefaultActionBarManager implements ActionBarManager {

	private final ScriptureToggleActionBarButton scriptureToggleActionBarButton;
	private final SortActionBarButton sortActionBarButton;

	@Inject
	public BibleBookActionBarManager(ScriptureToggleActionBarButton scriptureToggleActionBarButton, SortActionBarButton sortActionBarButton) {
		this.scriptureToggleActionBarButton = scriptureToggleActionBarButton;
		this.sortActionBarButton = sortActionBarButton;
	}
	
	public void registerScriptureToggleClickListener(OnClickListener scriptureToggleClickListener) {
		scriptureToggleActionBarButton.registerClickListener(scriptureToggleClickListener);
	}
	
	public void setScriptureShown(boolean isScripture) {
		scriptureToggleActionBarButton.setOn(isScripture);
	}
	
	public SortActionBarButton getSortButton() {
		return sortActionBarButton;
	}

	/* (non-Javadoc)
	 * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#prepareOptionsMenu(android.app.Activity, android.view.Menu, android.support.v7.app.ActionBar, net.bible.android.view.activity.page.MenuCommandHandler)
	 */
	@Override
	public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar) {
		super.prepareOptionsMenu(activity, menu, actionBar);
		
		scriptureToggleActionBarButton.addToMenu(menu);
		sortActionBarButton.addToMenu(menu);
	}
	
	/* (non-Javadoc)
	 * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#updateButtons()
	 */
	@Override
	public void updateButtons() {
		super.updateButtons();
		
		// this can be called on end of speech in non-ui thread
		CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				scriptureToggleActionBarButton.update();
				sortActionBarButton.update();
		    }
		});
	}
}
