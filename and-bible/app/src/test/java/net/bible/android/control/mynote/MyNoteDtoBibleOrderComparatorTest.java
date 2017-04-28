package net.bible.android.control.mynote;

import net.bible.service.db.mynote.MyNoteDto;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import edu.emory.mathcs.backport.java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * This is almost identical to the similar BookmarkDtoBibleOrderComparatorTest
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class MyNoteDtoBibleOrderComparatorTest {
	private final Versification kjv = Versifications.instance().getVersification("KJV");
	private final Versification nrsv = Versifications.instance().getVersification("NRSV");
	private final Versification lxx = Versifications.instance().getVersification("LXX");
	private final Versification segond = Versifications.instance().getVersification("Segond");
	private final Versification synodal = Versifications.instance().getVersification("Synodal");

	@Test
	public void compare() throws Exception {
		final MyNoteDto kjvPs17_2 = getMyNote(kjv, BibleBook.PS, 17, 2);
		final MyNoteDto nrsvPs17_3 = getMyNote(nrsv, BibleBook.PS, 17, 3);
		final MyNoteDto lxxPs17_4 = getMyNote(lxx, BibleBook.PS, 17, 4);
		final MyNoteDto synodalPs17_5 = getMyNote(synodal, BibleBook.PS, 17, 5);
		final MyNoteDto kjvPs17_6 = getMyNote(kjv, BibleBook.PS, 17, 6);
		final MyNoteDto kjvPs17_7 = getMyNote(nrsv, BibleBook.PS, 17, 7);
		final MyNoteDto segondPs17_8 = getMyNote(segond, BibleBook.PS, 17, 8);
		final List<MyNoteDto> myNotes = Arrays.asList(
				kjvPs17_2,
				nrsvPs17_3,
				lxxPs17_4,
				synodalPs17_5,
				kjvPs17_6,
				kjvPs17_7,
				segondPs17_8
		);

		Collections.sort(myNotes, new MyNoteDtoBibleOrderComparator(myNotes));

		assertThat(myNotes, contains(kjvPs17_2, nrsvPs17_3, lxxPs17_4, kjvPs17_6, kjvPs17_7, segondPs17_8, synodalPs17_5));
	}

	private MyNoteDto getMyNote(Versification v11n, BibleBook book, int chapter, int verse) {
		MyNoteDto newMyNoteDto = new MyNoteDto();
		final VerseRange verseRange = new VerseRange(v11n, new Verse(v11n, book, chapter, verse), new Verse(v11n, book, chapter, verse));
		newMyNoteDto.setVerseRange(verseRange);
		newMyNoteDto.setNoteText("Some test note text");

		return newMyNoteDto;
	}

}