package net.bible.android.view;

import java.util.Observable;
import java.util.Observer;

import net.bible.android.CurrentPassage;

import org.crosswire.jsword.versification.BibleInfo;

import android.util.Log;

/** Automatically 'guess' current verse being read to aid quick movement to Commentary. 
 * this need some refinement:
 * 	due to calc method which starts with verse 1 when v1 is at top 
 * 	low verses may be above screen, and last verse is when high verse 
 * is at bottom so high verses may be off bottom of screen
 * Also headings and long verses cause inaccuracy.
 * 
 * Suggestion - weight verses by length and add some for headings.
 * 
 * @author denha1m
 *
 */
public class VerseCalculator {

	private int maxScrollRange = 0;
	private int numVerses = 0;
	
	private static final String TAG = "VerseCalculator";

	public VerseCalculator() {
		super();

		// calculate num verses now and whenever the chapter changes
		calculateNumVerses();
		CurrentPassage.getInstance().addObserver(new Observer() {
			@Override
			public void update(Observable arg0, Object arg1) {
				calculateNumVerses();				
			}
		});
	}
	
	public void newPosition(int y) {
		if (maxScrollRange>0 && !CurrentPassage.getInstance().isSingleVerse()) {
			float fractionFromTop = Float.valueOf(y)/Float.valueOf(maxScrollRange);
			System.out.println("perc down:"+fractionFromTop);
			int verse = (int)(Float.valueOf(numVerses-1)*fractionFromTop)+1;
			System.out.println("verse:"+verse);
			CurrentPassage.getInstance().setCurrentVerse(verse);
		}
	}

	private void calculateNumVerses() {
		try {
			Log.d(TAG, "Calculate number of verses");
			numVerses = CurrentPassage.getInstance().getNumberOfVersesDisplayed();
			Log.d(TAG, "Calculated number of verses:"+numVerses);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	public void setMaxScrollRange(int maxScrollRange) {
		this.maxScrollRange = maxScrollRange;
	}
	
}
