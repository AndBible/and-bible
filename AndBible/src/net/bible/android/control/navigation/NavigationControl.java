package net.bible.android.control.navigation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.versification.BibleTraverser;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.SystemKJV;
import org.crosswire.jsword.versification.system.Versifications;

/**
 * Used by Passage navigation ui
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class NavigationControl {
	
	private boolean isBibleBookSelectorShowingScripture = true;
	
	private PageControl pageControl;
	
	private DocumentBibleBooksFactory documentBibleBooksFactory;
	
	private BibleTraverser bibleTraverser;
	
	/** 
	 * Get books in current Document - either all Scripture books or all non-Scripture books
	 */
	public List<BibleBook> getBibleBooks(boolean isScriptureRequired) {
		List<BibleBook> books = new ArrayList<BibleBook>();

		List<BibleBook> documentBookList = documentBibleBooksFactory.getBooksFor(getCurrentPassageDocument());

		for (BibleBook bibleBook : documentBookList) {
    		if (isScriptureRequired == bibleTraverser.isScripture(bibleBook)) { //&& !Scripture.isIntro(bibleBook)
    			books.add(bibleBook);
    		}
		}

		return books;
	}

	public boolean currentDocumentContainsNonScripture() {
		return getBibleBooks(false).size()>0;
	}
	
	public boolean isCurrentlyShowingScripture() {
		return isBibleBookSelectorShowingScripture || !currentDocumentContainsNonScripture();  
	}
	
	public void toggleBibleBookSelectorScriptureDisplay() {
		isBibleBookSelectorShowingScripture = !isBibleBookSelectorShowingScripture;  
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
		Book doc = getCurrentPassageDocument();
		
		// this should always be true
		if (doc!=null && doc instanceof AbstractPassageBook) {
			return ((AbstractPassageBook)doc).getVersification();
		} else {
			// but safety first
			return Versifications.instance().getVersification(SystemKJV.V11N_NAME);
		}
	}
	
	/** 
	 * When navigating books and chapters there should always be a current Passage based book
	 */
	private AbstractPassageBook getCurrentPassageDocument() {
		CurrentPageManager currentPageManager = pageControl.getCurrentPageManager();
		Book doc;
		if (currentPageManager.isBibleShown() || currentPageManager.isCommentaryShown()) {
			doc = currentPageManager.getCurrentPage().getCurrentDocument();
		} else {
			// should not reach here
			doc = currentPageManager.getCurrentBible().getCurrentDocument();
		}
		return (AbstractPassageBook)doc;
	}

	public void setPageControl(PageControl pageControl) {
		this.pageControl = pageControl;
	}

	public void setDocumentBibleBooksFactory(DocumentBibleBooksFactory documentBibleBooksFactory) {
		this.documentBibleBooksFactory = documentBibleBooksFactory;
	}

	public void setBibleTraverser(BibleTraverser bibleTraverser) {
		this.bibleTraverser = bibleTraverser;
	}
}
