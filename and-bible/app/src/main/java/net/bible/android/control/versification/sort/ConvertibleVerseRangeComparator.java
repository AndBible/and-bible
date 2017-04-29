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
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
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
