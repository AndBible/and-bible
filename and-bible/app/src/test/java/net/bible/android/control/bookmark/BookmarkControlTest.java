package net.bible.android.control.bookmark;

import net.bible.android.activity.BuildConfig;
import net.bible.android.common.resource.AndroidResourceProvider;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

import org.crosswire.jsword.passage.NoSuchVerseException;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.passage.VerseRangeFactory;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class)
public class BookmarkControlTest {

	// keep changing the test verse
	private static final String TEST_VERSE_START = "Psalms 119:";
	private static final Versification KJV_VERSIFICATION = Versifications.instance().getVersification("KJV");
	private int testVerseCounter;
	private String currentTestVerse;
	
	// keep changing the test label
	private static final String TEST_LABEL_START = "Test label ";
	private int testLabelCounter;
	private String currentTestLabel;

	private BookmarkControl bookmarkControl;
	
    @Before
    public void setUp() throws Exception {
		bookmarkControl = new BookmarkControl(new AndroidResourceProvider());
	}

	@After
	public void tearDown() throws Exception {

		List<BookmarkDto> bookmarks = bookmarkControl.getAllBookmarks();
		for (BookmarkDto dto : bookmarks) {
			bookmarkControl.deleteBookmark(dto);
		}

		List<LabelDto> labels = bookmarkControl.getAllLabels();
		for (LabelDto dto : labels) {
			bookmarkControl.deleteLabel(dto);
		}
		
		bookmarkControl = null;
	}

	@Test
	public void testAddBookmark() {
		try {
			BookmarkDto newDto = addTestVerse();
			assertEquals("New Bookmark key incorrect.  Test:"+currentTestVerse+" was:"+newDto.getVerseRange().getName(), newDto.getVerseRange().getName(), currentTestVerse);
			assertTrue("New Bookmark id incorrect", newDto.getId()>-1);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception:"+e.getMessage());
		}
	}

	@Test
	public void testGetAllBookmarks() {
		try {
			addTestVerse();
			addTestVerse();
			addTestVerse();
			
			List<BookmarkDto> bookmarks = bookmarkControl.getAllBookmarks();
			assertTrue( bookmarks.size()==3);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception:"+e.getMessage());
		}
	}


	@Test
	public void testDeleteBookmark() {
		addTestVerse();
		List<BookmarkDto> bookmarks = bookmarkControl.getAllBookmarks();
		BookmarkDto toDelete = bookmarks.get(0);
		bookmarkControl.deleteBookmark(toDelete);
		
		bookmarks = bookmarkControl.getAllBookmarks();
		for (BookmarkDto bookmark : bookmarks) {
			assertFalse("delete failed", bookmark.getId().equals(toDelete.getId()));
		}
		
	}

	@Test
	public void testAddLabel() {
		try {
			LabelDto newDto = addTestLabel();
			assertEquals("New Label name incorrect.  Test:"+currentTestLabel+" was:"+newDto.getName(), newDto.getName(), currentTestLabel);
			assertTrue("New Label id incorrect", newDto.getId()>-1);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception:"+e.getMessage());
		}
	}

	@Test
	public void testSetBookmarkLabels() {
		BookmarkDto bookmark = addTestVerse();
		LabelDto label1 = addTestLabel();
		LabelDto label2 = addTestLabel();
		List<LabelDto> labelList = new ArrayList<>();
		labelList.add(label1);
		labelList.add(label2);

		// add 2 labels and check they are saved
		bookmarkControl.setBookmarkLabels(bookmark, labelList);
		
		List<BookmarkDto> list1 = bookmarkControl.getBookmarksWithLabel(label1);
		assertEquals(1, list1.size());
		assertEquals(bookmark, list1.get(0));

		List<BookmarkDto> list2 = bookmarkControl.getBookmarksWithLabel(label2);
		assertEquals(1, list2.size());
		assertEquals(bookmark, list2.get(0));

		// check 1 label is deleted if it is not linked
		List<LabelDto> labelList2 = new ArrayList<>();
		labelList2.add(label1);
		bookmarkControl.setBookmarkLabels(bookmark, labelList2);
	
		List<BookmarkDto> list3 = bookmarkControl.getBookmarksWithLabel(label1);
		assertEquals(1, list3.size());
		List<BookmarkDto> list4 = bookmarkControl.getBookmarksWithLabel(label2);
		assertEquals(0, list4.size());
	}

	@Test
	public void testGetBookmarksWithLabel() {
		BookmarkDto bookmark = addTestVerse();
		LabelDto label1 = addTestLabel();
		List<LabelDto> labelList = new ArrayList<>();
		labelList.add(label1);
		
		// add 2 labels and check they are saved
		bookmarkControl.setBookmarkLabels(bookmark, labelList);

		List<BookmarkDto> list1 = bookmarkControl.getBookmarksWithLabel(label1);
		assertEquals(1, list1.size());
		assertEquals(bookmark, list1.get(0));
	}

	@Test
	public void testVerseRange() {
		BookmarkDto newBookmarkDto = new BookmarkDto();
		final VerseRange verseRange = new VerseRange(KJV_VERSIFICATION, new Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 2), new Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 5));
		newBookmarkDto.setVerseRange(verseRange);

		BookmarkDto newDto = bookmarkControl.addBookmark(newBookmarkDto);

		assertThat(newDto.getVerseRange(), equalTo(verseRange));

		assertThat(bookmarkControl.isBookmarkForKey(verseRange.getStart()), equalTo(true));
	}

	@Test
	public void testIsBookmarkForAnyVerseRangeWithSameStart() {
		BookmarkDto newBookmarkDto = new BookmarkDto();
		final VerseRange verseRange = new VerseRange(KJV_VERSIFICATION, new Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10));
		newBookmarkDto.setVerseRange(verseRange);

		bookmarkControl.addBookmark(newBookmarkDto);

		Verse startVerse = new Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10);
		assertThat(bookmarkControl.isBookmarkForKey(startVerse), equalTo(true));

		// 1 has the same start as 10 but is not the same
		Verse verseWithSameStart = new Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 1);
		assertThat(bookmarkControl.isBookmarkForKey(verseWithSameStart), equalTo(false));
	}

	@Test
	public void testGetVersesWithBookmarksInPassage() throws Exception {
		final VerseRange passage = new VerseRange(KJV_VERSIFICATION, new Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 1), new Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10));

		// add bookmark in range
		final BookmarkDto bookmarkDto = addBookmark("ps.17.1-ps.17.2");
		LabelDto greenLabelDto = new LabelDto();
		greenLabelDto.setName("G");
		greenLabelDto.setBookmarkStyle(BookmarkStyle.GREEN_HIGHLIGHT);
		greenLabelDto = bookmarkControl.saveOrUpdateLabel(greenLabelDto);
		bookmarkControl.setBookmarkLabels(bookmarkDto, Collections.singletonList(greenLabelDto));

		addBookmark("ps.17.10");

		// add bookmark out of range
		addBookmark("ps.17.0");
		addBookmark("ps.17.11");

		// check only bookmark in range is returned
		final Map<Integer, List<BookmarkStyle>> versesWithBookmarksInPassage = bookmarkControl.getVerseBookmarkStylesInPassage(passage);

		assertThat(versesWithBookmarksInPassage.size(), equalTo(3));
		assertThat(versesWithBookmarksInPassage.get(1), contains(BookmarkStyle.GREEN_HIGHLIGHT));
		assertThat(versesWithBookmarksInPassage.get(2), contains(BookmarkStyle.GREEN_HIGHLIGHT));
		assertThat(versesWithBookmarksInPassage.get(10), Matchers.<BookmarkStyle>empty());
	}

	private BookmarkDto addTestVerse() {
		try {
			currentTestVerse = getNextTestVerse();

			return addBookmark(currentTestVerse);
		} catch (Exception e) {
			fail("Error in verse:"+currentTestVerse);
		}
		return null;
	}

	private BookmarkDto addBookmark(String verse) throws NoSuchVerseException {
		BookmarkDto bookmark = new BookmarkDto();
		bookmark.setVerseRange(VerseRangeFactory.fromString(KJV_VERSIFICATION, verse));
		return bookmarkControl.addBookmark(bookmark);
	}

	private LabelDto addTestLabel() {
		currentTestLabel = getNextTestLabel();
		
		LabelDto label = new LabelDto();
		label.setName(currentTestLabel);
		
		return bookmarkControl.saveOrUpdateLabel(label);
	}

	private String getNextTestVerse() {
		return TEST_VERSE_START+(++testVerseCounter);
	}
	private String getNextTestLabel() {
		return TEST_LABEL_START+(++testLabelCounter);
	}
}
