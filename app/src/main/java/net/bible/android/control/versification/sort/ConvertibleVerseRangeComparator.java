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

package net.bible.android.control.versification.sort;

import net.bible.android.control.versification.ConvertibleVerseRange;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.mynote.MyNoteDto;

import org.crosswire.jsword.versification.Versification;

import java.util.Comparator;
import java.util.List;

/**
 * Comparator for ConvertibleVerseRanges.
 * Compares them in a consistent order according to which v11n is most used and compatible.
 * Ensures the same v11n is chosen when comparing v1, v2 and v2, v1 so that the order is consistent.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

public class ConvertibleVerseRangeComparator implements Comparator<ConvertibleVerseRangeUser> {

	private final CompatibleVersificationChooser compatibleVersificationChooser;

	private ConvertibleVerseRangeComparator(CompatibleVersificationChooser compatibleVersificationChooser) {
		this.compatibleVersificationChooser = compatibleVersificationChooser;
	}

	@Override
	public int compare(ConvertibleVerseRangeUser a, ConvertibleVerseRangeUser b) {
		if (a == null) {
			if (b == null) {
				return 0;
			}
			return -1;
		} else if (b == null) {
			return 1;
		}

		final ConvertibleVerseRange aCvr = a.getConvertibleVerseRange();
		final ConvertibleVerseRange bCvr = b.getConvertibleVerseRange();

		if (aCvr == null) {
			if (bCvr == null) {
				return 0;
			}
			return -1;
		} else if (bCvr == null) {
			return 1;
		}

		// must compare in the same (but opposite) order when comparing b,a and a,b so cannot just use a.v11n()
		final Versification v11n = compatibleVersificationChooser.findPreferredCompatibleVersification(aCvr, bCvr);

		return aCvr.getVerseRange(v11n).compareTo(bCvr.getVerseRange(v11n));
	}

	public static class Builder {
		private List<Versification> prioritisedVersifications;

		public Builder withBookmarks(List<BookmarkDto> bookmarks) {
			prioritisedVersifications = new VersificationPrioritiser(bookmarks).getPrioritisedVersifications();
			return this;
		}

		public Builder withMyNotes(List<MyNoteDto> myNoteDtos) {
			prioritisedVersifications = new VersificationPrioritiser(myNoteDtos).getPrioritisedVersifications();
			return this;
		}

		public ConvertibleVerseRangeComparator build() {
			final CompatibleVersificationChooser compatibleVersificationChooser = new CompatibleVersificationChooser(prioritisedVersifications);
			return new ConvertibleVerseRangeComparator(compatibleVersificationChooser);
		}
	}
}
