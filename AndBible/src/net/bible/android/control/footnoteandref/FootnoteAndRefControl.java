package net.bible.android.control.footnoteandref;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.format.Note;

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

	public List<Note> getCurrentPageFootnotesAndReferences() {
		try {
			return ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getCurrentPageFootnotesAndReferences();
		} catch (Exception e) {
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
			return new ArrayList<Note>();
		}
	}
	
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
		return getCurrentPageManager().getCurrentBible().getSingleKey();
	}

	/** go to previous verse
	 */
	public void next() {
		Verse verse = getVerse();
		if (!verse.getVersification().isEndOfChapter(verse)) {
			getCurrentPageManager().getCurrentBible().doNextVerse();
		}
	}
	
	/** go to next verse
	 */
	public void previous() {
		Verse verse = getVerse();
		if (!verse.getVersification().isStartOfChapter(verse)) {
			getCurrentPageManager().getCurrentBible().doPreviousVerse();
		}		
	}
	
	public CurrentPageManager getCurrentPageManager() {
		return ControlFactory.getInstance().getCurrentPageControl();
	}
}
