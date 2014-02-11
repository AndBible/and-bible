package net.bible.android.control.navigation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.versification.Scripture;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.SystemKJV;
import org.crosswire.jsword.versification.system.Versifications;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class NavigationControl {
	
	private PageControl pageControl;
	
	/** 
	 * get books from current Versification that are scriptural
	 */
	public List<BibleBook> getScripturalBibleBooks() {
		
		List<BibleBook> books = new ArrayList<BibleBook>();
		Iterator<BibleBook> bookIter = getVersification().getBookIterator();
		while (bookIter.hasNext()) {
			BibleBook bibleBook = bookIter.next();
    		if (Scripture.isScripture(bibleBook)) {
    			books.add(bibleBook);
    		}
		}

		return books;
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
		CurrentPageManager currentPageManager = pageControl.getCurrentPageManager();
		Book doc;
		if (currentPageManager.isBibleShown() || currentPageManager.isCommentaryShown()) {
			doc = currentPageManager.getCurrentPage().getCurrentDocument();
		} else {
			// should not reach here
			doc = currentPageManager.getCurrentBible().getCurrentDocument();
		}
		
		// this should always be true
		if (doc!=null && doc instanceof AbstractPassageBook) {
			return ((AbstractPassageBook)doc).getVersification();
		} else {
			// but safety first
			return Versifications.instance().getVersification(SystemKJV.V11N_NAME);
		}
	}
	
	

	public void setPageControl(PageControl pageControl) {
		this.pageControl = pageControl;
	}
}
