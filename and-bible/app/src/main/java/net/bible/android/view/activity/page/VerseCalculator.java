package net.bible.android.view.activity.page;

import net.bible.android.control.page.ChapterVerse;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Automatically find current verse at top of display to aid quick movement to Commentary.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VerseCalculator {

	private SortedMap<Integer, ChapterVerse> verseByOffset = new TreeMap<>();

	// going to a verse pushes the offset a couple of pixels past the verse position on large screens i.e. going to Judg 5:11 will show Judg 5:12
	private static final int SLACK_FOR_JUMP_TO_VERSE = 5;

	public void init() {
		verseByOffset = new TreeMap<>();
	}
	
	/**
	 * when a page is displayed js calls this function to recored the position of all verses to enable current verse calculation
	 */
	public void registerVersePosition(ChapterVerse chapterVerse, int offset) {
		if (!verseByOffset.containsKey(offset)) {
			verseByOffset.put(offset, chapterVerse);
		}
	}
	
	/** compare scrollOffset to the verseByOffset to find which verse is at the top of the screen
	 * 
	 * @param scrollOffset    distance from the top of the screen.
	 * @return verse number
	 */
	public ChapterVerse calculateCurrentVerse(int scrollOffset) {
		int adjustedScrollOffset = scrollOffset - SLACK_FOR_JUMP_TO_VERSE;

		for (Map.Entry<Integer, ChapterVerse> entry : verseByOffset.entrySet()) {
			if (entry.getKey() > adjustedScrollOffset) {
				return entry.getValue();
			}
		}
		// maybe scrolled off bottom
		return verseByOffset.get(verseByOffset.lastKey());
	}
}
