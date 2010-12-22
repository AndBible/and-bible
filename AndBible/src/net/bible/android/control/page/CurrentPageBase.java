package net.bible.android.control.page;


import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.PassageChangeMediator;
import net.bible.service.sword.SwordApi;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;

abstract class CurrentPageBase implements CurrentPage {

	private Book currentDocument;
	
	private boolean inhibitChangeNotifications;

	private static final String TAG = "CurrentPage";
	
	abstract protected void doSetKey(Key key);
	
	/** notify mediator that page has changed and a lot of things need to update themselves
	 */
	protected void beforePageChange() {
		if (!isInhibitChangeNotifications()) {
			PassageChangeMediator.getInstance().onBeforeCurrentPageChanged();
		}
	}
	/** notify mediator that page has changed and a lot of things need to update themselves
	 */
	protected void pageChange() {
		if (!isInhibitChangeNotifications()) {
			PassageChangeMediator.getInstance().onCurrentPageChanged();
		}
	}

	/** notify mediator that a detail - normally just verse no - has changed and the title need to update itself
	 */
	protected void pageDetailChange() {
		if (!isInhibitChangeNotifications()) {
			PassageChangeMediator.getInstance().onCurrentPageDetailChanged();
		}
	}

	
	/** displayed in titlebar
	 */
	@Override
	public String getKeyDescription() {
		StringBuffer desc = new StringBuffer();
		Book book = getCurrentDocument();
		if (book!=null) {
			desc.append(book.getInitials());
		}
		Key key = getKey();
		if (key!=null) {
			desc.append(" ").append(key.getName());
		}
		return desc.toString();
	}
	
	@Override
	public Key getSingleKey() {
		// by default do not handle single key requirement - it is really just for bibles
		return getKey();
	}

	@Override
	public void setKey(Key key) {
		beforePageChange();
		doSetKey(key);
		pageChange();
	}
	
	@Override
	public void next() {
	}

	@Override
	public void previous() {
	}

	@Override
	public boolean isSingleKey() {
		return false;
	}

	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getCurrentDocument()
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
	 * @see net.bible.android.control.CurrentPage#setCurrentDocument(org.crosswire.jsword.book.Book)
	 */
	@Override
	public void setCurrentDocument(Book doc) {
		if (getKey()!=null && !doc.equals(currentDocument) && !doc.contains(getKey())) {
			doSetKey(null);
		}
		localSetCurrentDocument(doc);
	}
	/* Set new doc and if possible show new doc
	 * @see net.bible.android.control.CurrentPage#setCurrentDocument(org.crosswire.jsword.book.Book)
	 */
	@Override
	public void setCurrentDocumentAndKey(Book doc, Key key) {
		doSetKey(null);
		localSetCurrentDocument(doc);
	}
	
	protected void localSetCurrentDocument(Book doc) {
		this.currentDocument = doc;
	}

	@Override
	public void updateOptionsMenu(Menu menu) {
		// these are fine for Bible and commentary
		menu.findItem(R.id.selectPassageButton).setTitle(R.string.selectPassage);		
		menu.findItem(R.id.searchButton).setEnabled(true);		
	}
	
	@Override
	public void updateContextMenu(Menu menu) {
		// by default disable notes but bible will enable
		menu.findItem(R.id.notes).setVisible(false);		
	}
	
	@Override
	public boolean isInhibitChangeNotifications() {
		return inhibitChangeNotifications;
	}
	
	@Override
	public void setInhibitChangeNotifications(boolean inhibitChangeNotifications) {
		this.inhibitChangeNotifications = inhibitChangeNotifications;
	}


	@Override
	public void restoreState(SharedPreferences inState) {
		if (inState!=null) {
			Log.d(TAG, "State not null");
			String document = inState.getString(getBookCategory().getName()+"_document", null);
			if (StringUtils.isNotEmpty(document)) {
				Log.d(TAG, "State document:"+document);
				Book book = SwordApi.getInstance().getDocumentByInitials(document);
				if (book!=null) {
					Log.d(TAG, "Document:"+book.getName());
					// bypass setter to avoid automatic notifications
					localSetCurrentDocument(book);
				}
			}
		}
	}

	/** called during app close down to save state
	 * 
	 * @param outState
	 */
	@Override
	public void saveState(SharedPreferences outState) {
		if (currentDocument!=null) {
			SharedPreferences.Editor editor = outState.edit();
			editor.putString(getBookCategory().getName()+"_document", getCurrentDocument().getInitials());
			editor.commit();
		}
	}
}
