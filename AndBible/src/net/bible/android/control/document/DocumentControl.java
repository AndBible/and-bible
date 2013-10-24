package net.bible.android.control.document;

import java.util.List;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.versification.ConvertibleVerse;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.common.util.Filter;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;

import android.util.Log;

/** Control use of different documents/books/modules - used by front end
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DocumentControl {
	
	private static final String TAG = "DocumentControl";

	/** user wants to change to a different document/module
	 * 
	 * @param newDocument
	 */
	public void changeDocument(Book newDocument) {
		CurrentPageManager.getInstance().setCurrentDocument( newDocument );
	}
	
	/** Book is deletable according to the driver if it is in the download dir i.e. not sdcard\jsword
	 * and according to And Bible if it is not currently selected
	 * @param document
	 * @return
	 */
	public boolean canDelete(Book document) {
		return 	document != null && 
				document.getDriver().isDeletable(document) &&
				!document.equals(CurrentPageManager.getInstance().getCurrentBible().getCurrentDocument()) &&
				!document.equals(CurrentPageManager.getInstance().getCurrentCommentary().getCurrentDocument()) &&
				!document.equals(CurrentPageManager.getInstance().getCurrentDictionary().getCurrentDocument());
	}
	
	/** delete selected document, even of current doc (Map and Gen Book only currently) and tidy up CurrentPage
	 */
	public void deleteDocument(Book document) throws BookException {
		SwordDocumentFacade.getInstance().deleteDocument(document);
				
		CurrentPageManager currentPageManager = ControlFactory.getInstance().getCurrentPageControl();
		CurrentPage currentPage = currentPageManager.getBookPage(document);
		if (currentPage!=null) {
			currentPage.checkCurrentDocumentStillInstalled();
		}
	}
	
	/** Suggest an alternative dictionary to view or return null
	 */
	public boolean isStrongsInBook() {
		try {
			Book currentBook = ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getCurrentDocument();
			// very occasionally the below has thrown an Exception and I don't know why, so I wrap all this in a try/catch
			return currentBook.getBookMetaData().hasFeature(FeatureType.STRONGS_NUMBERS);
		} catch (Exception e) {
			Log.e(TAG, "Error checking for strongs Numbers in book", e);
			return false;
		}
	}

	/** are we currently in Bible, Commentary, Dict, or Gen Book mode
	 */
	public BookCategory getCurrentCategory() {
		return ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getBookCategory();
	}
	
	/** show split book/chap/verse buttons in toolbar for Bibles and Commentaries
	 */
	public boolean showSplitPassageSelectorButtons() {
		BookCategory currentCategory = getCurrentCategory();
		return	(BookCategory.BIBLE.equals(currentCategory) ||
				BookCategory.COMMENTARY.equals(currentCategory));
	}
	
	/** Suggest an alternative bible to view or return null
	 * 
	 * @return
	 */
	public Book getSuggestedBible() {
		CurrentPageManager currentPageManager = ControlFactory.getInstance().getCurrentPageControl();
		Book currentBible = currentPageManager.getCurrentBible().getCurrentDocument();
		final ConvertibleVerse requiredVerseConverter = getRequiredVerseForSuggestions();
		
		// only show bibles that contain verse
		Filter<Book> bookFilter = new Filter<Book>() {
			@Override
			public boolean test(Book book) {
				return book.contains(requiredVerseConverter.getVerse(((AbstractPassageBook)book).getVersification()));
			}
		};
		
		return getSuggestedBook(SwordDocumentFacade.getInstance().getBibles(), currentBible, bookFilter, currentPageManager.isBibleShown());
	}

	/** Suggest an alternative commentary to view or return null
	 */
	public Book getSuggestedCommentary() {
		CurrentPageManager currentPageManager = ControlFactory.getInstance().getCurrentPageControl();
		Book currentCommentary = currentPageManager.getCurrentCommentary().getCurrentDocument();
		final ConvertibleVerse requiredVerseConverter = getRequiredVerseForSuggestions();
		
		// only show commentaries that contain verse - extra checks for TDavid because it always returns true
		Filter<Book> bookFilter = new Filter<Book>() {
			@Override
			public boolean test(Book book) {
				Verse verse = requiredVerseConverter.getVerse(((AbstractPassageBook)book).getVersification());
				if (!book.contains(verse)) {
					return false;
				}

				// book claims to contain the verse but 
				// TDavid has a flawed index and incorrectly claims to contain contents for all books of the bible so only return true if !TDavid or is Psalms
				return !book.getInitials().equals("TDavid") || 
						verse.getBook().equals(BibleBook.PS);
			}
		};

		
		return getSuggestedBook(SwordDocumentFacade.getInstance().getBooks(BookCategory.COMMENTARY), currentCommentary, bookFilter, currentPageManager.isCommentaryShown());
	}

	/** Suggest an alternative dictionary to view or return null
	 */
	public Book getSuggestedDictionary() {
		CurrentPageManager currentPageManager = ControlFactory.getInstance().getCurrentPageControl();
		Book currentDictionary = currentPageManager.getCurrentDictionary().getCurrentDocument();
		return getSuggestedBook(SwordDocumentFacade.getInstance().getBooks(BookCategory.DICTIONARY), currentDictionary, null, currentPageManager.isDictionaryShown());
	}
	
	/** Suggest an alternative dictionary to view or return null
	 */
	public Book getSuggestedGenBook() {
		CurrentPageManager currentPageManager = ControlFactory.getInstance().getCurrentPageControl();
		Book currentBook = currentPageManager.getCurrentGeneralBook().getCurrentDocument();
		return getSuggestedBook(SwordDocumentFacade.getInstance().getBooks(BookCategory.GENERAL_BOOK), currentBook, null, currentPageManager.isGenBookShown());
	}

	/** Suggest an alternative map to view or return null
	 */
	public Book getSuggestedMap() {
		CurrentPageManager currentPageManager = ControlFactory.getInstance().getCurrentPageControl();
		Book currentBook = currentPageManager.getCurrentMap().getCurrentDocument();
		return getSuggestedBook(SwordDocumentFacade.getInstance().getBooks(BookCategory.MAPS), currentBook, null, currentPageManager.isMapShown());
	}

	/** possible books will often not include the current verse but most will include chap 1 verse 1
	 */
	private ConvertibleVerse getRequiredVerseForSuggestions() {
		Verse currentVerse = ControlFactory.getInstance().getCurrentPageControl().getCurrentBible().getSingleKey();
		return new ConvertibleVerse(currentVerse.getBook(), 1, 1);
	}

	/** Suggest an alternative document to view or return null
	 * 
	 * @return
	 */
	private Book getSuggestedBook(List<Book> books, Book currentDocument, Filter<Book> filter, boolean isBookTypeShownNow) {
		Book suggestion = null;
		if (!isBookTypeShownNow) {
			// allow easy switch back to current doc
			suggestion = currentDocument;
		} else {
			// only suggest alternative if more than 1
			if (books.size()>1) {
				// find index of current document
				int currentDocIndex = -1;
				for (int i=0; i<books.size(); i++) {
					if (books.get(i).equals(currentDocument)) {
						currentDocIndex = i;
					}
				}
				
				// find the next doc containing related content e.g. if in NT then don't show TDavid
				for (int i=0; i<books.size()-1 && suggestion==null; i++) {
					Book possibleDoc = books.get((currentDocIndex+i+1)%books.size());
					
					if (filter==null || filter.test(possibleDoc)) {
						 suggestion = possibleDoc;
					}
				}
			}
		}
		
		return suggestion;
	}
}
