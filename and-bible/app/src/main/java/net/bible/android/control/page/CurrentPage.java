package net.bible.android.control.page;

import android.app.Activity;
import android.view.Menu;

import net.bible.service.common.ParseException;
import net.bible.service.format.Note;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public interface CurrentPage {

	String toString();

	BookCategory getBookCategory();

	Class<? extends Activity> getKeyChooserActivity();

	void next();

	void previous();
	
	/** get incremented key according to the type of page displayed - verse, chapter, ...
	 */
	Key getKeyPlus(int num);

	/** add or subtract a number of pages from the current position and return Page
	 */
	Key getPagePlus(int num);

	/** set key without updating screens */
	void doSetKey(Key key);

	/** set key and update screens */
	void setKey(Key key);

	boolean isSingleKey();
	
	// bible and commentary share a key (verse)
	boolean isShareKeyBetweenDocs();

	/** get current key
	 */
	Key getKey();
	
	/** get key for 1 verse instead of whole chapter if bible
	 */
	Key getSingleKey();
	
	Book getCurrentDocument();

	void setCurrentDocument(Book currentBible);

	void setCurrentDocumentAndKey(Book doc, Key key);
	
	boolean checkCurrentDocumentStillInstalled();

	/** get a page to display */
	String getCurrentPageContent();

	/** get footnotes */
	List<Note> getCurrentPageFootnotesAndReferences() throws ParseException;

	void updateOptionsMenu(Menu menu);

	void restoreState(JSONObject state) throws JSONException;

	JSONObject getStateJson() throws JSONException;

	void setInhibitChangeNotifications(boolean inhibitChangeNotifications);

	boolean isInhibitChangeNotifications();

	boolean isSearchable();
	boolean isSpeakable();
	
	//screen offset as a percentage of total height of screen
	float getCurrentYOffsetRatio();
	void setCurrentYOffsetRatio(float currentYOffsetRatio);

}