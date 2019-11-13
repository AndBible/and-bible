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

package net.bible.android.control.page;

import android.os.AsyncTask;
import android.util.Log;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.control.page.window.Window;
import net.bible.service.format.HtmlMessageFormatter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract public class UpdateTextTask extends AsyncTask<Window, Integer, String> {

	private Window window;
	private float yScreenOffsetRatio = SharedConstants.NO_VALUE;
	
	private static final String TAG = "UpdateTextTask";
	
    /** callbacks from base class when result is ready */
	abstract protected void showText(String text, Window screenToUpdate, float yOffsetRatio);
	
	@Override
	protected void onPreExecute() {
		//NOOP
	}
	
	@Override
    protected String doInBackground(Window... windows) {
        Log.d(TAG, "Loading html in background");
    	String text;
    	try {
    		window = windows[0];
    		CurrentPage currentPage = window.getPageManager().getCurrentPage(); 
    		Book document = currentPage.getCurrentDocument();
    		// if bible show whole chapter
    		Key key = currentPage.getKey();
    		// but allow for jump to specific verse e.g. after search result
    		if (!(currentPage instanceof CurrentBiblePage)) {
    			yScreenOffsetRatio = currentPage.getCurrentYOffsetRatio();
    		}

            Log.d(TAG, "Loading document:" + document + " key:" + key);
            
            text = currentPage.getCurrentPageContent();
            
    	} catch (OutOfMemoryError oom) {
    		Log.e(TAG, "Out of memory error", oom);
    		System.gc();
    		text = HtmlMessageFormatter.format(R.string.error_page_too_large);
    	}
    	return text;
    }

    protected void onPostExecute(String htmlFromDoInBackground) {
        Log.d(TAG, "Got html length "+htmlFromDoInBackground.length());
        showText(htmlFromDoInBackground, window, yScreenOffsetRatio);
    }
}
