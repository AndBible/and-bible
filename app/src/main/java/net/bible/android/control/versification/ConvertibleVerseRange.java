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

package net.bible.android.control.versification;

import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.Versification;

/**
 * Allow a VerseRange to be easily converted to any other.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ConvertibleVerseRange {

	private VerseRange originalVerseRange;

	private ConvertibleVerse startVerse;
	private ConvertibleVerse endVerse;

	public ConvertibleVerseRange(VerseRange verseRange) {
		this.originalVerseRange = verseRange;

		this.startVerse = new ConvertibleVerse(verseRange.getStart());
		this.endVerse = new ConvertibleVerse(verseRange.getEnd());
	}

	public VerseRange getVerseRange() {
		return originalVerseRange;
	}

	public VerseRange getVerseRange(Versification v11n) {
		return new VerseRange(v11n, startVerse.getVerse(v11n), endVerse.getVerse(v11n));
	}

	public boolean isConvertibleTo(Versification v11n) {
		return startVerse.isConvertibleTo(v11n) && endVerse.isConvertibleTo(v11n);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ConvertibleVerseRange that = (ConvertibleVerseRange) o;

		return originalVerseRange.equals(that.getVerseRange(originalVerseRange.getVersification()));
	}

	@Override
	public String toString() {
		return "ConvertibleVerseRange{" +
				"originalVerseRange=" + originalVerseRange +
				'}';
	}

	@Override
	public int hashCode() {
		return originalVerseRange.hashCode();
	}

	public Versification getOriginalVersification() {
		return originalVerseRange.getVersification();
	}
}
