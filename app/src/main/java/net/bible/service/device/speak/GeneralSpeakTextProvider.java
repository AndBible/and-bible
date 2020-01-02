/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.device.speak;

import android.content.SharedPreferences;
import android.util.Log;

import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.speak.SpeakSettings;
import net.bible.android.control.speak.SpeakSettingsChangedEvent;
import net.bible.service.common.AndRuntimeException;
import net.bible.service.common.CommonUtils;

import net.bible.service.device.speak.event.SpeakProgressEvent;
import net.bible.service.sword.SwordContentFacade;
import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keep track of a list of chunks of text being fed to TTS
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class GeneralSpeakTextProvider implements SpeakTextProvider {

	private final SwordContentFacade swordContentFacade;
    private List<String> mTextToSpeak = new ArrayList<String>();
    private int nextTextToSpeak = 0;
    // this fraction supports pause/rew/ff; if o then speech occurs normally, if 0.5 then next speech chunk is half completed...
    private float fractionOfNextSentenceSpoken = 0;
    private String currentText = "";
    
    // Before ICS Android would split up long text for you but since ICS this error occurs:
	//    if (mText.length() >= MAX_SPEECH_ITEM_CHAR_LENGTH) {
	//        Log.w(TAG, "Text too long: " + mText.length() + " chars");
    private static final int MAX_SPEECH_ITEM_CHAR_LENGTH = 4000;
    
    // require DOTALL to allow . to match new lines which occur in books like JOChrist
	@SuppressWarnings("Annotator")
	private static Pattern BREAK_PATTERN = Pattern.compile(".{100,2000}[a-z]+[.?!][\\s]{1,}+", Pattern.DOTALL);
	private Book book = null;
	private List<Key> keyList = null;

	@Override
	public void startUtterance(@NotNull String utteranceId) {
		if (keyList != null && keyList.size() > 0) {
			ABEventBus.getDefault().post(new SpeakProgressEvent(book, keyList.get(0),
					new TextCommand(currentText, TextCommand.TextType.NORMAL)));
			ABEventBus.getDefault().post(new SpeakProgressEvent(book, keyList.get(0),
					new TextCommand(book.getName(), TextCommand.TextType.TITLE)));
		}
	}

	@Override
	public int getNumItemsToTts() {
		return 1;
	}

	@NotNull
	@Override
	public String getStatusText(int showFlag) {
		if(keyList != null && keyList.size() > 0) {
			return keyList.get(0).getName();
		}
		else {
			return "";
		}
	}

	@Override
	public void updateSettings(@NotNull SpeakSettingsChangedEvent speakSettingsChangedEvent) {

	}

	@Nullable
	@Override
	public Verse getCurrentlyPlayingVerse() {
		return null;
	}

	@Nullable
	@Override
	public Book getCurrentlyPlayingBook() {
		return null;
	}

	@Override
	public void setSpeaking(boolean isSpeaking) {

	}

	@Override
	public boolean isSpeaking() {
		return false;
	}

	private static class StartPos {
		boolean found = false;
		private int startPosition = 0;
		private String text = "";
		private float actualFractionOfWhole = 1;
	}
	
	// enable state to be persisted if paused for a long time
	private static final String PERSIST_SPEAK_TEXT = "SpeakText";
	private static final String PERSIST_SPEAK_TEXT_SEPARATOR = "XXSEPXX";
	private static final String PERSIST_NEXT_TEXT = "NextText";
	private static final String PERSIST_FRACTION_SPOKEN = "FractionSpoken";
	
	private static final String TAG = "Speak";

	GeneralSpeakTextProvider(SwordContentFacade swordContentFacade) {
		this.swordContentFacade = swordContentFacade;
	}

	private void setupReading(List<String> textsToSpeak) {
		for (String text : textsToSpeak) {
	   		this.mTextToSpeak.addAll(breakUpText(text));
		}
    	Log.d(TAG, "Total Num blocks in speak queue:"+mTextToSpeak.size());
	}

	void setupReading(Book book, List<Key> keyList, boolean repeat) {
		this.book = book;
		Log.d(TAG, "Keys:"+keyList.size());
		// build a string containing the text to be spoken
		List<String> textToSpeak = new ArrayList<>();
		this.keyList = keyList;
		// first concatenate the number of required chapters
		try {
			for (Key key : keyList) {
				// intro
				textToSpeak.add(key.getName()+". ");

				// content
				textToSpeak.add( swordContentFacade.getTextToSpeak(book, key));

				// add a pause at end to separate passages
				textToSpeak.add("\n");
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
			throw new AndRuntimeException("Error preparing Speech", e);
		}

		// if repeat was checked then concatenate with itself
		if (repeat) {
			textToSpeak.add("\n");
			textToSpeak.addAll(textToSpeak);
		}

		// speak current chapter or stop speech if already speaking
   		setupReading(textToSpeak);
	}

	public boolean isMoreTextToSpeak() {
		//TODO: there seems to be an occasional problem when using ff/rew/pause in the last chunk
		return nextTextToSpeak<mTextToSpeak.size();
	}

	@NotNull
	@Override
	public SpeakCommand getNextSpeakCommand(@NotNull String utteranceId, boolean isCurrent) {
        String text = getNextTextChunk();
        
        // if a pause occurred then skip the first part
        if (fractionOfNextSentenceSpoken>0) {
        	Log.d(TAG, "Getting part of text to read.  Fraction:"+fractionOfNextSentenceSpoken);

        	StartPos textFraction = getPrevTextStartPos(text, fractionOfNextSentenceSpoken);
        	if (textFraction.found) {
	        	fractionOfNextSentenceSpoken = textFraction.actualFractionOfWhole;
	        	text = textFraction.text;
        	} else {
        		Log.e(TAG, "Eror finding next text. fraction:"+fractionOfNextSentenceSpoken);
        		// try to prevent recurrence of error, but do not say anything
        		fractionOfNextSentenceSpoken = 0;
        		text = "";
        	}
        }
        currentText = text;
        return new TextCommand(text, TextCommand.TextType.NORMAL);
	}

	@NotNull
	@Override
	public String getText(@NotNull String utteranceId) {
		return currentText;
	}


	private String getNextTextChunk() {
		String text = peekNextTextChunk();
		nextTextToSpeak++;
        return text;
	}
	private String peekNextTextChunk() {
		if (!isMoreTextToSpeak()) {
			Log.e(TAG, "Error: passed end of Speaktext.  nextText:"+nextTextToSpeak+" textToSpeak size:"+mTextToSpeak.size());
			return "";
		}
        return mTextToSpeak.get(nextTextToSpeak);
	}
	
	/** fractionCompleted may be a fraction of a fraction of the current block if this is not the first pause in this block
	 * 
	 * @param fractionCompleted of last block of text returned by getNextSpeakCommand
	 */
	public void savePosition(float fractionCompleted) {
		Log.d(TAG, "Pause CurrentSentence:"+nextTextToSpeak);

        // accumulate these fractions until we reach the end of a chunk of text
        // if pause several times the fraction of text completed becomes a fraction of the fraction left i.e. 1-previousFractionCompleted
        // also ensure the fraction is never greater than 1/all text
        fractionOfNextSentenceSpoken += Math.min(1, 
        								((1.0-fractionOfNextSentenceSpoken)*fractionCompleted));
        Log.d(TAG, "Fraction of current sentence spoken:"+fractionOfNextSentenceSpoken);

        backOneChunk();
	}

	public void pause() {}

	public void stop(boolean doNotSync) {
		reset();
	}

	public void rewind(SpeakSettings.RewindAmount amount) {
		// go back to start of current sentence
    	StartPos textFraction = getPrevTextStartPos(peekNextTextChunk(), fractionOfNextSentenceSpoken);

    	// if could not find a previous sentence end
    	if (!textFraction.found) {
    		if (backOneChunk()) {
    			textFraction = getPrevTextStartPos(peekNextTextChunk(), 1.0f);
    		}
		} else {
			// go back a little bit further in the current chunk
			StartPos extraFraction = getPrevTextStartPos(peekNextTextChunk(), getStartPosFraction(textFraction.startPosition, peekNextTextChunk()));
			if (extraFraction.found) {
				textFraction = extraFraction;
			}
		}
    	
    	if (textFraction.found) {
	    	fractionOfNextSentenceSpoken = textFraction.actualFractionOfWhole;
    	} else {
        	Log.e(TAG, "Could not rewind");
    	}

    	Log.d(TAG, "Rewind chunk length start position:"+fractionOfNextSentenceSpoken);
	}

	public void forward(SpeakSettings.RewindAmount amount) {
		Log.d(TAG, "Forward nextText:"+nextTextToSpeak);

		// go back to start of current sentence
    	StartPos textFraction = getForwardTextStartPos(peekNextTextChunk(), fractionOfNextSentenceSpoken);

    	// if could not find the next sentence start
    	if (!textFraction.found && forwardOneChunk()) {
	    	textFraction = getForwardTextStartPos(peekNextTextChunk(), 0.0f);   		
		}
    	
    	if (textFraction.found) {
	    	fractionOfNextSentenceSpoken = textFraction.actualFractionOfWhole;
    	} else {
        	Log.e(TAG, "Could not forward");
    	}

    	Log.d(TAG, "Forward chunk length start position:"+fractionOfNextSentenceSpoken);
	}

	public void finishedUtterance(String utteranceId) {
		// reset pause info as a chunk is now finished and it may have been started using continue
		fractionOfNextSentenceSpoken = 0;
	}
	
	/** current chunk needs to be re-read (at least a fraction of it after pause)
	 */
	private boolean backOneChunk() {
		if (nextTextToSpeak > 0) {
			nextTextToSpeak--;
			return true;
		} else {
			return false;
		}
	}
	
	/** current chunk needs to be re-read (at least a fraction of it after pause)
	 */
	private boolean forwardOneChunk() {
		if (nextTextToSpeak < mTextToSpeak.size()-1) {
			nextTextToSpeak++;
			return true;
		} else {
			return false;
		}
	}

	public void reset() {
    	if (mTextToSpeak!=null) {
    		mTextToSpeak.clear();
    	}
		nextTextToSpeak = 0;
		fractionOfNextSentenceSpoken = 0;
	}
	
	/** save state to allow long pauses
	 */
	public void persistState() {
		if (mTextToSpeak.size()>0) {
			CommonUtils.INSTANCE.getSharedPreferences()
						.edit()
						.putString(PERSIST_SPEAK_TEXT, StringUtils.join(mTextToSpeak, PERSIST_SPEAK_TEXT_SEPARATOR))
						.putInt(PERSIST_NEXT_TEXT, nextTextToSpeak)
						.putFloat(PERSIST_FRACTION_SPOKEN, fractionOfNextSentenceSpoken)
						.apply();
		}
	}

	/** restore state to allow long pauses
	 * 
	 * @return state restored
	 */
	public boolean restoreState() {
		boolean isRestored = false;
		SharedPreferences sharedPreferences = CommonUtils.INSTANCE.getSharedPreferences();
		if (sharedPreferences.contains(PERSIST_SPEAK_TEXT)) {
			mTextToSpeak = new ArrayList<String>(Arrays.asList(sharedPreferences.getString(PERSIST_SPEAK_TEXT, "").split(PERSIST_SPEAK_TEXT_SEPARATOR)));
			nextTextToSpeak = sharedPreferences.getInt(PERSIST_NEXT_TEXT, 0);
			fractionOfNextSentenceSpoken = sharedPreferences.getFloat(PERSIST_FRACTION_SPOKEN, 0);
			clearPersistedState();
			isRestored = true;
		}
		
		return isRestored;
	}
	public void clearPersistedState() {
		CommonUtils.INSTANCE.getSharedPreferences().edit().remove(PERSIST_SPEAK_TEXT)
												.remove(PERSIST_NEXT_TEXT)
												.remove(PERSIST_FRACTION_SPOKEN)
												.apply();
	}

	private StartPos getPrevTextStartPos(String text, float fraction) {
		StartPos retVal = new StartPos();
		
    	int allTextLength = text.length();
    	int nextTextOffset = (int)(Math.min(1,fraction)*allTextLength);
    	
    	BreakIterator breakIterator = BreakIterator.getSentenceInstance();
    	breakIterator.setText(text);
    	int startPos = 0;
    	try {
    		// this can rarely throw an Exception
    		startPos = breakIterator.preceding(nextTextOffset);
    	} catch (Exception e) {
    		Log.e(TAG, "Error finding previous sentence start", e);
    	}
    	retVal.found = startPos>=0;
    	
    	if (retVal.found) {
        	retVal.startPosition = startPos; 
    		
	    	// because we don't return an exact fraction, but go to the beginning of a sentence, we need to update the fractionAlreadySpoken  
	    	retVal.actualFractionOfWhole = ((float)retVal.startPosition)/allTextLength;
	    	
	    	retVal.text = text.substring(retVal.startPosition);
    	}

    	return retVal;
	}
	
	private StartPos getForwardTextStartPos(String text, float fraction) {
		StartPos retVal = new StartPos();
		
    	int allTextLength = text.length();
    	int nextTextOffset = (int)(Math.min(1,fraction)*allTextLength);
    	
    	BreakIterator breakIterator = BreakIterator.getSentenceInstance();
    	breakIterator.setText(text);
    	int startPos = 0; 
    	try {
    		// this can rarely throw an Exception
    		startPos = breakIterator.following(nextTextOffset);
    	} catch (Exception e) {
    		Log.e(TAG, "Error finding next sentence start", e);
    	}
    	retVal.found = startPos>=0;
    	
    	if (retVal.found) {
    		// nudge the startPos past the beginning of sentence so this sentence start is found when searching for previous block in getNextSentence
        	retVal.startPosition = startPos<text.length()-1-1? startPos+1 : startPos;
        	
	    	// because we don't return an exact fraction, but go to the beginning of a sentence, we need to update the fractionAlreadySpoken  
	    	retVal.actualFractionOfWhole = ((float)retVal.startPosition)/allTextLength;
	    	
	    	retVal.text = text.substring(retVal.startPosition);
    	}
    	return retVal;
	}

	/** ICS rejects text longer than 4000 chars so break it up
	 * 
	 */
	private List<String> breakUpText(String text) {
		//
		// first try to split text nicely at the end of sentences
		//
		List<String> chunks1 = new ArrayList<>();
		
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
		List<String> chunks2 = new ArrayList<>();
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
	
	private List<String> splitEqually(String text, int size) {
	    // Give the list the right capacity to start with. You could use an array instead if you wanted.
	    List<String> ret = new ArrayList<>((text.length() + size - 1) / size);

	    for (int start = 0; start < text.length(); start += size) {
	        ret.add(text.substring(start, Math.min(text.length(), start + size)));
	    }
	    return ret;
	}
	
	private float getStartPosFraction(int startPos, String text) {
		float startFraction = ((float)startPos)/text.length();
		
		// ensure fraction is between 0 and 1
		startFraction = Math.max(0, startFraction);
		startFraction = Math.min(1, startFraction);
		
		return startFraction;
	}
	
	public long getTotalChars() {
		long totChars = 0;
		for (String chunk: mTextToSpeak) {
			totChars += chunk.length();
		}
		return totChars;
	}
	/** this relies on fraction which is set at pause
	 */
	public long getSpokenChars() {
		long spokenChars = 0;
		if (mTextToSpeak.size()>0) {
			for (int i=0; i<nextTextToSpeak-1; i++) {
				String chunk = mTextToSpeak.get(i);
				spokenChars += chunk.length();
			}
			
			if (nextTextToSpeak<mTextToSpeak.size()) {
				spokenChars += fractionOfNextSentenceSpoken * (float)mTextToSpeak.get(nextTextToSpeak).length();
			}
		}		
		return spokenChars;
	}

	@Override
	public void prepareForStartSpeaking() {}
}
