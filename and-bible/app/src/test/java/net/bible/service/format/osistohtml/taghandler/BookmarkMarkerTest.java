package net.bible.service.format.osistohtml.taghandler;

import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler;
import net.bible.test.PassageTestData;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsNot.not;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class BookmarkMarkerTest {


	private OsisToHtmlParameters osisToHtmlParameters;
	private OsisToHtmlSaxHandler.VerseInfo verseInfo;
	private BookmarkMarker bookmarkMarker;

	private static final int CURRENT_VERSE = 2;

	@Before
	public void setup() {
		osisToHtmlParameters = new OsisToHtmlParameters();
		verseInfo = new OsisToHtmlSaxHandler.VerseInfo();
		osisToHtmlParameters.setShowBookmarks(true);
		verseInfo.currentVerseNo = CURRENT_VERSE;
		osisToHtmlParameters.setVersesWithBookmarks(Arrays.asList(PassageTestData.PS_139_2));
		osisToHtmlParameters.setDefaultBookmarkStyle(BookmarkStyle.GREEN_HIGHLIGHT);
		bookmarkMarker = new BookmarkMarker(osisToHtmlParameters, verseInfo);
	}

	@Test
	public void testGetBookmarkClasses() throws Exception {
		List<String> bookmarkClasses = bookmarkMarker.getBookmarkClasses();
		assertThat(bookmarkClasses, contains("GREEN_HIGHLIGHT"));

		this.verseInfo.currentVerseNo = 3;
		bookmarkClasses = bookmarkMarker.getBookmarkClasses();
		assertThat(bookmarkClasses, not(contains("GREEN_HIGHLIGHT")));
	}
}