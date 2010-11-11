package net.bible.android.control.document;

import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.book.Book;

public class DocumentControl {
	public boolean canDelete(Book document) {
		return 	document != null && 
				document.getDriver().isDeletable(document) &&
				!document.equals(CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument());
	}
}
