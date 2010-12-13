package net.bible.android.control.bookmark;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

import org.crosswire.jsword.passage.PassageKeyFactory;

public class BookmarkControlTest extends TestCase {

	// keep changing the test verse
	private static final String TEST_VERSE_START = "Psalms 119:";
	private int testVerseCounter;
	private String currentTestVerse;
	
	// keep changing the test label
	private static final String TEST_LABEL_START = "Test label ";
	private int testLabelCounter;
	private String currentTestLabel;

	private BookmarkControl bookmarkControl;
	
	protected void setUp() throws Exception {
		super.setUp();
		bookmarkControl = new BookmarkControl();
	}

	protected void tearDown() throws Exception {
		super.tearDown();

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

	public void testAddBookmark() {
		try {
			BookmarkDto newDto = addTestVerse();
			assertEquals("New Bookmark key incorrect.  Test:"+currentTestVerse+" was:"+newDto.getKey().getName(), newDto.getKey().getName(), currentTestVerse);
			assertTrue("New Bookmark id incorrect", newDto.getId()>-1);
		} catch (Exception e) {
			e.printStackTrace();
			fail("exeption:"+e.getMessage());
		}
	}

	public void testGetAllBookmarks() {
		try {
			addTestVerse();
			addTestVerse();
			addTestVerse();
			
			List<BookmarkDto> bookmarks = bookmarkControl.getAllBookmarks();
			for (BookmarkDto dto : bookmarks) {
				System.out.println(dto.getId()+" "+dto.getKey().getName());
			}
			assertTrue( bookmarks.size()==3);
		} catch (Exception e) {
			e.printStackTrace();
			fail("exeption:"+e.getMessage());
		}
	}


	public void testDeleteBookmark() {
		addTestVerse();
		List<BookmarkDto> bookmarks = bookmarkControl.getAllBookmarks();
		BookmarkDto todelete = bookmarks.get(0);
		bookmarkControl.deleteBookmark(todelete);
		
		bookmarks = bookmarkControl.getAllBookmarks();
		for (BookmarkDto bookmark : bookmarks) {
			assertFalse("delete failed", bookmark.getId().equals(todelete.getId()));
		}
		
	}

	public void testAddLabel() {
		try {
			LabelDto newDto = addTestLabel();
			assertEquals("New Label name incorrect.  Test:"+currentTestLabel+" was:"+newDto.getName(), newDto.getName(), currentTestLabel);
			assertTrue("New Label id incorrect", newDto.getId()>-1);
		} catch (Exception e) {
			e.printStackTrace();
			fail("exeption:"+e.getMessage());
		}
	}

	public void testSetBookmarkLabels() {
		BookmarkDto bookmark = addTestVerse();
		LabelDto label1 = addTestLabel();
		LabelDto label2 = addTestLabel();
		List<LabelDto> labelList = new ArrayList<LabelDto>();
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
		List<LabelDto> labelList2 = new ArrayList<LabelDto>();
		labelList2.add(label1);
		bookmarkControl.setBookmarkLabels(bookmark, labelList2);
	
		List<BookmarkDto> list3 = bookmarkControl.getBookmarksWithLabel(label1);
		assertEquals(1, list3.size());
		List<BookmarkDto> list4 = bookmarkControl.getBookmarksWithLabel(label2);
		assertEquals(0, list4.size());
	}

	public void testGetBookmarksWithLabel() {
		BookmarkDto bookmark = addTestVerse();
		LabelDto label1 = addTestLabel();
		List<LabelDto> labelList = new ArrayList<LabelDto>();
		labelList.add(label1);
		
		// add 2 labels and check they are saved
		bookmarkControl.setBookmarkLabels(bookmark, labelList);

		List<BookmarkDto> list1 = bookmarkControl.getBookmarksWithLabel(label1);
		assertEquals(1, list1.size());
		assertEquals(bookmark, list1.get(0));
	}


	/**
	 * @return
	 */
	private BookmarkDto addTestVerse() {
		try {
			currentTestVerse = getNextTestVerse();
			
			BookmarkDto bookmark = new BookmarkDto();
			bookmark.setKey(PassageKeyFactory.instance().getKey(currentTestVerse));
			BookmarkDto newDto = bookmarkControl.addBookmark(bookmark);
			return newDto;
		} catch (Exception e) {
			fail("Error in verse:"+currentTestVerse);
		}
		return null;
	}
	/**
	 * @return
	 */
	private LabelDto addTestLabel() {
		currentTestLabel = getNextTestLabel();
		
		LabelDto label = new LabelDto();
		label.setName(currentTestLabel);
		LabelDto newDto = bookmarkControl.addLabel(label);
		return newDto;
	}

	private String getNextTestVerse() {
		return this.TEST_VERSE_START+(++testVerseCounter);
	}
	private String getNextTestLabel() {
		return this.TEST_LABEL_START+(++testLabelCounter);
	}
}
