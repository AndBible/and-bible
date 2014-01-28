package net.bible.android.control.bookmark;

import java.util.List;

import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;

public interface Bookmark {

	/** business method */
	boolean bookmarkCurrentVerse();
	
	/** text for item list */
	String getBookmarkVerseKey(BookmarkDto bookmark);
	String getBookmarkVerseText(BookmarkDto bookmark);
	
	/** get all labels */
	List<LabelDto> getAllLabels();

	/** get labels that can be assigned to a bookmark */
	List<LabelDto> getAssignableLabels();

	/** get bookmarks with the given label */
	BookmarkDto getBookmarkById(Long id);

	// pure bookmark methods
	
	/** get all bookmarks */
	List<BookmarkDto> getAllBookmarks();
	
	/** create a new bookmark */
	BookmarkDto addBookmark(BookmarkDto bookmark);
	
	/** delete this bookmark (and any links to labels) */
	boolean deleteBookmark(BookmarkDto bookmark);

	// Label related methods

	/** delete this label (and any links to bookmarks) */
	boolean deleteLabel(LabelDto label);

	/** create or update label name */ 
	public LabelDto saveOrUpdateLabel(LabelDto label);

	/** get bookmarks with the given label */
	List<BookmarkDto> getBookmarksWithLabel(LabelDto label);
	
	/** label the bookmark with these and only these labels */
	List<LabelDto> getBookmarkLabels(BookmarkDto bookmark);

	/** label the bookmark with these and only these labels */
	void setBookmarkLabels(BookmarkDto bookmark, List<LabelDto> labels);
	
	/** get a list of Verses which have bookmarks in the passage (normally a chapter) */
	List<Verse> getVersesWithBookmarksInPassage(Key passage);

	/** toggle order between date and Bibical */
	public void changeBookmarkSortOrder();

	public void setBookmarkSortOrder(BookmarkSortOrder bookmarkSortOrder);

	public String getBookmarkSortOrderDescription();
}
