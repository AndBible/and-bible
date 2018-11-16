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
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
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
			db.open();
			myNoteList = db.getMyNotesInBook(book);
		} finally {
			db.close();
		}

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
