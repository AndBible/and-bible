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

package net.bible.android.view.activity.base;

import android.content.res.Configuration;
import android.util.Log;
import android.view.Menu;

import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.actionbar.ActionBarManager;
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager;

import javax.inject.Inject;

/**
 * Base class for activities with a custom title bar
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public abstract class CustomTitlebarActivityBase extends ActivityBase {
	
	private ActionBarManager actionBarManager = new DefaultActionBarManager();

	private int optionsMenuId;

	protected static final int NO_OPTIONS_MENU = 0;

	private PageControl pageControl;

	private static final String TAG = "CustomTitlebrActvtyBase";

	public CustomTitlebarActivityBase() {
		this(NO_OPTIONS_MENU);
	}

	public CustomTitlebarActivityBase(int optionsMenuId) {
		this.optionsMenuId = optionsMenuId;
	}
	
	/** custom title bar code to add the FEATURE_CUSTOM_TITLE just before setContentView
	 * and set the new titlebar layout just after
	 */
    @Override
	public void setContentView(int layoutResID) {
		super.setContentView(layoutResID);
    }
    
    /** 
     * load the default menu items from xml config 
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	if (optionsMenuId!=NO_OPTIONS_MENU) {
	    	// Inflate the menu
	        getMenuInflater().inflate(optionsMenuId, menu);
    	}

		return super.onCreateOptionsMenu(menu);
    }

    /**
     * Allow some menu items to be hidden or otherwise altered
     */
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
        actionBarManager.prepareOptionsMenu(this, menu, getSupportActionBar());
		
		// must return true for menu to be displayed
		return true;
	}

    /** 
     * Hide/show the actionbar and call base class to hide/show everything else
     */
	public void toggleFullScreen() {
    	super.toggleFullScreen();
    	
    	if (!isFullScreen()) {
    		Log.d(TAG, "Fullscreen off");
    		getSupportActionBar().show();
    	} else {
    		Log.d(TAG, "Fullscreen on");
    		getSupportActionBar().hide();
    	}

    	getContentView().requestLayout();
    }

	/**
	 *  Called whenever something like strong preferences have been changed by the user.  Should refresh the screen
	 */
	protected void preferenceSettingsChanged() {
	}

    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		// the title bar has different widths depending on the orientation
		updateActionBarButtons();
	}

	/** update the quick links in the title bar
     */
    public void updateActionBarButtons() {
        actionBarManager.updateButtons();
    }

	protected void setActionBarManager(ActionBarManager actionBarManager) {
		this.actionBarManager = actionBarManager;
	}

	public PageControl getPageControl() {
		return pageControl;
	}

	@Inject
	public void setPageControl(PageControl pageControl) {
		this.pageControl = pageControl;
	}
}
