package net.bible.android.view.activity.page;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;

import java.util.LinkedList;
import java.util.List;

/** Automatically find current verse at top of display to aid quick movement to Commentary.
 * todo: ensure last verse is selectable 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VerseCalculator {

	private List<Integer> versePositionList = new LinkedList<>();

	private int prevCurrentVerse = -1;

	// going to a verse pushes the offset a couple of pixels past the verse position on large screens i.e. going to Judg 5:11 will show Judg 5:12
	private static final int SLACK_FOR_JUMP_TO_VERSE = 5;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	public VerseCalculator(ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		super();
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}
	
	public void init() {
		versePositionList = new LinkedList<>();
	}
	
	/** when a page is displayed js calls this function to recored the position of all verses to enable current verse calculation
	 * 
	 * @param verse
	 * @param offset
	 */
	public void registerVersePosition(int verse, int offset) {
		// cope with missing verses
		while (verse>versePositionList.size()) {
			// missed verse but need to put some offset so make it off screen
			// commentaries will have all missing verses except current verse but don't know if it is a commentary
			versePositionList.add(-1000);
		}
		versePositionList.add(offset);
	}
	
	public void newPosition(int scrollOffset) {
		// it is only bibles that have verses and dynamic verse update on scroll
		CurrentPageManager currentPageControl = activeWindowPageManagerProvider.getActiveWindowPageManager();
		if (currentPageControl.isBibleShown()) {
			int currentVerse = calculateCurrentVerse(scrollOffset);
			if (currentVerse!=prevCurrentVerse) {
				currentPageControl.getCurrentBible().setCurrentVerseNo(currentVerse);
			}
			prevCurrentVerse = currentVerse;
		}
	}
	
	/** compare scrollOffset to the versePositionList to find which verse is at the top of the screen
	 * 
	 * @param scrollOffset	distance from the top of the screen.
	 * @return
	 */
	private int calculateCurrentVerse(int scrollOffset) {
		int adjustedScrollOffset = scrollOffset - SLACK_FOR_JUMP_TO_VERSE;
		for (int verseIndex=0; verseIndex<versePositionList.size(); verseIndex++) {
			int pos = versePositionList.get(verseIndex);
			if (pos>adjustedScrollOffset) {
				return verseIndex;
			}
		}
		// maybe scrolled off botttom
		return versePositionList.size()-1;
	}
}
