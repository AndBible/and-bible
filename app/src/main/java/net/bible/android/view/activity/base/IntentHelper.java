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

package net.bible.android.view.activity.base;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import net.bible.android.BibleApplication;
import net.bible.android.control.page.CurrentBiblePage;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.passage.VerseRangeFactory;
import org.crosswire.jsword.versification.Versification;

/**
 * Save and fetch a verse range from/to intent extras and othr intent fucntionality
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class IntentHelper {

	// request codes passed to and returned from sub-activities
	public static final int REFRESH_DISPLAY_ON_FINISH = 2;

	public static final int UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH = 3;

	private static final String VERSE_RANGE = "net.bible.android.view.activity.comparetranslations.VerseRange";

	private static final String TAG = "IntentHelper";

	public VerseRange getIntentVerseRangeOrDefault(Intent intent) {
		//fetch verse from intent if set
		CurrentBiblePage currentDoc = BibleApplication.Companion.getApplication().getApplicationComponent().activeWindowPageManagerProvider().getActiveWindowPageManager().getCurrentBible();
		Versification currentV11n = currentDoc.getCurrentPassageBook().getVersification();

		try {
			Bundle extras = intent.getExtras();
			if (extras != null && extras.containsKey(VERSE_RANGE)) {
				return VerseRangeFactory.fromString(currentV11n, extras.getString(VERSE_RANGE));
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting Verse Range from intent, using default");
		}

		// if we got this far there was no verse in the intent
		final Verse defaultVerse = currentDoc.getSingleKey();
		return new VerseRange(currentV11n, defaultVerse);
	}

	public Intent updateIntentWithVerseRange(Intent intent, VerseRange verseRange) {
		intent.putExtra(VERSE_RANGE, verseRange.getOsisRef());

		return intent;
	}
}
