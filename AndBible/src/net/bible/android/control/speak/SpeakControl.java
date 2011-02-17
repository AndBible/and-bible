package net.bible.android.control.speak;

import java.util.Locale;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.device.TextToSpeechController;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.passage.Key;

import android.util.Log;

public class SpeakControl {
	
	private static final String TAG = "SpeakControl";
	
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
				Key current = page.addPages(i);
				textToSpeak.append( SwordApi.getInstance().getCanonicalText(page.getCurrentDocument(), current));
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
		}
		
		// if repeat was checked then concatenate with itself
		if (repeat) {
			String repeating = BibleApplication.getApplication().getString(R.string.repeating);
			textToSpeak.append(" ").append(repeating).append(" ").append(textToSpeak);
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
