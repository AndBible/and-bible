package net.bible.android.control.speak;

import java.util.Locale;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.device.TextToSpeechController;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.passage.Key;

import android.util.Log;

public class SpeakControl {
	
	private static final String TAG = "SpeakControl";

	/** return a list of prompt ids for the speak screen associated with the current document type
	 */
	public int[] getPromptIds() {
		int[] promptIds = new int[1];
		BookCategory bookCategory = CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument().getBookCategory();
		if (BookCategory.BIBLE.equals(bookCategory)) {
			promptIds[0] = R.plurals.num_chapters;
		} else if (BookCategory.COMMENTARY.equals(bookCategory)) {
			promptIds[0] = R.plurals.num_verses;
		} else {
			promptIds[0] = R.plurals.num_pages;
		}
		return promptIds;
	}
	
	public void speak(int chapters, boolean queue, boolean repeat) {
		Log.d(TAG, "Chapters:"+chapters);
		CurrentPage page = CurrentPageManager.getInstance().getCurrentPage();
		
		//calculate locale to use for speech
    	String bookLanguageCode = page.getCurrentDocument().getLanguage().getCode();
        // Set preferred language to the same language as the book.
        // Note that a language may not be available, and the result will indicate this.
    	Log.d(TAG, "Book has language code:"+bookLanguageCode);
    	Locale speechLocale = null;
    	if (bookLanguageCode.equals(Locale.getDefault().getLanguage())) {
    		// for people in UK the UK accent is preferable to the US accent
    		speechLocale = Locale.getDefault();
    	} else {
    		speechLocale = new Locale(bookLanguageCode);
    	}

		// build a string containing the text to be spoken
    	// first concatenate the number of required chapters
		StringBuffer textToSpeak = new StringBuffer();
		try {
			for (int i=0; i<chapters; i++) {
				Key current = page.getPagePlus(i);
				textToSpeak.append( SwordApi.getInstance().getCanonicalText(page.getCurrentDocument(), current));
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
		}
		
		// if repeat was checked then concatenate with itself
		if (repeat) {
			String repeating = BibleApplication.getApplication().getString(R.string.repeating);
			// grab the text now before repeating is appended otherwise 'repeating..' is also appended at the end
			String baseText = textToSpeak.toString();
			textToSpeak.append("\n")
						.append(repeating)
						.append(".\n").append(baseText);
		}

		// speak current chapter or stop speech if already speaking
    	TextToSpeechController tts = TextToSpeechController.getInstance();
		Log.d(TAG, "Tell TTS to say current chapter");
    	tts.speak(speechLocale, textToSpeak.toString(), queue);
	}
	
	public void stop() {
		Log.d(TAG, "Stop TTS speaking");
    	TextToSpeechController tts = TextToSpeechController.getInstance();
		tts.stop();
	}
}
