package net.bible.android.control.versification;

import android.support.annotation.NonNull;

import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.Versification;

/**
 * Allow a VerseRange to be easily converted to any other.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class ConvertibleVerseRange implements Comparable<ConvertibleVerseRange> {

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

	@Override
	public int compareTo(@NonNull ConvertibleVerseRange other) {
		if (this.originalVerseRange == null) {
			return other.originalVerseRange == null ? 0 : -1;
		}
		if (other.originalVerseRange == null){
			return 1;
		}
		final VerseRange otherInThisV11n = other.getVerseRange(originalVerseRange.getVersification());
		if (otherInThisV11n.getStart().getOrdinal()>0) {
			return originalVerseRange.compareTo(otherInThisV11n);
		}
		final VerseRange thisInOtherV11n = getVerseRange(other.originalVerseRange.getVersification());
		if (thisInOtherV11n.getStart().getOrdinal()>0) {
			return thisInOtherV11n.compareTo(other.originalVerseRange);
		}

		// TODO cannot compare the verses!!!
		return 0;
	}

	public Versification getOriginalVersification() {
		return originalVerseRange.getVersification();
	}
}
