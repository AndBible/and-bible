package net.bible.android.control.footnoteandref;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BookName;

/** Support the Compare Translations screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class FootnoteAndRefControl {

	@SuppressWarnings("unused")
	private static final String TAG = "FootnoteAndRefControl";

	private CurrentPageManager currentPageManager; // injected
	
	public String getTitle() {
		StringBuilder stringBuilder = new StringBuilder();
		boolean wasFullBookname = BookName.isFullBookName();
		BookName.setFullBookName(false);
		
		stringBuilder.append(BibleApplication.getApplication().getString(R.string.notes))
					 .append(": ")
					 .append(getVerse().getName());
		
		BookName.setFullBookName(wasFullBookname);
		return stringBuilder.toString();
	}
	
	public Verse getVerse() {
		return currentPageManager.getCurrentBible().getSingleKey();
	}

	/** go to previous verse
	 */
	public void next() {
		if (!getVerse().isEndOfChapter()) {
			currentPageManager.getCurrentBible().doNextVerse();
		}
	}
	
	/** go to next verse
	 */
	public void previous() {
		if (!getVerse().isStartOfChapter()) {
			currentPageManager.getCurrentBible().doPreviousVerse();
		}		
	}
	
	public void setCurrentPageManager(CurrentPageManager currentPageManager) {
		this.currentPageManager = currentPageManager;
	}
}
