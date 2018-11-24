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

package net.bible.android.view.activity.navigation;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import net.bible.android.control.navigation.NavigationControl;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.util.buttongrid.ButtonGrid;
import net.bible.android.view.util.buttongrid.ButtonGrid.ButtonInfo;
import net.bible.android.view.util.buttongrid.OnButtonGridActionListener;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Choose a chapter to view
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class GridChoosePassageChapter extends CustomTitlebarActivityBase implements OnButtonGridActionListener {

	private BibleBook mBibleBook=BibleBook.GEN;

	private NavigationControl navigationControl;

	private ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final String TAG = "GridChoosePassageChaptr";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	// background goes white in some circumstances if theme changes so prevent theme change
    	setAllowThemeChange(false);
        super.onCreate(savedInstanceState);

		buildActivityComponent().inject(this);
		int bibleBookNo = getIntent().getIntExtra(GridChoosePassageBook.BOOK_NO, navigationControl.getDefaultBibleBookNo());
		//TODO av11n - this is done now
		mBibleBook = BibleBook.values()[bibleBookNo];

		// show chosen book in page title to confirm user choice
		try {
			//TODO av11n - probably should use same v11n as used in GridChoosePassageBook
			setTitle(navigationControl.getVersification().getLongName(mBibleBook));
		} catch (Exception nsve) {
			Log.e(TAG, "Error in selected book no", nsve);
		}

		ButtonGrid grid = new ButtonGrid(this);
		grid.setOnButtonGridActionListener(this);

		grid.addButtons(getBibleChaptersButtonInfo(mBibleBook));
		setContentView(grid);
	}

	private List<ButtonInfo> getBibleChaptersButtonInfo(BibleBook book) {
		int chapters;
		Verse currentVerse = KeyUtil.getVerse(activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentBible().getKey());
		BibleBook currentBibleBook = currentVerse.getBook();
		int currentBibleChapter = currentVerse.getChapter();

		try {
			chapters = navigationControl.getVersification().getLastChapter(book);
		} catch (Exception nsve) {
			chapters = -1;
		}

		List<ButtonInfo> keys = new ArrayList<>();
		for (int i=1; i<=chapters; i++) {
			ButtonInfo buttonInfo = new ButtonInfo();
			// this is used for preview
			buttonInfo.id = i;
			buttonInfo.name = Integer.toString(i);
			if (currentBibleBook == book && i == currentBibleChapter) {
				buttonInfo.textColor = Color.YELLOW;
				buttonInfo.highlight = true;
			}
			keys.add(buttonInfo);
		}
		return keys;
	}

	@Override
	public void buttonPressed(ButtonInfo buttonInfo) {
		int chapter = buttonInfo.id;
		Log.d(TAG, "Chapter selected:"+chapter);
		try {
			CurrentPageManager currentPageControl = activeWindowPageManagerProvider.getActiveWindowPageManager();
			if (!navigateToVerse() && !currentPageControl.getCurrentPage().isSingleKey()) {
				currentPageControl.getCurrentPage().setKey(new Verse(navigationControl.getVersification(), mBibleBook, chapter, 1));
				onSave(null);
			} else {
    			// select verse
	        	Intent myIntent = new Intent(this, GridChoosePassageVerse.class);
	        	myIntent.putExtra(GridChoosePassageBook.BOOK_NO, mBibleBook.ordinal());
	        	myIntent.putExtra(GridChoosePassageBook.CHAPTER_NO, chapter);
	        	startActivityForResult(myIntent, chapter);
			}
		} catch (Exception e) {
			Log.e(TAG, "error on select of bible book", e);
		}
	}
	
	static boolean navigateToVerse() {
		return CommonUtils.getSharedPreferences().getBoolean("navigate_to_verse_pref", false);
	}

    public void onSave(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent(this, GridChoosePassageBook.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }

    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (resultCode==Activity.RESULT_OK) {
    		returnToPreviousScreen();
    	}
    }

	@Inject
	void setNavigationControl(NavigationControl navigationControl) {
		this.navigationControl = navigationControl;
	}

	@Inject
	void setActiveWindowPageManagerProvider(ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}
}
