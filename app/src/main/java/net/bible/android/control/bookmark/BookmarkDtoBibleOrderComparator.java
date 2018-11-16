package net.bible.android.control.bookmark;

import net.bible.android.control.versification.sort.ConvertibleVerseRangeComparator;
import net.bible.service.db.bookmark.BookmarkDto;

import java.util.Comparator;
import java.util.List;

/**
 * Complex comparison of dtos ensuring the best v11n is used for each comparison.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class BookmarkDtoBibleOrderComparator implements Comparator<BookmarkDto> {
	private final ConvertibleVerseRangeComparator convertibleVerseRangeComparator;

	public BookmarkDtoBibleOrderComparator(List<BookmarkDto> bookmarkDtos) {
		this.convertibleVerseRangeComparator = new ConvertibleVerseRangeComparator.Builder().withBookmarks(bookmarkDtos).build();
	}

	@Override
	public int compare(BookmarkDto o1, BookmarkDto o2) {
		return convertibleVerseRangeComparator.compare(o1, o2);
	}
}
