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

package net.bible.service.readingplan;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.OsisParser;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.versification.Versification;

import android.util.Log;

/**
 * Get a Key from either a simple reference or an OSIS reference
 */
public class PassageReader {

	private Versification v11n;
	
	private OsisParser osisParser = new OsisParser();
	
	private static final String TAG = "PassageReader";
	
	PassageReader(Versification v11n) {
		this.v11n = v11n;
	}

	/**
	 * Return a Key representing the passage passed in or an empty passage if it can't be parsed.
	 * @param passage Textual ref
	 * @return
	 */
	public Key getKey(String passage) {
		Key key = null;
		try {
			// spaces confuse the osis parser
			passage = passage.trim();
			
			// If expecting OSIS then use OSIS parser
			key = osisParser.parseOsisRef(v11n, passage);
			
			// OSIS parser is strict so try treating as normal ref if osis parser fails
			if (key==null) {
				Log.d(TAG, "Non OSIS Reading plan passage:"+passage);
				key = PassageKeyFactory.instance().getKey(v11n, passage);
			}
		} catch (Exception e) {
			Log.e(TAG, "Invalid passage reference in reading plan:"+passage);
		}
		
		// If all else fails return an empty passage to prevent NPE
		if (key==null) {
			key = PassageKeyFactory.instance().createEmptyKeyList(v11n);
		}
		return key;
	}
}
