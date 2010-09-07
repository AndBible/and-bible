package net.bible.android.view;

import java.util.Observable;
import java.util.Observer;

import net.bible.android.CurrentPassage;
import android.util.Log;
import android.webkit.WebView;

/** Automatically 'guess' current verse being read to aid quick movement to Commentary. 
 * this need some refinement:
 * 	due to calc method which starts with verse 1 when v1 is at top 
 * 	low verses may be above screen, and last verse is when high verse 
 * is at bottom so high verses may be off bottom of screen
 * Also headings and long verses cause inaccuracy.
 * 
 * Suggestion - weight verses by length and add some for headings.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VerseCalculator {

	private int maxScrollRange = 0;
	private int numVerses = 0;
	
	private WebView view;
	
	private static final String TAG = "VerseCalculator";

	public VerseCalculator(WebView bibleView) {
		super();
		
		this.view = bibleView;

		// calculate num verses now and whenever the chapter changes
		calculateNumVerses();
	}
	
	public void init(int scrollHeight) {
		int verticalscrollRange = scrollHeight;

		Log.d(TAG, "updating verse calculator:"+verticalscrollRange+" height:"+view.getHeight());
		Log.d(TAG, "setting vertical scroll range:"+verticalscrollRange);
		// the y posn never actually reaches the bottom of the total scroll range it stops one page height up from bottom so need to adjust for that
		int maxScrollRange = verticalscrollRange-view.getHeight();
		setMaxScrollRange(maxScrollRange);

		calculateNumVerses();
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
			Log.e(TAG, "Error calculating verses number"+e.getMessage());
		}
	}

	public void setMaxScrollRange(int maxScrollRange) {
		this.maxScrollRange = maxScrollRange;
	}
	
}
