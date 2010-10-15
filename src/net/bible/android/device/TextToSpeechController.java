package net.bible.android.device;

import java.util.HashMap;
import java.util.Locale;

import net.bible.android.activity.R;
import net.bible.android.currentpagecontrol.CurrentPage;
import net.bible.service.sword.SwordApi;
import android.app.AlertDialog;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

/**
 * <p>Demonstrates text-to-speech (TTS). Please note the following steps:</p>
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

    private boolean mIsSpeaking;
    
    private String bookLanguageCode;
    private String textToSpeak;
    
    private Context context;
    
    private static final TextToSpeechController singleton = new TextToSpeechController();
    
    public static TextToSpeechController getInstance() {
    	return singleton;
    }
    
    private TextToSpeechController() {
    }

    public void speak(Context context, CurrentPage passage) {
    	this.context = context;
    	try {
	    	bookLanguageCode = passage.getCurrentDocument().getLanguage().getCode();
	    	textToSpeak = SwordApi.getInstance().getCanonicalText(passage.getCurrentDocument(), passage.getKey());
	    	
	        // Initialize text-to-speech. This is an asynchronous operation.
	        // The OnInitListener (second argument) is called after initialization completes.
	        mTts = new TextToSpeech(context,
	            this  // TextToSpeech.OnInitListener
	            );
    	} catch (Exception e) {
    		showError("Error preparing test to display");
    	}
    }

    public void shutdown() {
    	Log.d(TAG, "Shutdown TTS");
        // Don't forget to shutdown!
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
		mIsSpeaking = false;
    }

    // Implements TextToSpeech.OnInitListener.
    @Override
    public void onInit(int status) {
        // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
        if (status == TextToSpeech.SUCCESS) {
            // Set preferred language to the same language as the book.
            // Note that a language may not be available, and the result will indicate this.
            int result = mTts.setLanguage(new Locale(this.bookLanguageCode));
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
               // Language data is missing or the language is not supported.
                showError(context.getString(R.string.tts_lang_not_available));
            } else {
                // The TTS engine has been successfully initialized.
            	int ok = mTts.setOnUtteranceCompletedListener(this);
            	if (ok==TextToSpeech.ERROR) {
            		Log.e(TAG, "Error registering onUtteranceCompletedListener");
            	}
            	
            	mIsSpeaking = true;
            	
            	// say the text
                sayText();
            }
        } else {
            // Initialization failed.
            showError("Could not initialize TextToSpeech.");
        }
    }

    private void sayText() {
    	// Always set the UtteranceId (or else OnUtteranceCompleted will not be called)
        HashMap dummyTTSParams = new HashMap();
        dummyTTSParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "theUtId");

        // ask TTs to say the text
        mTts.speak(this.textToSpeak,
            TextToSpeech.QUEUE_FLUSH,  // Drop all pending entries in the playback queue.
            dummyTTSParams);
    }
    
    private void showError(String text) {
    	Log.e(TAG, text);
        AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
        builder.setMessage(text);
        builder.setPositiveButton(context.getText(R.string.okay), null);
        builder.show();
    }

	public void stop() {
		shutdown();
	}

	@Override
	public void onUtteranceCompleted(String utteranceId) {
		Log.d(TAG, "onUtteranceCompleted");
		shutdown();
	}

	public boolean isSpeaking() {
		Log.d(TAG, "isSpeaking:"+mIsSpeaking);
		return mIsSpeaking;
	}
}
