package net.bible.android.control.bookmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.bookmark.BookmarkDBAdapter;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;
import net.bible.service.sword.SwordContentFacade;

import org.crosswire.jsword.passage.Key;

import android.util.Log;
import android.widget.Toast;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BookmarkControl implements Bookmark {

	public static final LabelDto LABEL_ALL;
	public static final LabelDto LABEL_UNLABELLED;
	static {
		LABEL_ALL = new LabelDto();
		LABEL_ALL.setName(BibleApplication.getApplication().getString(R.string.all));
		LABEL_ALL.setId(Long.valueOf(-999));
		LABEL_UNLABELLED = new LabelDto();
		LABEL_UNLABELLED.setName(BibleApplication.getApplication().getString(R.string.label_unlabelled));
		LABEL_UNLABELLED.setId(Long.valueOf(-998));
	}
	
	private static final String TAG = "BookmarkControl";
	
	@Override
	public boolean bookmarkCurrentVerse() {
		boolean bOk = false;
		if (CurrentPageManager.getInstance().isBibleShown() || CurrentPageManager.getInstance().isCommentaryShown()) {
			Key currentVerse = CurrentPageManager.getInstance().getCurrentBible().getSingleKey();
			
			if (getBookmarkByKey(currentVerse)!=null) {
				// bookmark for this verse already exists
				Toast.makeText(BibleApplication.getApplication().getApplicationContext(), R.string.bookmark_exists, Toast.LENGTH_SHORT).show();
			} else {
				// prepare new bookmark and add to db
				BookmarkDto bookmarkDto = new BookmarkDto();
				bookmarkDto.setKey(currentVerse);
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
	public String getBookmarkVerseText(BookmarkDto bookmark) {
		String verseText = "";
		try {
			verseText = SwordContentFacade.getInstance().getPlainText(CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument(), bookmark.getKey().getOsisRef(), 1);
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
		db.open();
		List<BookmarkDto> bookmarkList = null;
		try {
			bookmarkList = db.getAllBookmarks();
			Collections.sort(bookmarkList);
		} finally {
			db.close();
		}

		return bookmarkList;
	}

	/** create a new bookmark */
	public BookmarkDto addBookmark(BookmarkDto bookmark) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		db.open();
		BookmarkDto newBookmark = null;
		try {
			newBookmark = db.insertBookmark(bookmark);
		} finally {
			db.close();
		}
		return newBookmark;
	}

	/** get all bookmarks */
	public BookmarkDto getBookmarkById(Long id) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		db.open();
		BookmarkDto bookmark = null;
		try {
			bookmark = db.getBookmarkDto(id);
		} finally {
			db.close();
		}

		return bookmark;
	}

	/** get bookmark with this key if it exists or return null */
	public BookmarkDto getBookmarkByKey(Key key) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		db.open();
		BookmarkDto bookmark = null;
		try {
			bookmark = db.getBookmarkByKey(key.getOsisID());
		} finally {
			db.close();
		}

		return bookmark;
	}

	/** delete this bookmark (and any links to labels) */
	public boolean deleteBookmark(BookmarkDto bookmark) {
		boolean bOk = false;
		if (bookmark!=null && bookmark.getId()!=null) {
			BookmarkDBAdapter db = new BookmarkDBAdapter();
			db.open();
			bOk = db.removeBookmark(bookmark);
		}		
		return bOk;
	}

	// Label related methods
	/** get bookmarks with the given label */
	public List<BookmarkDto> getBookmarksWithLabel(LabelDto label) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		db.open();
		List<BookmarkDto> bookmarkList = null;
		try {
			if (LABEL_ALL.equals(label)) {
				bookmarkList = db.getAllBookmarks();
			} else if (LABEL_UNLABELLED.equals(label)) {
				bookmarkList = db.getUnlabelledBookmarks();
			} else {
				bookmarkList = db.getBookmarksWithLabel(label);
			}
			assert bookmarkList!=null;
			Collections.sort(bookmarkList);

		} finally {
			db.close();
		}

		return bookmarkList;
	}

	/** get bookmarks associated labels */
	public List<LabelDto> getBookmarkLabels(BookmarkDto bookmark) {
		List<LabelDto> labels;
		
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		db.open();
		try {
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
		db.open();
		try {
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
	public LabelDto addLabel(LabelDto label) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		db.open();
		LabelDto newLabel = null;
		try {
			newLabel = db.insertLabel(label);
		} finally {
			db.close();
		}
		return newLabel;
	}

	/** delete this bookmark (and any links to labels) */
	public boolean deleteLabel(LabelDto label) {
		boolean bOk = false;
		if (label!=null && label.getId()!=null && !LABEL_ALL.equals(label) && !LABEL_UNLABELLED.equals(label)) {
			BookmarkDBAdapter db = new BookmarkDBAdapter();
			db.open();
			bOk = db.removeLabel(label);
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
		db.open();
		List<LabelDto> labelList = new ArrayList<LabelDto>();
		try {
			labelList.addAll(db.getAllLabels());
		} finally {
			db.close();
		}

		return labelList;
	}

	@Override
	public List<Key> getKeysWithBookmarksInPassage(Key passage) {
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		db.open();
		List<BookmarkDto> bookmarkList = null;
		try {
			bookmarkList = db.getBookmarksInPassage(passage);
			Collections.sort(bookmarkList);
		} finally {
			db.close();
		}

		List<Key> keysWithBookmarks = new ArrayList<Key>();
		if (bookmarkList!=null) {
			for (BookmarkDto bookmarkDto : bookmarkList) {
				keysWithBookmarks.add(bookmarkDto.getKey());
			}
		}
		return keysWithBookmarks;
	}
}
