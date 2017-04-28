package net.bible.android.control.bookmark;

import android.support.annotation.NonNull;

import net.bible.service.db.bookmark.BookmarkDto;

import java.util.Comparator;

/**
 * Sort bookmarks by create date, most recent first
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class BookmarkCreationDateComparator implements Comparator<BookmarkDto> {

	public int compare(@NonNull BookmarkDto bookmark1, @NonNull BookmarkDto bookmark2) {
		// descending order
		return bookmark2.getCreatedOn().compareTo(bookmark1.getCreatedOn());
	}

}
