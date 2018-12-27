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

package net.bible.android.view.activity.base.actionbar;

import android.app.Activity;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.TitleSplitter;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/** 
 * Show current verse/key and document on left of actionBar
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public abstract class Title {

	private ActionBar actionBar;
	private Activity activity;
	
	private TextView documentTitle;
	private TextView documentSubtitle;
	private TextView pageTitle;
	private TextView pageSubtitle;
	
	private static final TitleSplitter titleSplitter = new TitleSplitter();

	abstract protected String[] getDocumentTitleParts();
	abstract protected String[] getPageTitleParts();
	abstract protected void onDocumentTitleClick();
	abstract protected void onPageTitleClick();
	
	private static final String TAG = "Title";
	
	public void addToBar(ActionBar actionBar, final Activity activity) {
		this.actionBar = actionBar;
		this.activity = activity;

		actionBar.setCustomView(R.layout.title);

	    documentTitle = actionBar.getCustomView().findViewById(R.id.documentTitle);
	    documentSubtitle = actionBar.getCustomView().findViewById(R.id.documentSubtitle);
	    pageTitle = actionBar.getCustomView().findViewById(R.id.pageTitle);
	    pageSubtitle = actionBar.getCustomView().findViewById(R.id.pageSubtitle);

	    // clicking document title shows document selector
	    ViewGroup documentTitleContainer = actionBar.getCustomView().findViewById(R.id.documentTitleContainer);
	    documentTitleContainer.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onDocumentTitleClick();
			}
	    });

	    // clicking page title shows appropriate key selector
	    ViewGroup pageTitleContainer = actionBar.getCustomView().findViewById(R.id.pageTitleContainer);
	    pageTitleContainer.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onPageTitleClick();
			}
	    });

	    actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME);

	    update(true);
		
		// do not display the app icon in the actionbar
		actionBar.setDisplayShowHomeEnabled(false);

		// remove a small amount of extra padding at the left of the actionbar see: http://stackoverflow.com/questions/27354812/android-remove-left-margin-from-actionbars-custom-layout
		ViewParent toolbar = actionBar.getCustomView().getParent();
		if (toolbar instanceof Toolbar) {
			((Toolbar) toolbar).setContentInsetsAbsolute(0, 0);
		}
	}
	
	public void update() {
		// update everything if called externally
		update(true);
	}
	
	protected void update(final boolean everything) {
		CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (actionBar!=null) {
					// always update verse number
					String[] pageParts = getTwoPageTitleParts();
					if (pageParts.length>0) pageTitle.setText(pageParts[0]);
					if (pageParts.length>1) pageSubtitle.setText(pageParts[1]);
					pageSubtitle.setVisibility(pageParts.length>1? View.VISIBLE : View.GONE);
					
					// don't always need to redisplay document name
					if (everything) {
						String[] documentParts = getTwoDocumentTitleParts();
						if (documentParts.length>0) documentTitle.setText(documentParts[0]);
						if (documentParts.length>1) documentSubtitle.setText(documentParts[1]);
						documentSubtitle.setVisibility(documentParts.length>1? View.VISIBLE : View.GONE);
					}
				}
			}
		});
	}

	protected Activity getActivity() {
		return activity;
	}
	
	private String[] getTwoPageTitleParts() {
		try {
			return unsplitIfLandscape(getPageTitleParts());
		} catch (Exception e) {
			Log.e(TAG,  "Error getting reading plan title", e);
			return new String[] {"", ""};
		}
	}

	private String[] getTwoDocumentTitleParts() {
		try {
			return unsplitIfLandscape(getDocumentTitleParts());
		} catch (Exception e) {
			Log.e(TAG,  "Error getting reading plan title", e);
			return new String[] {"", ""};
		}

	}

	protected String[] getTwoTitleParts(String title, boolean lastAreMoreSignificant) {
		String[] parts = titleSplitter.split(title);
		parts = reduceTo2Parts(parts, lastAreMoreSignificant);
		return parts;
	}
	
	private String[] reduceTo2Parts(String[] parts, boolean lastAreMoreSignificant) {
		// return the last 2 parts as only show 2 and last are normally most significant
		if (lastAreMoreSignificant) {
			parts = ArrayUtils.subarray(parts, parts.length-2, parts.length);
		} else {
			parts = ArrayUtils.subarray(parts, 0, 2);
		}
		return parts;
	}
	
	private String[] unsplitIfLandscape(String[] parts) {
		// un-split if in landscape because landscape actionBar has more width but less height
		if (!CommonUtils.isPortrait()) {
			parts = new String[] { StringUtils.join(parts, " ") };
		}
		return parts;
	}

}
