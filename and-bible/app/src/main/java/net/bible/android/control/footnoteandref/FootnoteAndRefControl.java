package net.bible.android.control.footnoteandref;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.versification.BibleTraverser;
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

	private BibleTraverser bibleTraverser;

	@SuppressWarnings("unused")
	private static final String TAG = "FootnoteAndRefControl";

	public FootnoteAndRefControl(BibleTraverser bibleTraverser) {
		this.bibleTraverser = bibleTraverser;
	}

	public List<Note> getCurrentPageFootnotesAndReferences() {
		try {
			return ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getCurrentPageFootnotesAndReferences();
		} catch (Exception e) {
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e);
			return new ArrayList<Note>();
		}
	}
	
	public String getTitle(Verse verse) {
		StringBuilder stringBuilder = new StringBuilder();
		boolean wasFullBookname = BookName.isFullBookName();
		BookName.setFullBookName(false);
		
		stringBuilder.append(BibleApplication.getApplication().getString(R.string.notes))
					 .append(": ")
					 .append(verse.getName());
		
		BookName.setFullBookName(wasFullBookname);
		return stringBuilder.toString();
	}
	
	/** go to previous verse
	 */
	public Verse next(Verse verse) {
		if (verse.getVersification().isEndOfChapter(verse)) {
			return verse;
		} else {
			return bibleTraverser.getNextVerse(getCurrentPageManager().getCurrentPassageDocument(), verse);
		}
	}
	
	/** go to next verse
	 */
	public Verse previous(Verse verse) {
		if (verse.getVersification().isStartOfChapter(verse)) {
			return verse;
		} else {
			return bibleTraverser.getPrevVerse(getCurrentPageManager().getCurrentPassageDocument(), verse);
		}
	}
	
	public CurrentPageManager getCurrentPageManager() {
		return ControlFactory.getInstance().getCurrentPageControl();
	}
}
