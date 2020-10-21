package net.bible.android.control.bookmark;


import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark;

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
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class BookmarkDtoBibleOrderComparatorTest {

	private final Versification kjv = Versifications.instance().getVersification("KJV");
	private final Versification nrsv = Versifications.instance().getVersification("NRSV");
	private final Versification lxx = Versifications.instance().getVersification("LXX");
	private final Versification segond = Versifications.instance().getVersification("Segond");
	private final Versification synodal = Versifications.instance().getVersification("Synodal");

	@Test
	public void compare() throws Exception {
		final Bookmark kjvPs17_2 = getBookmark(kjv, BibleBook.PS, 17, 2);
		final Bookmark nrsvPs17_3 = getBookmark(nrsv, BibleBook.PS, 17, 3);
		final Bookmark lxxPs17_4 = getBookmark(lxx, BibleBook.PS, 17, 4);
		final Bookmark synodalPs17_5 = getBookmark(synodal, BibleBook.PS, 17, 5);
		final Bookmark kjvPs17_6 = getBookmark(kjv, BibleBook.PS, 17, 6);
		final Bookmark kjvPs17_7 = getBookmark(nrsv, BibleBook.PS, 17, 7);
		final Bookmark segondPs17_8 = getBookmark(segond, BibleBook.PS, 17, 8);
		final List<Bookmark> bookmarks = Arrays.asList(
				kjvPs17_2,
				nrsvPs17_3,
				lxxPs17_4,
				synodalPs17_5,
				kjvPs17_6,
				kjvPs17_7,
				segondPs17_8
		);

		Collections.sort(bookmarks, new BookmarkBibleOrderComparator(bookmarks));

		assertThat(bookmarks, contains(kjvPs17_2, nrsvPs17_3, lxxPs17_4, kjvPs17_6, kjvPs17_7, segondPs17_8, synodalPs17_5));
	}

	private Bookmark getBookmark(Versification v11n, BibleBook book, int chapter, int verse) {
		final VerseRange verseRange = new VerseRange(v11n, new Verse(v11n, book, chapter, verse), new Verse(v11n, book, chapter, verse));
		return new Bookmark(verseRange);
	}
}
