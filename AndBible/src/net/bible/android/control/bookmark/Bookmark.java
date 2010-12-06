package net.bible.android.control.bookmark;

import java.util.List;

import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

public interface Bookmark {

	// pure bookmark methods
	
	/** get all bookmarks */
	List<BookmarkDto> getAllBookmarks();
	
	/** create a new bookmark */
	BookmarkDto addBookmark(BookmarkDto bookmark);
	
	/** delete this bookmark (and any links to labels) */
	boolean deleteBookmark(BookmarkDto bookmark);

	// Label related methods
	/** get bookmarks with the given label */
	List<BookmarkDto> getBookmarksWithLabel(LabelDto label);
	
	/** label the bookmark with these and only these labels */
	List<BookmarkDto> setBookmarkLabels(BookmarkDto bookmark, List<LabelDto> labels);
}
