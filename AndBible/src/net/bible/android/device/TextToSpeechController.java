package net.bible.android.device;

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
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.

 */
public class TextToSpeechController implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener, AppToBackgroundListener {

    private static final String TAG = "TextToSpeechController";

    private TextToSpeech mTts;

    private List<Locale> localePreferenceList;
    private SpeakTextProvider mSpeakTextProvider;

    private TTSLanguageSupport ttsLanguageSupport = new TTSLanguageSupport();
    
    private Context context;

    private long uniqueUtteranceNo = 0;
    private String mLatestUtteranceId = "";
    // tts.isSpeaking() returns false when multiple text is queued on some older versions of Android so maintain it manually
    private boolean isSpeaking = false;
    
    private static final TextToSpeechController singleton = new TextToSpeechController();
    
    public static TextToSpeechController getInstance() {
    	return singleton;
    }
    
    private TextToSpeechController() {
    	Log.d(TAG, "Creating TextToSpeechController");
    	context = BibleApplication.getApplication().getApplicationContext();
    	CurrentActivityHolder.getInstance().addAppToBackgroundListener(this);
    	mSpeakTextProvider = new SpeakTextProvider();
    }

    public boolean isLanguageAvailable(String langCode) {
    	return ttsLanguageSupport.isLangKnownToBeSupported(langCode);
    }

    public void speak(List<Locale> localePreferenceList, String textToSpeak, boolean queue) {
    	Log.d(TAG, "speak string of length:"+textToSpeak.length()+(queue?" queued":""));
   		if (!queue) {
   			Log.d(TAG, "Queue is false so requesting stop");
   			stop();
   		}
   		mSpeakTextProvider.addTextToSpeak(textToSpeak);

    	if (mTts==null) {
        	Log.d(TAG, "mTts was null so initialising Tts");

    		// currently can't change Locale until speech ends
        	this.localePreferenceList = localePreferenceList;
	    	try {
		        // Initialize text-to-speech. This is an asynchronous operation.
		        // The OnInitListener (second argument) is called after initialization completes.
		        mTts = new TextToSpeech(context,
		            this  // TextToSpeech.OnInitListener
		            );
	    	} catch (Exception e) {
	    		Log.e(TAG,  "Error initialising Tts", e);
	    		showError(R.string.error_occurred);
	    	}
    	} else {
   			speakAllText();
    	}
    }

    // Implements TextToSpeech.OnInitListener.
    @Override
    public void onInit(int status) {
    	Log.d(TAG, "Tts initialised");

        // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
        if (status == TextToSpeech.SUCCESS) {
        	Log.d(TAG, "Tts initialisation succeeded");
        	boolean localeOK = false;
        	Locale locale = null;
        	for (int i=0; i<localePreferenceList.size() && !localeOK; i++) {
        		locale = localePreferenceList.get(i);
        		Log.d(TAG, "Checking for locale:"+locale);
        		int result = mTts.setLanguage(locale);
        		localeOK = ((result != TextToSpeech.LANG_MISSING_DATA) && (result != TextToSpeech.LANG_NOT_SUPPORTED));
        		if (localeOK) {
        			Log.d(TAG, "Successful locale:"+locale);
        		}
        	}
        		
            if (!localeOK) {
    	    	Log.e(TAG, "TTS missing or not supported");
               // Language data is missing or the language is not supported.
    	    	ttsLanguageSupport.addUnsupportedLocale(locale);
                showError(R.string.tts_lang_not_available);
            } else {
                // The TTS engine has been successfully initialized.
    	    	ttsLanguageSupport.addSupportedLocale(locale);
            	int ok = mTts.setOnUtteranceCompletedListener(this);
            	if (ok==TextToSpeech.ERROR) {
            		Log.e(TAG, "Error registering onUtteranceCompletedListener");
            	}
            	
            	// say the text
           		speakAllText();
            }
        } else {
        	Log.d(TAG, "Tts initialisation failed");
            // Initialization failed.
            showError(R.string.error_occurred);
        }
    }

    private void speakAllText() {
    	Log.d(TAG, "about to send all text to TTS");
        // ask TTs to say the text
    	while (mSpeakTextProvider.isMoreTextToSpeak()) {
    		String text = mSpeakTextProvider.getNextTextToSpeak();
    		speakString(text);
    	}
    	mSpeakTextProvider.reset();
    }

    private void speakString(String text) {
    	// Always set the UtteranceId (or else OnUtteranceCompleted will not be called)
        HashMap<String, String> dummyTTSParams = new HashMap<String, String>();
        String utteranceId = "AND-BIBLE-"+uniqueUtteranceNo++;
        dummyTTSParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

    	Log.d(TAG, "do speak substring of length:"+text.length()+" utteranceId:"+utteranceId);
        mTts.speak(text,
                TextToSpeech.QUEUE_ADD, // handle flush by clearing text queue 
                dummyTTSParams);
        
        mLatestUtteranceId = utteranceId;
        isSpeaking = true;
        Log.d(TAG, "Speaking:"+text);
    }
    private void showError(int msgId) {
    	Dialogs.getInstance().showErrorMsg(msgId);
    }

	public void stop() {
    	Log.d(TAG, "Stop TTS");
		
        // Don't forget to shutdown!
        if (isSpeaking()) {
        	Log.d(TAG, "Flushing speech");
        	// flush remaining text
	        mTts.speak(" ", TextToSpeech.QUEUE_FLUSH, null);
        }
        
        mSpeakTextProvider.reset();
        isSpeaking = false;
	}

	@Override
	public void onUtteranceCompleted(String utteranceId) {
		Log.d(TAG, "onUtteranceCompleted:"+utteranceId);
		if (mLatestUtteranceId.equals(utteranceId)) {
			Log.d(TAG, "Shutting down TTS");
			shutdown();
		}
	}

    public void shutdown() {
    	Log.d(TAG, "Shutdown TTS");
		try {
	        // Don't forget to shutdown!
	        if (mTts != null) {
	            mTts.stop();
	            mTts.shutdown();
	            mTts = null;
	        }
		} catch (Exception e) {
			Log.e(TAG, "Error shutting down Tts engine", e);
		} finally {
			mTts = null;
	        isSpeaking = false;
	        mSpeakTextProvider.reset();
		}
    }

	public boolean isSpeaking() {
		return isSpeaking;
	}

	@Override
	public void applicationNowInBackground(AppToBackgroundEvent e) {
		shutdown();		
	}
}
