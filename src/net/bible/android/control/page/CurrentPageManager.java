package net.bible.android.control.page;

import java.util.List;

import net.bible.android.control.PassageChangeMediator;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.content.SharedPreferences;

public class CurrentPageManager {
	// use the same verse in the commentary and bible to keep them in sync
	private CurrentBibleVerse currentBibleVerse;
	private CurrentBiblePage currentBiblePage;
	private CurrentCommentaryPage currentCommentaryPage;
	private CurrentDictionaryPage currentDictionaryPage;
	
	private CurrentPage currentDisplayedPage;
	
	private static CurrentPageManager singleton;
	
	static public CurrentPageManager getInstance() {
		if (singleton==null) {
			synchronized(CurrentPageManager.class)  {
				if (singleton==null) {
					CurrentPageManager instance = new CurrentPageManager();
					instance.currentBibleVerse = new CurrentBibleVerse();
					instance.currentBiblePage = new CurrentBiblePage(instance.currentBibleVerse);
					instance.currentCommentaryPage = new CurrentCommentaryPage(instance.currentBibleVerse);
					
					instance.currentDictionaryPage = new CurrentDictionaryPage();
					
					instance.currentDisplayedPage = instance.currentBiblePage;
					singleton = instance;
				}
			}
		}
		return singleton;
	}
	
	public CurrentPage getCurrentPage() {
		return currentDisplayedPage;
	}
	public CurrentBiblePage getCurrentBible() {
		return currentBiblePage;
	}
	public CurrentDictionaryPage getCurrentDictionary() {
		return currentDictionaryPage;
	}
	
	public CurrentPage setCurrentDocument(Book currentBook) {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();

		CurrentPage nextPage = getBookPage(currentBook);
		if (nextPage!=null) {
			// must be in this order because History needs to grab the current doc before change
			nextPage.setCurrentDocument(currentBook);
			currentDisplayedPage = nextPage;
		}
		
		PassageChangeMediator.getInstance().onCurrentPageChanged();

		return nextPage;
	}

	public CurrentPage setCurrentDocumentAndKey(Book currentBook, Key key) {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();

		CurrentPage nextPage = getBookPage(currentBook);
		if (nextPage!=null) {
			try {
				nextPage.setInhibitChangeNotifications(true);
				nextPage.setCurrentDocument(currentBook);
				nextPage.setKey(key);
				currentDisplayedPage = nextPage;
			} finally {
				nextPage.setInhibitChangeNotifications(false);
			}
		}
		PassageChangeMediator.getInstance().onCurrentPageChanged();

		return nextPage;
	}
	
	private CurrentPage getBookPage(Book book) {
		BookCategory bookCategory = book.getBookCategory();
		CurrentPage bookPage = null;
		if (bookCategory.equals(BookCategory.BIBLE)) {
			bookPage = currentBiblePage;
		} else if (bookCategory.equals(BookCategory.COMMENTARY)) {
			bookPage = currentCommentaryPage;
		} else if (bookCategory.equals(BookCategory.DICTIONARY)) {
			bookPage = currentDictionaryPage;
		}
		return bookPage;
	}

	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	public void saveState(SharedPreferences outState) {
		//xxtodo save & restore other module types
		currentBiblePage.saveState(outState);
	}
	/** called during app start-up to restore previous state
	 * 
	 * @param inState
	 */
	public void restoreState(SharedPreferences inState) {
		//xxtodo save & restore other module types
		currentBiblePage.restoreState(inState);
	}
	
	public boolean isBibleShown() {
		return currentBiblePage == currentDisplayedPage;
	}
	public void showBible() {
		PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();
		currentDisplayedPage = currentBiblePage;
		PassageChangeMediator.getInstance().onCurrentPageChanged();
	}
	
	/** Suggest an alternative bible to view or return null
	 * 
	 * @return
	 */
	public Book getSuggestedBible() {
		Book currentBible = currentBiblePage.getCurrentDocument();
		return getSuggestedBook(SwordApi.getInstance().getBibles(), currentBible, isBibleShown());
	}

	/** Suggest an alternative comentary to view or return null
	 * 
	 * @return
	 */
	public Book getSuggestedCommentary() {
		Book currentCommentary = currentCommentaryPage.getCurrentDocument();
		return getSuggestedBook(SwordApi.getInstance().getBooks(BookCategory.COMMENTARY), currentCommentary,  currentCommentaryPage == currentDisplayedPage);
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
