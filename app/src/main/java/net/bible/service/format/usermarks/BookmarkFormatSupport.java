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

package net.bible.service.format.usermarks;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.bookmark.BookmarkDBAdapter;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

/**
 * Support display of bookmarked verses.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class BookmarkFormatSupport {

	@Inject
	public BookmarkFormatSupport() {
	}

	public Map<Integer, Set<BookmarkStyle>> getVerseBookmarkStylesInPassage(Key passage) {
		// assumes the passage only covers one book, which always happens to be the case here
		Verse firstVerse = KeyUtil.getVerse(passage);
		BibleBook book = firstVerse.getBook();

		// get all Bookmarks in containing book to include variations due to differing versifications
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		List<BookmarkDto> bookmarkList;
		BookmarkStyle defaultBookmarkStyle = BookmarkStyle.valueOf(CommonUtils.INSTANCE.getSharedPreferences().getString(
				"default_bookmark_style_pref", BookmarkStyle.YELLOW_STAR.name()));
		Map<Integer, Set<BookmarkStyle>> bookmarkStylesByVerseNoInPassage = new HashMap<>();
		try {
			bookmarkList = db.getBookmarksInBook(book);

			// convert to required versification and check verse is in passage
			if (bookmarkList!=null) {
				Versification requiredVersification = firstVerse.getVersification();
				for (BookmarkDto bookmarkDto : bookmarkList) {
					VerseRange bookmarkVerseRange = bookmarkDto.getVerseRange(requiredVersification);
					if (passage.contains(bookmarkVerseRange.getStart())) {
						final List<LabelDto> bookmarkLabels = db.getBookmarkLabels(bookmarkDto);
						if(bookmarkLabels.isEmpty()) {
							bookmarkLabels.add(new LabelDto(null, null, defaultBookmarkStyle));
						}
						final List<BookmarkStyle> bookmarkStyles = getBookmarkStyles(bookmarkLabels, defaultBookmarkStyle);

						for (Verse verse : bookmarkVerseRange.toVerseArray()) {
							Set<BookmarkStyle> stylesSet = bookmarkStylesByVerseNoInPassage.get(verse.getVerse());
							if(stylesSet != null) {
								stylesSet.addAll(bookmarkStyles);
							}
							else {
								stylesSet = new TreeSet<>(bookmarkStyles);
								bookmarkStylesByVerseNoInPassage.put(verse.getVerse(), stylesSet);
							}

						}
					}
				}
			}

		} finally {}
		return bookmarkStylesByVerseNoInPassage;
	}

	/**
	 * Get distinct styles in enum order
	 */
	private List<BookmarkStyle> getBookmarkStyles(List<LabelDto> bookmarkLabels, BookmarkStyle defaultStyle) {
		Set<BookmarkStyle> bookmarkStyles = new TreeSet<>();
		for (LabelDto label : bookmarkLabels) {
			BookmarkStyle style = label.getBookmarkStyle();

			if (style!=null) {
				bookmarkStyles.add(style);
			}
			else {
				bookmarkStyles.add(defaultStyle);
			}
		}
		return new ArrayList<>(bookmarkStyles);
	}
}
