package net.bible.android.currentpagecontrol;


import java.util.List;

import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.content.SharedPreferences;
import android.util.Log;

abstract class CurrentPageBase implements CurrentPage {

	private Book currentDocument;

	private static final String TAG = "CurrentPage";
	
	/** notify mediator that page has changed and a lot of things need to update themselves
	 */
	protected void pageChange() {
		PassageChangeMediator.getInstance().onCurrentPageChanged();
	}
	/** notify mediator that a detail - normally just verse no - has changed and the title need to update itself
	 */
	protected void pageDetailChange() {
		PassageChangeMediator.getInstance().onCurrentPageDetailChanged();
	}

	/** displayed in titlebar
	 */
	@Override
	public String getKeyDescription() {
		return getCurrentDocument().getInitials()+" "+getKey().getName();
	}

	@Override
	public void setKey(Key key) {
		doSetKey(key);
		pageChange();
	}
	
	abstract protected void doSetKey(Key key);
	
	@Override
	public void next() {
		
	}

	@Override
	public void previous() {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isSingleKey() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Key getKey() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see net.bible.android.currentpagecontrol.CurrentPage#getCurrentDocument()
	 */
	@Override
	public Book getCurrentDocument() {
		if (currentDocument==null) {
			List<Book> books = SwordApi.getInstance().getBooks(getBookCategory());
			if (books.size()>0) {
				currentDocument = books.get(0);
			}
		}
		return currentDocument;
	}


	/* Set new doc and if possible show new doc
	 * @see net.bible.android.currentpagecontrol.CurrentPage#setCurrentDocument(org.crosswire.jsword.book.Book)
	 */
	@Override
	public void setCurrentDocument(Book doc) {
		if (getKey()!=null && !doc.equals(currentDocument) && !doc.contains(getKey())) {
			setKey(null);
		}
		localSetCurrentDocument(doc);
		
		// not yet because we currently always go to the index first and pick a key at which point a refresh will occur
		if (getKey()!=null) {
			pageChange();
		}
	}
	
	protected void localSetCurrentDocument(Book doc) {
		this.currentDocument = doc;
	}

	@Override
	public void restoreState(SharedPreferences inState) {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveState(SharedPreferences outState) {
		// TODO Auto-generated method stub

	}
}
