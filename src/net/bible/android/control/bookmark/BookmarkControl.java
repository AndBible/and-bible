package net.bible.android.control.bookmark;

import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.service.db.bookmark.BookmarkDBAdapter;
import net.bible.service.db.bookmark.BookmarkDatabaseHelper.BookmarkColumn;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;
import android.content.ContentValues;

public class BookmarkControl implements Bookmark {

	// pure bookmark methods

	/** get all bookmarks */
	public List<BookmarkDto> getAllBookmarks() {

		return null;
	}

	/** create a new bookmark */
	public BookmarkDto addBookmark(BookmarkDto bookmark) {
		// Create a new row of values to insert.
		ContentValues newValues = new ContentValues();
		newValues.put(BookmarkColumn.KEY, bookmark.getKey().getName());

		BookmarkDBAdapter db = new BookmarkDBAdapter(BibleApplication.getApplication().getApplicationContext());
		db.open();
		BookmarkDto newBookmark = null;
		try {
			long newId = db.insertBookmark(newValues);
			
			newBookmark = db.getBookmarkDto(newId);
		} finally {
			db.close();
		}
		return newBookmark;
	}

	/** delete this bookmark (and any links to labels) */
	public boolean deleteBookmark(BookmarkDto bookmark) {

		return false;
	}

	// Label related methods
	/** get bookmarks with the given label */
	public List<BookmarkDto> getBookmarksWithLabel(LabelDto label) {

		return null;
	}

	/** label the bookmark with these and only these labels */
	public List<BookmarkDto> setBookmarkLabels(BookmarkDto bookmark,
			List<LabelDto> labels) {

		return null;
	}
}
