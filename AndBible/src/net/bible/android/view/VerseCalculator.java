package net.bible.android.view;

import java.util.LinkedList;
import java.util.List;

import net.bible.android.CurrentPassage;
import android.util.Log;
import android.webkit.WebView;

/** Automatically find current verse at top of display to aid quick movement to Commentary.
 * todo: ensure last verse is selectable 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VerseCalculator {

	private int numVerses = 0;
	
	private List<Integer> versePositionList = new LinkedList<Integer>();
	
	private WebView view;
	
	private static final String TAG = "VerseCalculator";

	public VerseCalculator(WebView bibleView) {
		super();
		
		this.view = bibleView;

		// calculate num verses now and whenever the chapter changes
		calculateNumVerses();
	}
	
	public void init() {

		calculateNumVerses();
		
		versePositionList = new LinkedList<Integer>();
	}
	
	public void registerVersePosition(int verse, int offset) {
		assert versePositionList.size()+1==verse : "Verse positions must be calculated in order";
		versePositionList.add(offset);
	}
	
	public void newPosition(int scrollOffset) {
		if (!CurrentPassage.getInstance().isSingleVerse()) {
			int currentVerse = calculateCurrentVerse(scrollOffset);
			CurrentPassage.getInstance().setCurrentVerse(currentVerse);
		}
	}
	
	private int calculateCurrentVerse(int scrollOffset) {
		
		for (int verseIndex=0; verseIndex<versePositionList.size(); verseIndex++) {
			int pos = versePositionList.get(verseIndex);
			if (pos>scrollOffset) {
				CurrentPassage.getInstance().setCurrentVerse(verseIndex+1);
				return verseIndex+1;
			}
		}
		// maybe scrolled off botttom
		return versePositionList.size()+1;
	}

	private void calculateNumVerses() {
		try {
			numVerses = CurrentPassage.getInstance().getNumberOfVersesDisplayed();
		} catch (Exception e) {
			Log.e(TAG, "Error calculating verses number"+e.getMessage());
		}
	}
}
