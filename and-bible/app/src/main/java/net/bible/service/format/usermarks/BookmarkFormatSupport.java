package net.bible.service.format.usermarks;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.bookmark.BookmarkStyle;
import net.bible.service.db.bookmark.BookmarkDBAdapter;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.db.bookmark.LabelDto;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

/**
 * Support display of bookmarked verses.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@ApplicationScope
public class BookmarkFormatSupport {

	@Inject
	public BookmarkFormatSupport() {
	}

	public Map<Integer, List<BookmarkStyle>> getVerseBookmarkStylesInPassage(Key passage) {
		// assumes the passage only covers one book, which always happens to be the case here
		Verse firstVerse = KeyUtil.getVerse(passage);
		BibleBook book = firstVerse.getBook();

		// get all Bookmarks in containing book to include variations due to differing versifications
		BookmarkDBAdapter db = new BookmarkDBAdapter();
		List<BookmarkDto> bookmarkList;
		Map<Integer, List<BookmarkStyle>> bookmarkStylesByVerseNoInPassage = new HashMap<>();
		try {
			db.open();
			bookmarkList = db.getBookmarksInBook(book);

			// convert to required versification and check verse is in passage
			if (bookmarkList!=null) {
				Versification requiredVersification = firstVerse.getVersification();
				for (BookmarkDto bookmarkDto : bookmarkList) {
					VerseRange bookmarkVerseRange = bookmarkDto.getVerseRange(requiredVersification);
					if (passage.contains(bookmarkVerseRange.getStart())) {
						final List<LabelDto> bookmarkLabels = db.getBookmarkLabels(bookmarkDto);
						final List<BookmarkStyle> bookmarkStyles = getBookmarkStyles(bookmarkLabels);

						for (Verse verse : bookmarkVerseRange.toVerseArray()) {
							bookmarkStylesByVerseNoInPassage.put(verse.getVerse(), bookmarkStyles);
						}
					}
				}
			}

		} finally {
			db.close();
		}
		return bookmarkStylesByVerseNoInPassage;
	}

	/**
	 * Get distinct styles in enum order
	 */
	private List<BookmarkStyle> getBookmarkStyles(List<LabelDto> bookmarkLabels) {
		Set<BookmarkStyle> bookmarkStyles = new TreeSet<>();
		for (LabelDto label : bookmarkLabels) {
			BookmarkStyle style = label.getBookmarkStyle();

			if (style!=null) {
				bookmarkStyles.add(style);
			}
		}
		return new ArrayList<>(bookmarkStyles);
	}
}
