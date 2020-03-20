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
import net.bible.service.db.mynote.MyNoteDBAdapter;
import net.bible.service.db.mynote.MyNoteDto;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Support display of verses with notes.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class MyNoteFormatSupport {

	@Inject
	public MyNoteFormatSupport() {
	}

	public List<Verse> getVersesWithNotesInPassage(Key passage) {
		// assumes the passage only covers one book, which always happens to be the case here
		Verse firstVerse = KeyUtil.getVerse(passage);
		BibleBook book = firstVerse.getBook();

		MyNoteDBAdapter db = new MyNoteDBAdapter();
		List<MyNoteDto> myNoteList = null;
		try {
			myNoteList = db.getMyNotesInBook(book);
		} finally {}

		// convert to required versification and check verse is in passage
		List<Verse> versesInPassage = new ArrayList<>();
		if (myNoteList!=null) {
			boolean isVerseRange = passage instanceof VerseRange;
			Versification requiredVersification = firstVerse.getVersification();
			for (MyNoteDto myNoteDto : myNoteList) {
				VerseRange verseRange = myNoteDto.getVerseRange(requiredVersification);
				//TODO should not require VerseRange cast but bug in JSword
				if (isVerseRange) {
					if (((VerseRange)passage).contains(verseRange.getStart())) {
						versesInPassage.add(verseRange.getStart());
					}
				} else {
					if (passage.contains(verseRange)) {
						versesInPassage.add(verseRange.getStart());
					}
				}
			}
		}

		return versesInPassage;
	}
}
