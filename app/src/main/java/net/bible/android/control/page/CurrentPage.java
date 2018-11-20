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

import android.app.Activity;
import android.view.Menu;

import net.bible.service.common.ParseException;
import net.bible.service.format.Note;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public interface CurrentPage {

	String toString();

	BookCategory getBookCategory();

	Class<? extends Activity> getKeyChooserActivity();

	void next();

	void previous();
	
	/** get incremented key according to the type of page displayed - verse, chapter, ...
	 */
	Key getKeyPlus(int num);

	/** add or subtract a number of pages from the current position and return Page
	 */
	Key getPagePlus(int num);

	/** set key without updating screens */
	void doSetKey(Key key);

	/** set key and update screens */
	void setKey(Key key);

	boolean isSingleKey();
	
	// bible and commentary share a key (verse)
	boolean isShareKeyBetweenDocs();

	/** get current key
	 */
	Key getKey();
	
	/** get key for 1 verse instead of whole chapter if bible
	 */
	Key getSingleKey();
	
	Book getCurrentDocument();

	void setCurrentDocument(Book currentBible);

	void setCurrentDocumentAndKey(Book doc, Key key);
	
	boolean checkCurrentDocumentStillInstalled();

	/** get a page to display */
	String getCurrentPageContent();

	/** get footnotes */
	List<Note> getCurrentPageFootnotesAndReferences() throws ParseException;

	void updateOptionsMenu(Menu menu);

	void restoreState(JSONObject state) throws JSONException;

	JSONObject getStateJson() throws JSONException;

	void setInhibitChangeNotifications(boolean inhibitChangeNotifications);

	boolean isInhibitChangeNotifications();

	boolean isSearchable();
	boolean isSpeakable();
	
	//screen offset as a percentage of total height of screen
	float getCurrentYOffsetRatio();
	void setCurrentYOffsetRatio(float currentYOffsetRatio);

}
