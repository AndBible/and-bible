package net.bible.service.device.speak;

import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.phonecall.PhoneCallMonitor;
import net.bible.android.control.event.phonecall.PhoneCallStarted;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.control.versification.BibleTraverser;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.speak.event.SpeakEvent;
import net.bible.service.device.speak.event.SpeakEvent.SpeakState;
import net.bible.service.device.speak.event.SpeakEventManager;

import net.bible.service.sword.SwordContentFacade;
import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;


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
@ApplicationScope
public class TextToSpeechServiceManager {

	private static final String TAG = "Speak";

    private TextToSpeech mTts;

    private List<Locale> localePreferenceList;
    private Locale currentLocale = Locale.getDefault();
	private static String PERSIST_LOCALE_KEY = "SpeakLocale";
    private static String PERSIST_BIBLE_PROVIDER = "SpeakBibleProvider";
    
    private SpeakTextProvider mSpeakTextProvider;

	private GeneralSpeakTextProvider generalSpeakTextProvider;
	private BibleSpeakTextProvider bibleSpeakTextProvider;

    private SpeakTiming mSpeakTiming;

    private TTSLanguageSupport ttsLanguageSupport = new TTSLanguageSupport();
    
    private SpeakEventManager speakEventManager = SpeakEventManager.getInstance();
    
    private static final String UTTERANCE_PREFIX = "AND-BIBLE-";
    private long uniqueUtteranceNo = 0;

    // tts.isSpeaking() returns false when multiple text is queued on some older versions of Android so maintain it manually
    private boolean isSpeaking = false;
    
    private boolean isPaused = false;

	@Inject
    public TextToSpeechServiceManager(SwordContentFacade swordContentFacade, BibleTraverser bibleTraverser,
									  WindowControl windowControl, BookmarkControl bookmarkControl) {
    	Log.d(TAG, "Creating TextToSpeechServiceManager");
		generalSpeakTextProvider = new GeneralSpeakTextProvider(swordContentFacade);
		SwordBook book = (SwordBook) windowControl.getActiveWindowPageManager().getCurrentBible().getCurrentDocument();
		Verse verse = windowControl.getActiveWindowPageManager().getCurrentBible().getSingleKey();

		bibleSpeakTextProvider = new BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl, book, verse);
    	mSpeakTextProvider = bibleSpeakTextProvider;

    	mSpeakTiming = new SpeakTiming();
		ABEventBus.getDefault().safelyRegister(this);
    	restorePauseState();
    }

	public BibleSpeakTextProvider getBibleSpeakTextProvider() {
		return bibleSpeakTextProvider;
	}

    public boolean isLanguageAvailable(String langCode) {
    	return ttsLanguageSupport.isLangKnownToBeSupported(langCode);
    }

	public synchronized void speakBible(SwordBook book, Verse verse) {
		switchProvider(bibleSpeakTextProvider);
		clearTtsQueue();
		bibleSpeakTextProvider.setupReading(book, verse);
		localePreferenceList = calculateLocalePreferenceList(book);
		startSpeakingInitingIfRequired();
	}

	public synchronized void speakText(Book book, List<Key> keyList, boolean queue, boolean repeat) {
		switchProvider(generalSpeakTextProvider);
		generalSpeakTextProvider.setupReading(book, keyList, repeat);
		handleQueue(queue);
		localePreferenceList = calculateLocalePreferenceList(book);
		startSpeakingInitingIfRequired();
    }

	private void switchProvider(SpeakTextProvider newProvider) {
		if(newProvider != mSpeakTextProvider) {
			mSpeakTextProvider.reset();
			mSpeakTextProvider = newProvider;
		}
	}

	private void handleQueue(boolean queue) {
   		if (!queue) {
   			Log.d(TAG, "Queue is false so requesting stop");
   			clearTtsQueue();
   		} else if (isPaused()) {
   			Log.d(TAG, "New speak request while paused so clearing paused speech");
   			clearTtsQueue();
   			isPaused = false;
   		}
	}

	private List<Locale> calculateLocalePreferenceList(Book fromBook) {
		//calculate preferred locales to use for speech
		// Set preferred language to the same language as the book.
		// Note that a language may not be available, and so we have a preference list
		String bookLanguageCode = fromBook.getLanguage().getCode();
		Log.d(TAG, "Book has language code:"+bookLanguageCode);

		List<Locale> localePreferenceList = new ArrayList<>();
		if (bookLanguageCode.equals(Locale.getDefault().getLanguage())) {
			// for people in UK the UK accent is preferable to the US accent
			localePreferenceList.add( Locale.getDefault() );
		}

		// try to get the native country for the lang
		String countryCode = getDefaultCountryCode(bookLanguageCode);
		if (countryCode!=null) {
			localePreferenceList.add( new Locale(bookLanguageCode, countryCode));
		}

		// finally just add the language of the book
		localePreferenceList.add( new Locale(bookLanguageCode));
		return localePreferenceList;
	}

	private String getDefaultCountryCode(String language) {
		if (language.equals("en")) return Locale.UK.getCountry();
		if (language.equals("fr")) return Locale.FRANCE.getCountry();
		if (language.equals("de")) return Locale.GERMANY.getCountry();
		if (language.equals("zh")) return Locale.CHINA.getCountry();
		if (language.equals("it")) return Locale.ITALY.getCountry();
		if (language.equals("jp")) return Locale.JAPAN.getCountry();
		if (language.equals("ko")) return Locale.KOREA.getCountry();
		if (language.equals("hu")) return "HU";
		if (language.equals("cs")) return "CZ";
		if (language.equals("fi")) return "FI";
		if (language.equals("pl")) return "PL";
		if (language.equals("pt")) return "PT";
		if (language.equals("ru")) return "RU";
		if (language.equals("tr")) return "TR";
		return null;
	}

	private void startSpeakingInitingIfRequired() {
		if (mTts==null) {
        	Log.d(TAG, "mTts was null so initialising Tts");

	    	try {
		        // Initialize text-to-speech. This is an asynchronous operation.
		        // The OnInitListener (second argument) (this class) is called after initialization completes.
		        mTts = new TextToSpeech(BibleApplication.getApplication().getApplicationContext(), this.onInitListener);
	    	} catch (Exception e) {
	    		Log.e(TAG,  "Error initialising Tts", e);
	    		showError(R.string.error_occurred, e);
	    	}
    	} else {
   			startSpeaking();
    	}
	}

    /**
     * Add event listener to stop on call
     */
	protected void stopIfPhoneCall() {
		
		PhoneCallMonitor.ensureMonitoringStarted();
		
		// listen for phone call in order to pause speak
	}
    
    public synchronized void rewind() {
    	Log.d(TAG, "Rewind TTS");
    	// prevent onUtteranceCompleted causing next text to be grabbed
    	uniqueUtteranceNo++;
    	boolean wasPaused = isPaused;
    	isPaused = true;
    	if (isSpeaking) {
    		mTts.stop();
    	}
        isSpeaking = false;
        
        if (!wasPaused) {
	        // ensure current position is saved which is done during pause
	        mSpeakTextProvider.pause(mSpeakTiming.getFractionCompleted());
        }

        // move current position back a bit
        mSpeakTextProvider.rewind();

        isPaused = wasPaused;
        if (!isPaused) {
        	startSpeakingInitingIfRequired();
        }
    }

    public synchronized void forward() {
    	Log.d(TAG, "Forward TTS");
    	// prevent onUtteranceCompleted causing next text to be grabbed
    	uniqueUtteranceNo++;
    	boolean wasPaused = isPaused;
    	isPaused = true;
    	if (isSpeaking) {
    		mTts.stop();
    	}
        isSpeaking = false;
        
        if (!wasPaused) {
	        // ensure current position is saved which is done during pause
        	mSpeakTextProvider.pause(mSpeakTiming.getFractionCompleted());
        }

        // move current position back a bit
        mSpeakTextProvider.forward();

        isPaused = wasPaused;
        if (!isPaused) {
        	startSpeakingInitingIfRequired();
        }
    }

    public synchronized void pause() {
    	Log.d(TAG, "Pause TTS");
		
        if (isSpeaking()) {
            isPaused = true;
	        isSpeaking = false;
	        
	        mSpeakTextProvider.pause(mSpeakTiming.getFractionCompleted());
	        
	        //kill the tts engine because it could be a long ime before restart and the engine may become corrupted or used elsewhere
	        shutdownTtsEngine();
	        
	        fireStateChangeEvent();
        }
    }

    public synchronized void continueAfterPause() {
    	try {
	    	Log.d(TAG, "continue after pause");
            isPaused = false;
            
	        // ask TTs to say the text
	    	startSpeakingInitingIfRequired();
    	} catch (Exception e) {
    		Log.e(TAG, "TTS Error continuing after Pause", e);
    		mSpeakTextProvider.reset();
    		isSpeaking = false;
    		shutdown();
    	}
    	
    	// should be able to clear this because we are now speaking
    	isPaused = false;
    }

    /** only check timing when paused to prevent concurrency problems
     */
    public long getPausedTotalSeconds() {
    	return mSpeakTiming.getSecsForChars(mSpeakTextProvider.getTotalChars());
    }
    public long getPausedCompletedSeconds() {
    	return mSpeakTiming.getSecsForChars(mSpeakTextProvider.getSpokenChars());
    }

    private void startSpeaking() {
    	Log.d(TAG, "about to send all text to TTS");
        // ask TTs to say the text
    	if (!isSpeaking) {
    		mSpeakTextProvider.prepareForContinue();
    		speakNextChunk();
    		isSpeaking = true;
    		isPaused = false;
    		fireStateChangeEvent();
    	}
    	
    	// should be able to clear this because we are now speaking
    	isPaused = false;
    }

	private void speakNextChunk() {
		String text = mSpeakTextProvider.getNextTextToSpeak();
		if (text.length()>0) {
			speakString(text);
		}
	}

	private void speakString(String text) {
		if (mTts==null) {
			Log.e(TAG, "Error: attempt to speak when tts is null.  Text:"+text);
		} else {
	    	// Always set the UtteranceId (or else OnUtteranceCompleted will not be called)
	        String utteranceId = UTTERANCE_PREFIX+uniqueUtteranceNo++;
	    	Log.d(TAG, "do speak substring of length:"+text.length()+" utteranceId:"+utteranceId);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				Bundle ttsParams = new Bundle();
				mTts.speak(text,
						TextToSpeech.QUEUE_ADD, // handle flush by clearing text queue
						ttsParams,
						utteranceId);
			}
			else {
				HashMap<String, String> dummyTTSParams = new HashMap<>();
				dummyTTSParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
				mTts.speak(text,
						TextToSpeech.QUEUE_ADD, // handle flush by clearing text queue
						dummyTTSParams);
			}
			mSpeakTiming.started(utteranceId, text.length());
	        isSpeaking = true;
		}
    }

    /** flush cached text
     */
	private void clearTtsQueue() {
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

    private void showError(int msgId, Exception e) {
    	Dialogs.getInstance().showErrorMsg(msgId);
    }
	
    public void shutdown() {
    	Log.d(TAG, "Shutdown TTS");

    	isSpeaking = false;
        isPaused = false;
        
        // tts.stop can trigger onUtteranceCompleted so set above flags first to avoid sending of a further text and setting isSpeaking to true
    	shutdownTtsEngine();
    	mSpeakTextProvider.stop();

        fireStateChangeEvent();
    }

    private void shutdownTtsEngine() {
    	Log.d(TAG, "Shutdown TTS Engine");
		try {
	        // Don't forget to shutdown!
	        if (mTts != null) {
	        	try {
	        		mTts.stop();
	        	} catch (Exception e) {
	        		Log.e(TAG, "Error stopping Tts engine", e);
	        	}
	            mTts.shutdown();
	        }
		} catch (Exception e) {
			Log.e(TAG, "Error shutting down Tts engine", e);
		} finally {
			mTts = null;
		}
    }

    private void fireStateChangeEvent() {
    	if (isPaused) {
    		speakEventManager.speakStateChanged(new SpeakEvent(SpeakState.PAUSED));
    	} else if (isSpeaking) {
    		speakEventManager.speakStateChanged(new SpeakEvent(SpeakState.SPEAKING));
    	} else {
    		speakEventManager.speakStateChanged(new SpeakEvent(SpeakState.SILENT));
    	}

    }
    
	public boolean isSpeaking() {
		return isSpeaking;
	}

	public boolean isPaused() {
		return isPaused;
	}

	/**
	 * Pause speak if phone call starts
	 */
	public void onEvent(PhoneCallStarted event) {
		if (isSpeaking()) {
			pause();
		}

		if (isPaused()) {
			persistPauseState();
		} else {
			// ensure a previous pause does not hang around and be restored incorrectly
			clearPauseState();
		}
		
		shutdownTtsEngine();
	}

	public void onEvent(AppToBackgroundEvent event) {
		if (isPaused()) {
			persistPauseState();
		}
		else {
			clearPauseState();
		}
	}

	/** persist and restore pause state to allow pauses to continue over an app exit
	 */
	private void persistPauseState() {
		Log.d(TAG, "Persisting Pause state");
		boolean isBible = mSpeakTextProvider == bibleSpeakTextProvider;

		mSpeakTextProvider.persistState();
		CommonUtils.getSharedPreferences()
					.edit()
					.putString(PERSIST_LOCALE_KEY, currentLocale.toString())
					.putBoolean(PERSIST_BIBLE_PROVIDER, isBible)
					.apply();
	}
	
	private void restorePauseState() {
		// ensure no relevant current state is overwritten accidentally
		if (!isSpeaking()  && !isPaused()) {
			Log.d(TAG, "Attempting to restore any Persisted Pause state");
			boolean isBible = CommonUtils.getSharedPreferences().getBoolean(PERSIST_BIBLE_PROVIDER, true);
			switchProvider(isBible ? bibleSpeakTextProvider : generalSpeakTextProvider);

			isPaused = mSpeakTextProvider.restoreState();
			
			// restore locale information so tts knows which voice to load when it initialises
			currentLocale = new Locale(CommonUtils.getSharedPreferences().getString(PERSIST_LOCALE_KEY, Locale.getDefault().toString()));
			localePreferenceList = new ArrayList<>();
			localePreferenceList.add(currentLocale);
		}
	}
	private void clearPauseState() {
		Log.d(TAG, "Clearing Persisted Pause state");
		mSpeakTextProvider.clearPersistedState();
		CommonUtils.getSharedPreferences().edit().remove(PERSIST_LOCALE_KEY).apply();
	}

	// Implements TextToSpeech.OnInitListener.
	private TextToSpeech.OnInitListener onInitListener = new TextToSpeech.OnInitListener() {

		public void onInit ( int status){
			Log.d(TAG, "Tts initialised");
			boolean isOk = false;

			// status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
			if (mTts != null && status == TextToSpeech.SUCCESS) {
				Log.d(TAG, "Tts initialisation succeeded");

				// set speech rate
                int speakSpeedPercentPref = CommonUtils.getSharedPreferences().getInt("speak_speed_percent_pref", 100);
                mTts.setSpeechRate(speakSpeedPercentPref/100F);

				boolean localeOK = false;
				Locale locale = null;
				for (int i = 0; i < localePreferenceList.size() && !localeOK; i++) {
					locale = localePreferenceList.get(i);
					Log.d(TAG, "Checking for locale:" + locale);
					int result = mTts.setLanguage(locale);
					localeOK = ((result != TextToSpeech.LANG_MISSING_DATA) && (result != TextToSpeech.LANG_NOT_SUPPORTED));
					if (localeOK) {
						Log.d(TAG, "Successful locale:" + locale);
						currentLocale = locale;
					}
				}

				if (!localeOK) {
					Log.e(TAG, "TTS missing or not supported");
					// Language data is missing or the language is not supported.
					ttsLanguageSupport.addUnsupportedLocale(locale);
					showError(R.string.tts_lang_not_available, new Exception("Tts missing or not supported"));
				} else {
					// The TTS engine has been successfully initialized.
					ttsLanguageSupport.addSupportedLocale(locale);
					int ok = mTts.setOnUtteranceProgressListener(onUtteranceCompletedListener);
					if (ok == TextToSpeech.ERROR) {
						Log.e(TAG, "Error registering onUtteranceCompletedListener");
					} else {
						// everything seems to have succeeded if we get here
						isOk = true;
						// say the text
						startSpeaking();

						// add event listener to stop on call
						stopIfPhoneCall();
					}
				}
			} else {
				Log.d(TAG, "Tts initialisation failed");
				// Initialization failed.
				showError(R.string.error_occurred, new Exception("Tts Initialisation failed"));
			}

			if (!isOk) {
				shutdown();
			}
		}
	};

	private final UtteranceProgressListener onUtteranceCompletedListener = new UtteranceProgressListener() {
		@Override
		public void onStart(String utteranceId) {

		}

		@Override
		public void onDone(String utteranceId) {
			Log.d(TAG, "onUtteranceCompleted:"+utteranceId);
			// pause/rew/ff can sometimes allow old messages to complete so need to prevent move to next sentence if completed utterance is out of date
			if ((!isPaused && isSpeaking) && StringUtils.startsWith(utteranceId, UTTERANCE_PREFIX)) {
				long utteranceNo = Long.valueOf(StringUtils.removeStart(utteranceId, UTTERANCE_PREFIX));
				if (utteranceNo == uniqueUtteranceNo-1) {
					mSpeakTextProvider.finishedUtterance(utteranceId);

					// estimate cps
					mSpeakTiming.finished(utteranceId);

					// ask TTs to say the text
					if (mSpeakTextProvider.isMoreTextToSpeak()) {
						speakNextChunk();
					} else {
						Log.d(TAG, "Shutting down TTS");
						shutdown();
					}
				}
			}
		}

		@Override
		public void onError(String utteranceId) {

		}
	};

    public void setRate(float speechRate) {
		if(mTts != null) {
			mTts.setSpeechRate(speechRate);
		}
		CommonUtils.getSharedPreferences().edit().putInt("speak_speed_percent_pref", Math.round(speechRate*100F)).apply();
    }
}
