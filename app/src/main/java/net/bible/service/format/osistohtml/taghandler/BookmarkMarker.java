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

package net.bible.service.format.osistohtml.taghandler;

import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import java.util.*;

/** Display an img if the current verse has MyNote
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BookmarkMarker {

	private Map<Integer, Set<BookmarkStyle>> bookmarkStylesByBookmarkedVerse = new HashMap<>();
	
	private OsisToHtmlParameters parameters;
	
	private VerseInfo verseInfo;

	public BookmarkMarker(OsisToHtmlParameters parameters, VerseInfo verseInfo) {
		this.parameters = parameters;
		this.verseInfo = verseInfo;

		// create hashset of verses to optimise verse note lookup
		bookmarkStylesByBookmarkedVerse.clear();
		if (parameters.getBookmarkStylesByBookmarkedVerse()!=null) {
			bookmarkStylesByBookmarkedVerse = parameters.getBookmarkStylesByBookmarkedVerse();
		}
	}
	
	/** Get any bookmark classes for current verse
	 */
	public List<String> getBookmarkClasses() {
		if (bookmarkStylesByBookmarkedVerse !=null && parameters.isShowBookmarks()) {
			if (bookmarkStylesByBookmarkedVerse.containsKey(verseInfo.currentVerseNo)) {
				final Set<BookmarkStyle> bookmarkStyles = bookmarkStylesByBookmarkedVerse.get(verseInfo.currentVerseNo);
				return getStyleNames(bookmarkStyles);
			}
		}
		return Collections.emptyList();
	}

	private List<String> getStyleNames(Set<BookmarkStyle> bookmarkStyles) {
		List<String> styleNames = new ArrayList<>();
		for (BookmarkStyle bookmarkStyle : bookmarkStyles) {
			styleNames.add(bookmarkStyle.name());
		}
		return styleNames;
	}
}
