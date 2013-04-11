package net.bible.android.control.navigation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.versification.Scripture;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

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

	/**
	 * @return
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
		
		Versification versification = ((AbstractPassageBook)doc).getVersification();
		return versification;
	}
	
	

	public void setPageControl(PageControl pageControl) {
		this.pageControl = pageControl;
	}
}
