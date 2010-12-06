package net.bible.android.control.bookmark;

import java.util.List;

import net.bible.service.db.bookmark.BookmarkDBAdapter;
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

		// Assign values for each row.
		newValues.put(BookmarkDBAdapter.BOOKMARK_KEY, bookmark.getKey()
				.getName());
		newValues.put(BookmarkDBAdapter.BOOKMARK_BOOK, bookmark.getBook()
				.getInitials());

		return null;
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
