package net.bible.service.format.osistohtml.taghandler;

import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class BookmarkMarkerTest {


	private OsisToHtmlParameters osisToHtmlParameters;
	private OsisToHtmlSaxHandler.VerseInfo verseInfo;
	private BookmarkMarker bookmarkMarker;
	Map<Integer, List<BookmarkStyle>> bookmarkStylesByBookmarkedVerse;

	@Before
	public void setup() {
		osisToHtmlParameters = new OsisToHtmlParameters();
		verseInfo = new OsisToHtmlSaxHandler.VerseInfo();
		osisToHtmlParameters.setShowBookmarks(true);

		bookmarkStylesByBookmarkedVerse = new HashMap<>();
		osisToHtmlParameters.setBookmarkStylesByBookmarkedVerse(bookmarkStylesByBookmarkedVerse);
		osisToHtmlParameters.setDefaultBookmarkStyle(BookmarkStyle.GREEN_HIGHLIGHT);
		bookmarkMarker = new BookmarkMarker(osisToHtmlParameters, verseInfo);
	}

	@Test
	public void testGetDefaultBookmarkClass() throws Exception {
		bookmarkStylesByBookmarkedVerse.put(2, null);
		this.verseInfo.currentVerseNo = 2;
		List<String> bookmarkClasses = bookmarkMarker.getBookmarkClasses();
		assertThat(bookmarkClasses, contains("GREEN_HIGHLIGHT"));
	}

	@Test
	public void testGetCustomBookmarkClass() throws Exception {
		bookmarkStylesByBookmarkedVerse.put(3, Collections.singletonList(BookmarkStyle.RED_HIGHLIGHT));
		this.verseInfo.currentVerseNo = 3;
		List<String> bookmarkClasses = bookmarkMarker.getBookmarkClasses();
		assertThat(bookmarkClasses, contains("RED_HIGHLIGHT"));
	}

	@Test
	public void testGetNoBookmarkClass() throws Exception {
		bookmarkStylesByBookmarkedVerse.put(3, Collections.singletonList(BookmarkStyle.RED_HIGHLIGHT));
		this.verseInfo.currentVerseNo = 4;
		List<String> bookmarkClasses = bookmarkMarker.getBookmarkClasses();
		assertThat(bookmarkClasses.size(), equalTo(0));
	}
}