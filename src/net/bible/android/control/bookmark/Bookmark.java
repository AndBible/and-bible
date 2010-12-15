package net.bible.android.control.bookmark;

import java.util.List;

import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

public interface Bookmark {

	//** business method */
	boolean bookmarkCurrentVerse();
	
	String getBookmarkVerseText(BookmarkDto bookmark);
	
	/** get all labels */
	List<LabelDto> getAllLabels();

	// pure bookmark methods
	
	/** get all bookmarks */
	List<BookmarkDto> getAllBookmarks();
	
	/** create a new bookmark */
	BookmarkDto addBookmark(BookmarkDto bookmark);
	
	/** delete this bookmark (and any links to labels) */
	boolean deleteBookmark(BookmarkDto bookmark);

	// Label related methods

	/** create a new label */
	LabelDto addLabel(LabelDto label);

	/** delete this label (and any links to bookmarks) */
	boolean deleteLabel(LabelDto label);

	/** get bookmarks with the given label */
	List<BookmarkDto> getBookmarksWithLabel(LabelDto label);
	
	/** label the bookmark with these and only these labels */
	void setBookmarkLabels(BookmarkDto bookmark, List<LabelDto> labels);
}
