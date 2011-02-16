package net.bible.android.control.speak;

import net.bible.android.BibleApplication;
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
		StringBuffer textToSpeak = new StringBuffer();
		try {
			for (int i=0; i<chapters; i++) {
				Key current = page.addPages(i);
				Log.d(TAG, "i="+i+" current="+current.getName());
				textToSpeak.append( SwordApi.getInstance().getCanonicalText(page.getCurrentDocument(), current));
				Log.d(TAG, "text="+textToSpeak.toString());
			}
			Log.d(TAG, "final text="+textToSpeak.toString());
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
		}
		
		if (repeat) {
			textToSpeak.append(textToSpeak);
		}
    	// speak current chapter or stop speech if already speaking
    	TextToSpeechController tts = TextToSpeechController.getInstance();
		Log.d(TAG, "Tell TTS to say current chapter");
    	tts.speak(BibleApplication.getApplication().getApplicationContext(), page, textToSpeak.toString());
	}
	
	public void stop() {
		Log.d(TAG, "Stop TTS speaking");
    	TextToSpeechController tts = TextToSpeechController.getInstance();
		tts.stop();
	}
}
