package net.bible.android.device;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundListener;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.Dialogs;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

/**
 * <p>text-to-speech (TTS). Please note the following steps:</p>
 *
 * <ol>
 * <li>Construct the TextToSpeech object.</li>
 * <li>Handle initialization callback in the onInit method.
 * The activity implements TextToSpeech.OnInitListener for this purpose.</li>
 * <li>Call TextToSpeech.speak to synthesize speech.</li>
 * <li>Shutdown TextToSpeech in onDestroy.</li>
 * </ol>
 *
 * <p>Documentation:
 * http://developer.android.com/reference/android/speech/tts/package-summary.html
 * </p>
 * <ul>
 */
public class TextToSpeechController implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener, AppToBackgroundListener {

    private static final String TAG = "TextToSpeechController";

    private TextToSpeech mTts;

    private Locale speechLocale;
    private List<String> mTextToSpeak = new ArrayList<String>();
    private int currentSentence = 0;
    private boolean queue;

    private Context context;
    
    private static final TextToSpeechController singleton = new TextToSpeechController();
    private static final String END_SENTENCE_PAUSE = ".";
    
    public static TextToSpeechController getInstance() {
    	return singleton;
    }
    
    private TextToSpeechController() {
    	context = BibleApplication.getApplication().getApplicationContext();
    	CurrentActivityHolder.getInstance().addAppToBackgroundListener(this);
    }

    public void speak(Locale speechLocale, String textToSpeak, boolean queue) {
   		this.mTextToSpeak.addAll(Arrays.asList(textToSpeak.split("\\.")));
//    	this.mTextToSpeak.add(textToSpeak);

    	Log.d(TAG, "Num sentences:"+mTextToSpeak.size());
    	
    	this.queue = queue;
    	
    	if (mTts==null) {
    		// currently can't change Locale until speech ends
        	this.speechLocale = speechLocale;
	    	try {
		        // Initialize text-to-speech. This is an asynchronous operation.
		        // The OnInitListener (second argument) is called after initialization completes.
		        mTts = new TextToSpeech(context,
		            this  // TextToSpeech.OnInitListener
		            );
	    	} catch (Exception e) {
	    		showError(R.string.error_occurred);
	    	}
    	} else {
    		sayText();
    	}
    }

    // Implements TextToSpeech.OnInitListener.
    @Override
    public void onInit(int status) {
        // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
        if (status == TextToSpeech.SUCCESS) {
	    	Log.d(TAG, "Speech locale:"+speechLocale);
            int result = mTts.setLanguage(speechLocale);
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
    	    	Log.e(TAG, "TTS missing or not supported ("+result+")");
               // Language data is missing or the language is not supported.
                showError(R.string.tts_lang_not_available);
            } else {
                // The TTS engine has been successfully initialized.
            	int ok = mTts.setOnUtteranceCompletedListener(this);
            	if (ok==TextToSpeech.ERROR) {
            		Log.e(TAG, "Error registering onUtteranceCompletedListener");
            	}
            	
            	// say the text
                sayText();
            }
        } else {
            // Initialization failed.
            showError(R.string.error_occurred);
        }
    }

    private void sayText() {
        // ask TTs to say the text
    	speakNextString();
    }

    private void speakNextString() {
    	// Always set the UtteranceId (or else OnUtteranceCompleted will not be called)
        HashMap<String, String> dummyTTSParams = new HashMap<String, String>();
        dummyTTSParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AND-BIBLE"+System.currentTimeMillis());

        //TODO only add pause if not first string
        mTts.speak(this.mTextToSpeak.get(currentSentence++),
                queue ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH,  // Drop all pending entries in the playback queue.
                dummyTTSParams);

    }
    private void showError(int msgId) {
    	Dialogs.getInstance().showErrorMsg(msgId);
    }

	public void stop() {
		shutdown();
	}

	@Override
	public void onUtteranceCompleted(String utteranceId) {
		Log.d(TAG, "onUtteranceCompleted:"+utteranceId);
		if (currentSentence==mTextToSpeak.size()) {
			Log.d(TAG, "Shutting down TTS");
			shutdown();
			mTextToSpeak.clear();
			currentSentence = 0;
		} else {
			speakNextString();
		}
	}

    private void shutdown() {
    	Log.d(TAG, "Shutdown TTS");
        // Don't forget to shutdown!
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }
    }

	public boolean isSpeaking() {
		Log.d(TAG, "isSpeaking called");
		return mTts!=null && mTts.isSpeaking();
	}

	@Override
	public void applicationNowInBackground(AppToBackgroundEvent e) {
		stop();		
	}
}
