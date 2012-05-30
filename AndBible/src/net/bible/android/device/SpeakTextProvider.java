package net.bible.android.device;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

/** this was intended to support pause, FF, rew but that is not yet implemented
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SpeakTextProvider {

    private List<String> mTextToSpeak = new ArrayList<String>();
    private int currentSentence = 0;

	private static Pattern BREAK_PATTERN = Pattern.compile(".{100,2000}[a-z][.?!][ ]{1,}");
	
	private static final String TAG = "SpeakTextProvider";

	public void addTextToSpeak(String textToSpeak) {
   		this.mTextToSpeak.addAll(breakUpText(textToSpeak));

    	Log.d(TAG, "Num sentences:"+mTextToSpeak.size());

	}
	
	public boolean isMoreTextToSpeak() {
		return currentSentence<mTextToSpeak.size();
	}
	
	public String getNextTextToSpeak() {
        String text = mTextToSpeak.get(currentSentence++);
        return text;		
	}
	
	public void reset() {
    	if (mTextToSpeak!=null) {
    		mTextToSpeak.clear();
    	}
		currentSentence = 0;
	}

	private List<String> breakUpText(String text) {
		List<String> chunks = new ArrayList<String>();
		Matcher matcher = BREAK_PATTERN.matcher(text);

		int matchedUpTo = 0;
		while (matcher.find()) {
			// -1 because the pattern includes a char after the last space
			int nextEnd = matcher.end();
			chunks.add(text.substring(matchedUpTo, nextEnd));
			matchedUpTo = nextEnd;
		}
		// add on the final part of the text
		chunks.add(text.substring(matchedUpTo));

		return chunks;
	}

}
