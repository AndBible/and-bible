package net.bible.android.control.bookmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.common.resource.ResourceProvider;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.bookmark.BookmarkDBAdapter;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;
import net.bible.service.sword.SwordContentFacade;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

import android.util.Log;
import android.widget.Toast;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkControl implements Bookmark {

	public LabelDto LABEL_ALL;
	public LabelDto LABEL_UNLABELLED;
	static {
	}
	
	private static final String BOOKMARK_SORT_ORDER = "BookmarkSortOrder";

	private static final String TAG = "BookmarkControl";
	
	public BookmarkControl(ResourceProvider resourceProvider) {
		LABEL_ALL = new LabelDto();
		LABEL_ALL.setName(resourceProvider.getString(R.string.all));
		LABEL_ALL.setId(Long.valueOf(-999));
		LABEL_UNLABELLED = new LabelDto();
		LABEL_UNLABELLED.setName(resourceProvider.getString(R.string.label_unlabelled));
		LABEL_UNLABELLED.setId(Long.valueOf(-998));
	}
	
	@Override
	public boolean toggleBookmarkForVerse(Verse verse) {
		boolean bOk = false;
		CurrentPageManager currentPageControl = ControlFactory.getInstance().getCurrentPageControl();
		if (currentPageControl.isBibleShown() || currentPageControl.isCommentaryShown()) {
			Verse currentVerse = verse;

			BookmarkDto bookmarkDto = getBookmarkByKey(currentVerse);
			if (bookmarkDto !=null) {
				if (deleteBookmark(bookmarkDto)) {
					Toast.makeText(BibleApplication.getApplication().getApplicationContext(), R.string.bookmark_deleted, Toast.LENGTH_SHORT).show();
				} else {
					Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
				}
			} else {
				// prepare new bookmark and add to db
				bookmarkDto = new BookmarkDto();
				bookmarkDto.setVerse(currentVerse);
				BookmarkDto newBookmark = addBookmark(bookmarkDto);
				
				if (newBookmark!=null) {
					// success
					Toast.makeText(BibleApplication.getApplication().getApplicationContext(), R.string.bookmark_added, Toast.LENGTH_SHORT).show();
					bOk = true;
				} else {
					Dialogs.getInstance().showErrorMsg(R.string.error_occurred);
				}
			}
		}
		return bOk;
	}

	@Override
	public String getBookmarkVerseKey(BookmarkDto bookmark) {
		String keyText = "";
		try {
			Versification versification = ControlFactory.getInstance().getCurrentPageControl().getCurrentBible().getVersification();
			keyText = bookmark.getVerse(versification).getName();
		} catch (Exception e) {
			Log.e(TAG, "Error getting verse text", e);
		}
		return keyText;
	}

	@Override
	public String getBookmarkVerseText(BookmarkDto bookmark) {
		String verseText = "";
		try {
			CurrentBiblePage currentBible = ControlFactory.getInstance().getCurrentPageControl().getCurrentBible();
			Versification versification = currentBible.getVersification();
			verseText = SwordContentFacade.getInstance().getPlainText(currentBible.getCurrentDocument(), bookmark.getVerse(versification), 1);
			verseText = CommonUtils.limitTextLength(verseText);
		} catch (Exception e) {
			Log.e(TAG, "Error getting verse text", e);
		}
		return verseText;
	}

	// pure bookmark methods

	/** get all bookmarks */
	public List<BookmarkDto> getAllBookmarks() {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		List<BookmarkDto> bookmarkList = null;
		try {
			db.open();
			bookmarkList = db.getAllBookmarks();
			bookmarkList = getSortedBookmarks(bookmarkList);
		} finally {
			db.close();
		}

		return bookmarkList;
	}

	/** create a new bookmark */
	public BookmarkDto addBookmark(BookmarkDto bookmark) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		BookmarkDto newBookmark = null;
		try {
			db.open();
			newBookmark = db.insertBookmark(bookmark);
		} finally {
			db.close();
		}
		return newBookmark;
	}

	/** get all bookmarks */
	public BookmarkDto getBookmarkById(Long id) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		BookmarkDto bookmark = null;
		try {
			db.open();
			bookmark = db.getBookmarkDto(id);
		} finally {
			db.close();
		}

		return bookmark;
	}

	@Override
	public boolean isBookmarkForKey(Key key) {
		return getBookmarkByKey(key)!=null;
	}

	/** get bookmark with this key if it exists or return null */
	private BookmarkDto getBookmarkByKey(Key key) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		BookmarkDto bookmark = null;
		try {
			db.open();
			bookmark = db.getBookmarkByKey(key.getOsisID());
		} finally {
			db.close();
		}

		return bookmark;
	}

	/** delete this bookmark (and any links to labels) */
	@Override
	public boolean deleteBookmark(BookmarkDto bookmark) {
		boolean bOk = false;
		if (bookmark!=null && bookmark.getId()!=null) {
			BookmarkDBAdapter db = new BookmarkDBAdapter();
			try {
				db.open();
				bOk = db.removeBookmark(bookmark);
			} finally {
				db.close();
			}
		}		
		return bOk;
	}

	// Label related methods
	/** get bookmarks with the given label */
	public List<BookmarkDto> getBookmarksWithLabel(LabelDto label) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		List<BookmarkDto> bookmarkList = null;
		try {
			db.open();
			if (LABEL_ALL.equals(label)) {
				bookmarkList = db.getAllBookmarks();
			} else if (LABEL_UNLABELLED.equals(label)) {
				bookmarkList = db.getUnlabelledBookmarks();
			} else {
				bookmarkList = db.getBookmarksWithLabel(label);
			}
			assert bookmarkList!=null;
			bookmarkList = getSortedBookmarks(bookmarkList);

		} finally {
			db.close();
		}

		return bookmarkList;
	}

	/** get bookmarks associated labels */
	public List<LabelDto> getBookmarkLabels(BookmarkDto bookmark) {
		List<LabelDto> labels;
		
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		try {
			db.open();
			labels = db.getBookmarkLabels(bookmark);
		} finally {
			db.close();
		}
		return labels;
	}


	/** label the bookmark with these and only these labels */
	public void setBookmarkLabels(BookmarkDto bookmark, List<LabelDto> labels) {
		// never save LABEL_ALL 
		labels.remove(LABEL_ALL);
		labels.remove(LABEL_UNLABELLED);
		
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		try {
			db.open();
			List<LabelDto> prevLabels = db.getBookmarkLabels(bookmark);
			
			//find those which have been deleted and remove them
			Set<LabelDto> deleted = new HashSet<LabelDto>(prevLabels);
			deleted.removeAll(labels);
			for (LabelDto label : deleted) {
				db.removeBookmarkLabelJoin(bookmark, label);
			}
			
			//find those which are new and persist them
			Set<LabelDto> added = new HashSet<LabelDto>(labels);
			added.removeAll(prevLabels);
			for (LabelDto label : added) {
				db.insertBookmarkLabelJoin(bookmark, label);
			}

		} finally {
			db.close();
		}
	}
	
	@Override
	public LabelDto saveOrUpdateLabel(LabelDto label) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		LabelDto retLabel = null;
		try {
			db.open();
			if (label.getId()==null) {
				retLabel = db.insertLabel(label);
			} else {
				retLabel = db.updateLabel(label);			
			}
		} finally {
			db.close();
		}
		return retLabel;
	}

	/** delete this bookmark (and any links to labels) */
	public boolean deleteLabel(LabelDto label) {
		boolean bOk = false;
		if (label!=null && label.getId()!=null && !LABEL_ALL.equals(label) && !LABEL_UNLABELLED.equals(label)) {
			BookmarkDBAdapter db = new BookmarkDBAdapter();
			try {
				db.open();
				bOk = db.removeLabel(label);
			} finally {
				db.close();
			}
		}
		return bOk;
	}

	@Override
	public List<LabelDto> getAllLabels() {
		List<LabelDto> labelList = new ArrayList<LabelDto>();

		labelList = getAssignableLabels();
		Collections.sort(labelList);
		
		// add special label that is automatically associated with all-bookmarks
		labelList.add(0, LABEL_UNLABELLED);
		labelList.add(0, LABEL_ALL);

		return labelList;
	}

	@Override
	public List<LabelDto> getAssignableLabels() {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		List<LabelDto> labelList = new ArrayList<LabelDto>();
		try {
			db.open();
			labelList.addAll(db.getAllLabels());
		} finally {
			db.close();
		}

		return labelList;
	}

	@Override
	public List<Verse> getVersesWithBookmarksInPassage(Key passage) {
		// assumes the passage only covers one book, which always happens to be the case here
		Verse firstVerse = KeyUtil.getVerse(passage);
		BibleBook book = firstVerse.getBook();
		
		// get all Bookmarks in containing book to include variations due to differing versifications
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		List<BookmarkDto> bookmarkList = null;
		try {
			db.open();
			bookmarkList = db.getBookmarksInBook(book);
		} finally {
			db.close();
		}
		
		// convert to required versification and check verse is in passage
		List<Verse> versesInPassage = new ArrayList<Verse>();
		if (bookmarkList!=null) {
			boolean isVerseRange = passage instanceof VerseRange;
			Versification requiredVersification = firstVerse.getVersification();
			for (BookmarkDto bookmarkDto : bookmarkList) {
				Verse verse = bookmarkDto.getVerse(requiredVersification);
				//TODO should not require VerseRange cast but bug in JSword
				if (isVerseRange) {
					if (((VerseRange)passage).contains(verse)) {
						versesInPassage.add(verse);
					}
				} else {
					if (passage.contains(verse)) {
						versesInPassage.add(verse);
					}
				}
			}
		}

		return versesInPassage;
	}
	
	private List<BookmarkDto> getSortedBookmarks(List<BookmarkDto> bookmarkList) {
		Comparator<BookmarkDto> comparator = null;
		switch (getBookmarkSortOrder()) {
			case DATE_CREATED:
				comparator = BookmarkDto.BOOKMARK_CREATION_DATE_COMPARATOR;
				break;
			case BIBLE_BOOK:
			default:
				comparator = BookmarkDto.BOOKMARK_BIBLE_ORDER_COMPARATOR;
				break;
			
		}
		Collections.sort(bookmarkList, comparator);
		return bookmarkList;
	}

	public void changeBookmarkSortOrder() {
		if (getBookmarkSortOrder().equals(BookmarkSortOrder.BIBLE_BOOK)) {
			setBookmarkSortOrder(BookmarkSortOrder.DATE_CREATED);
		} else {
			setBookmarkSortOrder(BookmarkSortOrder.BIBLE_BOOK);
		}
	}
	
	public BookmarkSortOrder getBookmarkSortOrder() {
		String bookmarkSortOrderStr = CommonUtils.getSharedPreference(BOOKMARK_SORT_ORDER, BookmarkSortOrder.BIBLE_BOOK.toString());
		return BookmarkSortOrder.valueOf(bookmarkSortOrderStr);
	}
	
	@Override
	public void setBookmarkSortOrder(BookmarkSortOrder bookmarkSortOrder) {
		CommonUtils.saveSharedPreference(BOOKMARK_SORT_ORDER, bookmarkSortOrder.toString());
	}

	@Override
	public String getBookmarkSortOrderDescription() {
		if (BookmarkSortOrder.BIBLE_BOOK.equals(getBookmarkSortOrder())) {
			return CommonUtils.getResourceString(R.string.sort_by_bible_book);
		} else {
			return CommonUtils.getResourceString(R.string.sort_by_date);
		}
	}
}
