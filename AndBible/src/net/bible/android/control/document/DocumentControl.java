package net.bible.android.control.document;

import java.util.List;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;

import android.view.View;

public class DocumentControl {
	public boolean canDelete(Book document) {
		return 	document != null && 
				document.getDriver().isDeletable(document) &&
				!document.equals(CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument());
	}
	
	/** Suggest an alternative bible to view or return null
	 * 
	 * @return
	 */
	public Book getSuggestedBible() {
		CurrentPageManager currentPageManager = ControlFactory.getInstance().getCurrentPageControl();
		Book currentBible = currentPageManager.getCurrentBible().getCurrentDocument();
		return getSuggestedBook(SwordApi.getInstance().getBibles(), currentBible, currentPageManager.isBibleShown());
	}

	/** Suggest an alternative comentary to view or return null
	 * 
	 * @return
	 */
	public Book getSuggestedCommentary() {
		CurrentPageManager currentPageManager = ControlFactory.getInstance().getCurrentPageControl();
		Book currentCommentary = currentPageManager.getCurrentCommentary().getCurrentDocument();
		return getSuggestedBook(SwordApi.getInstance().getBooks(BookCategory.COMMENTARY), currentCommentary,  currentPageManager.isCommentaryShown());
	}
	
	/** Suggest an alternative document to view or return null
	 * 
	 * @return
	 */
	private Book getSuggestedBook(List<Book> books, Book currentBook, boolean isShownNow) {
		Book suggestion = null;
		if (!isShownNow) {
			// allow easy switch back to bible view
			suggestion = currentBook;
		} else {
			// only suggest alternative if more than 1
			if (books.size()>1) {
				for (int i=0; i<books.size() && suggestion==null; i++) {
					Book bible = books.get(i);
					if (bible.equals(currentBook)) {
						// next bible in list or wrap around to first bible
						suggestion = books.get((i+1)%books.size()); 
					}
				}
			}
		}
		
		return suggestion;
	}
}
