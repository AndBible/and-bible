package net.bible.android.control.navigation;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.versification.Scripture;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.SystemKJV;
import org.crosswire.jsword.versification.system.Versifications;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * Used by Passage navigation ui
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class NavigationControl {
	
	private final PageControl pageControl;
	
	private final DocumentBibleBooksFactory documentBibleBooksFactory;
	
	private static final String BIBLE_BOOK_SORT_ORDER = "BibleBookSortOrder";

	@Inject
	public NavigationControl(PageControl pageControl, DocumentBibleBooksFactory documentBibleBooksFactory) {
		this.pageControl = pageControl;
		this.documentBibleBooksFactory = documentBibleBooksFactory;
	}

	/** 
	 * Get books in current Document - either all Scripture books or all non-Scripture books
	 */
	public List<BibleBook> getBibleBooks(boolean isScriptureRequired) {
		List<BibleBook> books = new ArrayList<>();

		AbstractPassageBook currentPassageDocument = getCurrentPassageDocument();
		if (currentPassageDocument!=null) {
			List<BibleBook> documentBookList = documentBibleBooksFactory.getBooksFor(currentPassageDocument);

			for (BibleBook bibleBook : documentBookList) {
				if (isScriptureRequired == Scripture.isScripture(bibleBook)) {
					books.add(bibleBook);
				}
			}

			books = getSortedBibleBooks(books, currentPassageDocument.getVersification());
		}
		return books;
	}

	public boolean isCurrentDefaultScripture() {
		return pageControl.isCurrentPageScripture();
	}
	
	/** Is this book of the bible not a single chapter book
	 * 
	 * @param book to check
	 * @return true if multi-chapter book
	 */
	public boolean hasChapters(BibleBook book) {
		return getVersification().getLastChapter(book)>1;
	}
	
	/** default book for use when jumping into the middle of passage selection
	 */
	public int getDefaultBibleBookNo() {
		return Arrays.binarySearch(BibleBook.values(), pageControl.getCurrentBibleVerse().getBook());
	}

	/** default chapter for use when jumping into the middle of passage selection
	 */
	public int getDefaultBibleChapterNo() {
		return pageControl.getCurrentBibleVerse().getChapter();
	}

	/**
	 * @return v11n of current document
	 */
	public Versification getVersification() {
		AbstractPassageBook doc = getCurrentPassageDocument();
		
		// this should always be true
		if (doc!=null) {
			return doc.getVersification();
		} else {
			// but safety first
			return Versifications.instance().getVersification(SystemKJV.V11N_NAME);
		}
	}
	
	private List<BibleBook> getSortedBibleBooks(List<BibleBook> bibleBookList, Versification versification) {
		if (getBibleBookSortOrder()==BibleBookSortOrder.ALPHABETICAL) {
			Collections.sort(bibleBookList, new BibleBookAlphabeticalComparator(versification));
		}
		return bibleBookList;
	}

	public void changeBibleBookSortOrder() {
		if (getBibleBookSortOrder().equals(BibleBookSortOrder.BIBLE_BOOK)) {
			setBibleBookSortOrder(BibleBookSortOrder.ALPHABETICAL);
		} else {
			setBibleBookSortOrder(BibleBookSortOrder.BIBLE_BOOK);
		}
	}
	
	private BibleBookSortOrder getBibleBookSortOrder() {
		String bibleBookSortOrderStr = CommonUtils.getSharedPreference(BIBLE_BOOK_SORT_ORDER, BibleBookSortOrder.BIBLE_BOOK.toString());
		return BibleBookSortOrder.valueOf(bibleBookSortOrderStr);
	}
	
	private void setBibleBookSortOrder(BibleBookSortOrder bibleBookSortOrder) {
		CommonUtils.saveSharedPreference(BIBLE_BOOK_SORT_ORDER, bibleBookSortOrder.toString());
	}

	/**
	 * The description is the opposite of the current state because the button text describes what will happen if you press it.
	 */
	public String getBibleBookSortOrderButtonDescription() {
		if (BibleBookSortOrder.BIBLE_BOOK.equals(getBibleBookSortOrder())) {
			return CommonUtils.getResourceString(R.string.sort_by_alphabetical);
		} else {
			return CommonUtils.getResourceString(R.string.sort_by_bible_book);
		}
	}
	
	/** 
	 * When navigating books and chapters there should always be a current Passage based book
	 */
	private AbstractPassageBook getCurrentPassageDocument() {
		return pageControl.getCurrentPageManager().getCurrentPassageDocument();
	}
}
