package net.bible.android.device;

import java.util.HashMap;
import java.util.Locale;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
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
public class TextToSpeechController implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener{

    private static final String TAG = "TextToSpeechController";

    private TextToSpeech mTts;

    private Locale speechLocale;
    private String textToSpeak;
    private boolean queue;

    // keep track of the current count of requests so we know when to shutdown Tts
    private int textCount =0;
    
    private Context context;
    
    private static final TextToSpeechController singleton = new TextToSpeechController();
    
    public static TextToSpeechController getInstance() {
    	return singleton;
    }
    
    private TextToSpeechController() {
    	context = BibleApplication.getApplication().getApplicationContext();
    }

    public void speak(Locale speechLocale, String textToSpeak, boolean queue) {
    	this.textToSpeak = textToSpeak;
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
    	// Always set the UtteranceId (or else OnUtteranceCompleted will not be called)
        HashMap dummyTTSParams = new HashMap();
        dummyTTSParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AND-BIBLE"+System.currentTimeMillis());

        this.textCount++;
        
        // ask TTs to say the text
        mTts.speak(this.textToSpeak,
            queue ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH,  // Drop all pending entries in the playback queue.
            dummyTTSParams);
    }
    
    private void showError(int msgId) {
    	Dialogs.getInstance().showErrorMsg(msgId);
    }

	public void stop() {
		textCount = 0;
		shutdown();
	}

	@Override
	public void onUtteranceCompleted(String utteranceId) {
		Log.d(TAG, "onUtteranceCompleted:"+utteranceId);
		if (--textCount==0) {
			shutdown();
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
}
