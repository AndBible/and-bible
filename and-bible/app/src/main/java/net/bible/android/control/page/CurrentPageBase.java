package net.bible.android.control.page;


import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.PassageChangeMediator;
import net.bible.service.common.ParseException;
import net.bible.service.format.HtmlMessageFormatter;
import net.bible.service.format.Note;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.common.activate.Activator;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Common functionality for different document page types
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
abstract class CurrentPageBase implements CurrentPage {

	private Book currentDocument;
	
	private boolean inhibitChangeNotifications;
	
	private float currentYOffsetRatio;
	private Key keyWhenYOffsetRatioSet;
	private Book docWhenYOffsetRatioSet;

	private static final String TAG = "CurrentPage";

	// all bibles and commentaries share the same key
	private boolean shareKeyBetweenDocs = false;

	private final SwordContentFacade swordContentFacade;

	private SwordDocumentFacade swordDocumentFacade;

	public abstract void doSetKey(Key key);
	
	protected CurrentPageBase(boolean shareKeyBetweenDocs, SwordContentFacade swordContentFacade, SwordDocumentFacade swordDocumentFacade) {
		super();
		this.shareKeyBetweenDocs = shareKeyBetweenDocs;
		this.swordContentFacade = swordContentFacade;
		this.swordDocumentFacade = swordDocumentFacade;
	}


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

	/** add or subtract a number of pages from the current position and return Page
	 * default is one key per page - all except bible use this default
	 */
	public Key getPagePlus(int num) {
		// If 1 key per page then same as getKeyPlus
		return getKeyPlus(num);
	}

	@Override
	public boolean isSingleKey() {
		return false;
	}

	@Override
	public String getCurrentPageContent() throws ParseException {
        String htmlText = swordContentFacade.readHtmlText(getCurrentDocument(), getKey());
                
        if (StringUtils.isEmpty(htmlText)) {
        	htmlText = HtmlMessageFormatter.format(R.string.error_no_content);
        }
        
        return htmlText;	
	}
	
	@Override
	public List<Note> getCurrentPageFootnotesAndReferences() throws ParseException {
        List<Note> footnotes = swordContentFacade.readFootnotesAndReferences(getCurrentDocument(), getKey());
        
        return footnotes;	
	}


	public boolean checkCurrentDocumentStillInstalled() {
		if (currentDocument!=null) {
			Log.d(TAG, "checkCurrentDocumentStillInstalled:"+currentDocument);
			// this sets currentDoc to null if it does not exist
			currentDocument = swordDocumentFacade.getDocumentByInitials(currentDocument.getInitials());
		}
		return currentDocument!=null;
	}
	
	/* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getCurrentDocument()
	 */
	@Override
	public Book getCurrentDocument() {
		if (currentDocument==null) {
			List<Book> books = swordDocumentFacade.getBooks(getBookCategory());
			if (books.size()>0) {
				currentDocument = books.get(0);
			}
		}
		return currentDocument;
	}

	public boolean isCurrentDocumentSet() {
		return currentDocument!=null;
	}

	/* Set new doc and if possible show new doc
	 * @see net.bible.android.control.CurrentPage#setCurrentDocument(org.crosswire.jsword.book.Book)
	 */
	@Override
	public void setCurrentDocument(Book doc) {
		Log.d(TAG, "Set current doc to "+doc);
		Book prevDoc = currentDocument;
		
		if (!doc.equals(currentDocument) && !shareKeyBetweenDocs && getKey()!=null && !doc.contains(getKey())) {
			doSetKey(null);
		}
		
		localSetCurrentDocument(doc);
		
		// try to clear memory to prevent OutOfMemory errors
		if (!currentDocument.equals(prevDoc)) {
			Activator.deactivate(prevDoc);
		}
	}
	
	/* Set new doc and if possible show new doc
	 * @see net.bible.android.control.CurrentPage#setCurrentDocument(org.crosswire.jsword.book.Book)
	 */
	@Override
	public void setCurrentDocumentAndKey(Book doc, Key key) {
		doSetKey(key);
		localSetCurrentDocument(doc);
	}
	
	protected void localSetCurrentDocument(Book doc) {
		this.currentDocument = doc;
	}

	@Override
	public void updateOptionsMenu(Menu menu) {
		// these are fine for Bible and commentary
		MenuItem menuItem = menu.findItem(R.id.searchButton);
		if (menuItem!=null) {
			menuItem.setEnabled(isSearchable());
		}
		menuItem = menu.findItem(R.id.bookmarksButton);
		if (menuItem!=null) {
			menuItem.setEnabled(true);
		}
		menuItem = menu.findItem(R.id.speakButton);
		if (menuItem!=null) {
			menuItem.setEnabled(isSpeakable());
		}
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
	public void restoreState(JSONObject jsonObject) throws JSONException {
		if (jsonObject!=null) {
			Log.d(TAG, "Restoring page state");
			if (jsonObject.has("document")) {
				String document = jsonObject.getString("document");
				if (StringUtils.isNotEmpty(document)) {
					Log.d(TAG, "State document:"+document);
					Book book = swordDocumentFacade.getDocumentByInitials(document);
					if (book!=null) {
						Log.d(TAG, "Restored document:"+book.getName());
						// bypass setter to avoid automatic notifications
						localSetCurrentDocument(book);
						
						try {
							if (jsonObject.has("key")) {
								String keyName = jsonObject.getString("key");
								if (StringUtils.isNotEmpty(keyName)) {
									doSetKey(book.getKey(keyName));
									Log.d(TAG, "Restored key:"+keyName);
								}
							}
						} catch (Exception e) {
							Log.e(TAG, "Error restoring key for document category:"+getBookCategory().getName());
						}
					}
				}
			}
		}
	}

	/** called during app close down to save state
	 */
	@Override
	public JSONObject getStateJson() throws JSONException {
		JSONObject object = new JSONObject();
		if (currentDocument!=null) {
			Log.d(TAG, "Getting json state for "+getBookCategory().getName());
			object.put("document", getCurrentDocument().getInitials());
			
			if (this.getKey()!=null) {
				object.put("key", getKey().getOsisID());
			}
		}
		return object;
	}

	public boolean isShareKeyBetweenDocs() {
		return shareKeyBetweenDocs;
	}

	/** can we enable the main menu Speak button 
	 */
	@Override
	public boolean isSpeakable() {
		return true;
	}

	/** how far down the page was the user - allows Back to go to correct line on non-Bible pages (Bibles use verse number for positioning)
	 */
	public float getCurrentYOffsetRatio() {
		try {
			// if key has changed then offsetRatio must be reset because user has changed page
			if (getKey()==null || !getKey().equals(keyWhenYOffsetRatioSet) || !getCurrentDocument().equals(docWhenYOffsetRatioSet)) {
				currentYOffsetRatio = 0;
			}
		} catch (Exception e) {
			// cope with occasional NPE thrown by above if statement
			// just pretend we are at the top of the page if error occurs
			currentYOffsetRatio = 0;
			Log.w(TAG, "NPE getting currentYOffsetRatio");
		}
		return currentYOffsetRatio;
	}
	public void setCurrentYOffsetRatio(float currentYOffsetRatio) {
		this.docWhenYOffsetRatioSet = getCurrentDocument();
		this.keyWhenYOffsetRatioSet = getKey();
		
		this.currentYOffsetRatio = currentYOffsetRatio;
	}

	public SwordDocumentFacade getSwordDocumentFacade() {
		return swordDocumentFacade;
	}
}