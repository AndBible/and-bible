package net.bible.android.view.activity.page;

import java.util.LinkedList;
import java.util.List;

import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.control.page.CurrentPageManager;

/** Automatically find current verse at top of display to aid quick movement to Commentary.
 * todo: ensure last verse is selectable 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VerseCalculator {

	private List<Integer> versePositionList = new LinkedList<Integer>();
	
//	private static final String TAG = "VerseCalculator";

	public VerseCalculator() {
		super();
	}
	
	public void init() {
		versePositionList = new LinkedList<Integer>();
	}
	
	/** when a page is displayed js calls this function to recored the position of all verses to enable current verse calculation
	 * 
	 * @param verse
	 * @param offset
	 */
	public void registerVersePosition(int verse, int offset) {
		assert versePositionList.size()+1==verse : "Verse positions must be calculated in order";
		versePositionList.add(offset);
	}
	
	public void newPosition(int scrollOffset) {
		// it is only bibles that have dynamic verse update on scroll
		if (CurrentPageManager.getInstance().getCurrentPage() instanceof CurrentBiblePage) {
			int currentVerse = calculateCurrentVerse(scrollOffset);
			CurrentPageManager.getInstance().getCurrentBible().setCurrentVerseNo(currentVerse);
		}
	}
	
	/** compare scrollOffset to the versePositionList to find which verse is at the top of the screen
	 * 
	 * @param scrollOffset	distance from the top of the screen.
	 * @return
	 */
	private int calculateCurrentVerse(int scrollOffset) {
		
		for (int verseIndex=0; verseIndex<versePositionList.size(); verseIndex++) {
			int pos = versePositionList.get(verseIndex);
			if (pos>scrollOffset) {
				return verseIndex+1;
			}
		}
		// maybe scrolled off botttom
		return versePositionList.size();
	}
}
