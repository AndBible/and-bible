package net.bible.android.currentpagecontrol;


import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.content.SharedPreferences;
import android.util.Log;

abstract class CurrentPageBase implements CurrentPage {

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

	@Override
	public Book getCurrentDocument() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setCurrentDocument(Book currentBible) {
		// TODO Auto-generated method stub

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
