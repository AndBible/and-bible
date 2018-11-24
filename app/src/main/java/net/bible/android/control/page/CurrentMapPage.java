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
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKey;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

/** Reference to current Map shown by viewer
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class CurrentMapPage extends CachedKeyPage implements CurrentPage {
	
	private Key key;

	@SuppressWarnings("unused")
	private static final String TAG = "CurrentMapPage";
	
	/* default */ CurrentMapPage(SwordContentFacade swordContentFacade, SwordDocumentFacade swordDocumentFacade) {
		super(false, swordContentFacade, swordDocumentFacade);
	}
	
	public BookCategory getBookCategory() {
		return BookCategory.MAPS;
	}

	@Override
	public Class<? extends Activity> getKeyChooserActivity() {
		return ChooseMapKey.class;
	}
	
	/** set key without notification
	 * 
	 * @param key
	 */
	public void doSetKey(Key key) {
		this.key = key;
	}

	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
	@Override
	public Key getKey() {
		return key;
    }

	@Override
	public void next() {
		Key next = getKeyPlus(1);
		if (next!=null) {
			setKey(next);
		}
	}

	@Override
	public void previous() {
		Key prev = getKeyPlus(-1);
		if (prev!=null) {
			setKey(prev);
		}
	}

	@Override
	public void updateOptionsMenu(Menu menu) {
		super.updateOptionsMenu(menu);

		MenuItem menuItem = menu.findItem(R.id.bookmarksButton);
		if (menuItem!=null) {
			menuItem.setEnabled(false);
		}

	}
	
	@Override
	public boolean isSingleKey() {
		return true;
	}

	/** can we enable the main menu search button 
	 */
	@Override
	public boolean isSearchable() {
		return false;
	}
}
