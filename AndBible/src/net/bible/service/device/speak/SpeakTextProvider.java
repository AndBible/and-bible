package net.bible.service.device.speak;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

/** Keep track of a list of chunks of text being fed to TTS
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SpeakTextProvider {

    private List<String> mTextToSpeak = new ArrayList<String>();
    private int currentSentence = 0;
    
    // Before ICS Android would split up long text for you but since ICS this error occurs:
	//    if (mText.length() >= MAX_SPEECH_ITEM_CHAR_LENGTH) {
	//        Log.w(TAG, "Text too long: " + mText.length() + " chars");
    private static final int MAX_SPEECH_ITEM_CHAR_LENGTH = 4000;
    
    // require DOTALL to allow . to match new lines which occur in books like JOChrist
	private static Pattern BREAK_PATTERN = Pattern.compile(".{100,2000}[a-z]+[.?!][\\s]{1,}+", Pattern.DOTALL);
	
	private static final String TAG = "SpeakTextProvider";

	public void addTextsToSpeak(List<String> textsToSpeak) {
		for (String text : textsToSpeak) {
	   		this.mTextToSpeak.addAll(breakUpText(text));
		}
    	Log.d(TAG, "Total Num blocks in speak queue:"+mTextToSpeak.size());
	}
	
	public boolean isMoreTextToSpeak() {
		return currentSentence<mTextToSpeak.size();
	}
	
	public String getNextTextToSpeak(double fractionAlreadySpoken) {
        String text = getNextTextToSpeak();
        if (fractionAlreadySpoken>0) {
        	Log.d(TAG, "Getting part of text to read.  Fraction:"+fractionAlreadySpoken);
        	text = text.substring((int)(Math.min(1,fractionAlreadySpoken)*text.length()));
        }
        return text;		
	}

	public String getNextTextToSpeak() {
        return mTextToSpeak.get(currentSentence++);
	}
	
	/** current chunk needs to be re-read (at least a fraction of it after pause)
	 */
	public void backOneChunk() {
		currentSentence = Math.max(0, currentSentence-1);
	}
	
	public void reset() {
    	if (mTextToSpeak!=null) {
    		mTextToSpeak.clear();
    	}
		currentSentence = 0;
	}

	/** ICS rejects text longer than 4000 chars so break it up
	 * 
	 */
	private List<String> breakUpText(String text) {
		//
		// first try to split text nicely at the end of sentences
		//
		List<String> chunks1 = new ArrayList<String>();
		
		// is the text short enough to use as is
		if (text.length()<MAX_SPEECH_ITEM_CHAR_LENGTH) {
			chunks1.add(text);
		} else {
			// break up the text at sentence ends
			Matcher matcher = BREAK_PATTERN.matcher(text);
	
			int matchedUpTo = 0;
			while (matcher.find()) {
				int nextEnd = matcher.end();
				chunks1.add(text.substring(matchedUpTo, nextEnd));
				matchedUpTo = nextEnd;
			}
			
			// add on the final part of the text, if there is any
			if (matchedUpTo < text.length()) {
				chunks1.add(text.substring(matchedUpTo));
			}
		}
		
		//
		// If any text is still too long because the regexp was not matched then forcefully split it up
		// All chunks are probably now less than 4000 chars as required by tts but go through again for languages that don't have '. ' at the end of sentences
		//
		List<String> chunks2 = new ArrayList<String>();
		for (String chunk : chunks1) {
			if (chunk.length()<MAX_SPEECH_ITEM_CHAR_LENGTH) {
				chunks2.add(chunk);
			} else {
				// force chunks to be correct length -10 is just to allow a bit of extra room
				chunks2.addAll(splitEqually(chunk, MAX_SPEECH_ITEM_CHAR_LENGTH-10));
			}
		}

		return chunks2;
	}
	
	public static List<String> splitEqually(String text, int size) {
	    // Give the list the right capacity to start with. You could use an array
	    // instead if you wanted.
	    List<String> ret = new ArrayList<String>((text.length() + size - 1) / size);

	    for (int start = 0; start < text.length(); start += size) {
	        ret.add(text.substring(start, Math.min(text.length(), start + size)));
	    }
	    return ret;
	}
//	private List<String> nonREbreakUpText(String text) {
//		List<String> chunks = new ArrayList<String>();
//
//		int matchedUpTo = 0;
//		int count = 0;
//		while (text.length()-matchedUpTo>1000) {
//			int nextEnd = text.indexOf(". ",matchedUpTo+100)+2;
//			if (nextEnd!=-1) {
//				Log.d(TAG, "Match "+(++count)+" from "+matchedUpTo+" to "+nextEnd);
//				chunks.add(text.substring(matchedUpTo, nextEnd));
//				matchedUpTo = nextEnd;
//			}
//		}
//		// add on the final part of the text
//		chunks.add(text.substring(matchedUpTo));
//
//		return chunks;
//	}
//
}
