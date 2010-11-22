package net.bible.android.control.document;

import java.util.List;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;

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
		Key requiredVerse = getRequiredVerseForSuggestions();
		return getSuggestedBook(SwordApi.getInstance().getBibles(), currentBible, requiredVerse, currentPageManager.isBibleShown());
	}

	/** Suggest an alternative commentary to view or return null
	 * 
	 * @return
	 */
	public Book getSuggestedCommentary() {
		CurrentPageManager currentPageManager = ControlFactory.getInstance().getCurrentPageControl();
		Book currentCommentary = currentPageManager.getCurrentCommentary().getCurrentDocument();
		Key requiredVerse = getRequiredVerseForSuggestions();
		return getSuggestedBook(SwordApi.getInstance().getBooks(BookCategory.COMMENTARY), currentCommentary, requiredVerse, currentPageManager.isCommentaryShown());
	}
	
	/** possible books will often not include the current verse but most will include chap 1 verse 1
	 */
	private Key getRequiredVerseForSuggestions() {
		Key currentVerseKey = ControlFactory.getInstance().getCurrentPageControl().getCurrentBible().getSingleKey();
		Verse verse = KeyUtil.getVerse(currentVerseKey);
		return new Verse(verse.getBook(), 1, 1, true);
	}

	/** Suggest an alternative document to view or return null
	 * 
	 * @return
	 */
	private Book getSuggestedBook(List<Book> books, Book currentDocument, Key requiredKey, boolean isBookTypeShownNow) {
		Book suggestion = null;
		if (!isBookTypeShownNow) {
			// allow easy switch back to bible view
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
					if (possibleDoc.contains(requiredKey)) {
						 suggestion = possibleDoc;
					}
				}
			}
		}
		
		return suggestion;
	}
}
