package net.bible.android.control.speak;

import android.media.AudioManager;
import android.util.Log;
import android.widget.Toast;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.AndRuntimeException;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.speak.TextToSpeechServiceManager;
import net.bible.service.sword.SwordContentFacade;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.Lazy;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class SpeakControl {

	private Lazy<TextToSpeechServiceManager> textToSpeechServiceManager;
	private final SwordContentFacade swordContentFacade;

	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

	private static final int NUM_LEFT_IDX = 3;
	private static final NumPagesToSpeakDefinition[] BIBLE_PAGES_TO_SPEAK_DEFNS = new NumPagesToSpeakDefinition[] {
			new NumPagesToSpeakDefinition(1, R.plurals.num_chapters, true, R.id.numChapters1),
			new NumPagesToSpeakDefinition(2, R.plurals.num_chapters, true, R.id.numChapters2),
			new NumPagesToSpeakDefinition(5, R.plurals.num_chapters, true, R.id.numChapters3),
			new NumPagesToSpeakDefinition(10, R.string.rest_of_book, false, R.id.numChapters4)
	};

	private static final NumPagesToSpeakDefinition[] COMMENTARY_PAGES_TO_SPEAK_DEFNS = new NumPagesToSpeakDefinition[] {
			new NumPagesToSpeakDefinition(1, R.plurals.num_verses, true, R.id.numChapters1),
			new NumPagesToSpeakDefinition(2, R.plurals.num_verses, true, R.id.numChapters2),
			new NumPagesToSpeakDefinition(5, R.plurals.num_verses, true, R.id.numChapters3),
			new NumPagesToSpeakDefinition(10, R.string.rest_of_chapter, false, R.id.numChapters4)
	};

	private static final NumPagesToSpeakDefinition[] DEFAULT_PAGES_TO_SPEAK_DEFNS = new NumPagesToSpeakDefinition[] {
			new NumPagesToSpeakDefinition(1, R.plurals.num_pages, true, R.id.numChapters1),
			new NumPagesToSpeakDefinition(2, R.plurals.num_pages, true, R.id.numChapters2),
			new NumPagesToSpeakDefinition(5, R.plurals.num_pages, true, R.id.numChapters3),
			new NumPagesToSpeakDefinition(10, R.plurals.num_pages, true, R.id.numChapters4)
	};

	private static final String TAG = "SpeakControl";

	@Inject
	public SpeakControl(Lazy<TextToSpeechServiceManager> textToSpeechServiceManager, SwordContentFacade swordContentFacade, ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.textToSpeechServiceManager = textToSpeechServiceManager;
		this.swordContentFacade = swordContentFacade;
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
	}

	/** return a list of prompt ids for the speak screen associated with the current document type
	 */
	public NumPagesToSpeakDefinition[] calculateNumPagesToSpeakDefinitions() {
		NumPagesToSpeakDefinition[] definitions;
		
		CurrentPage currentPage = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage();
		BookCategory bookCategory = currentPage.getCurrentDocument().getBookCategory();
		if (BookCategory.BIBLE.equals(bookCategory)) {
			Versification v11n = ((SwordBook) currentPage.getCurrentDocument()).getVersification();
			Verse verse = KeyUtil.getVerse(currentPage.getSingleKey());
			int chaptersLeft = 0;
			try {
				chaptersLeft = v11n.getLastChapter(verse.getBook()) - verse.getChapter() + 1;
			} catch (Exception e) {
				Log.e(TAG, "Error in book no", e);
			}
			definitions = BIBLE_PAGES_TO_SPEAK_DEFNS;
			definitions[NUM_LEFT_IDX].setNumPages(chaptersLeft);
		} else if (BookCategory.COMMENTARY.equals(bookCategory)) {
			Versification v11n = ((SwordBook) currentPage.getCurrentDocument()).getVersification();
			Verse verse = KeyUtil.getVerse(currentPage.getSingleKey());
			int versesLeft = 0;
			try {
				versesLeft = v11n.getLastVerse(verse.getBook(), verse.getChapter()) - verse.getVerse() + 1;
			} catch (Exception e) {
				Log.e(TAG, "Error in book no", e);
			}
			definitions = COMMENTARY_PAGES_TO_SPEAK_DEFNS;
			definitions[NUM_LEFT_IDX].setNumPages(versesLeft);
		} else {
			definitions = DEFAULT_PAGES_TO_SPEAK_DEFNS;
		}
		return definitions;
	}
	
	/** Toggle speech - prepare to speak single page OR if speaking then stop speaking
	 */
	public void speakToggleCurrentPage() {
		Log.d(TAG, "Speak toggle current page");

		// Continue
		if (isPaused()) {
			continueAfterPause();
        //Pause
		} else if (isSpeaking()) {
			pause();
        // Start Speak
		} else {
			try {
				CurrentPage page = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage();
				Book fromBook = page.getCurrentDocument();
		    	// first find keys to Speak
				List<Key> keyList = new ArrayList<>();
				keyList.add(page.getKey());
			
				speak(fromBook, keyList, true, false);

				Toast.makeText(BibleApplication.getApplication(), R.string.speak, Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
				Log.e(TAG, "Error getting chapters to speak", e);
				throw new AndRuntimeException("Error preparing Speech", e);
			}
		}
	}
	
	public boolean isCurrentDocSpeakAvailable() {
		boolean isAvailable;
		try {
			String docLangCode = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().getCurrentDocument().getLanguage().getCode();
			isAvailable = textToSpeechServiceManager.get().isLanguageAvailable(docLangCode);
		} catch (Exception e) {
			Log.e(TAG, "Error checking TTS lang available");
			isAvailable = false;
		}
		return isAvailable;
	}

	public boolean isSpeaking() {
		return textToSpeechServiceManager.get().isSpeaking();
	}

	public boolean isPaused() {
		return textToSpeechServiceManager.get().isPaused();
	}

	/** prepare to speak
	 */
	public void speak(NumPagesToSpeakDefinition numPagesDefn, boolean queue, boolean repeat) {
		Log.d(TAG, "Chapters:"+numPagesDefn.getNumPages());
		// if a previous speak request is paused clear the cached text 
		if (isPaused()) {
			Log.d(TAG, "Clearing paused Speak text");
			stop();
		}
		
		CurrentPage page = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage();
		Book fromBook = page.getCurrentDocument()
				;
    	// first find keys to Speak
		List<Key> keyList = new ArrayList<>();
		try {
			for (int i=0; i<numPagesDefn.getNumPages(); i++) {
				Key key = page.getPagePlus(i);
				if (key!=null) {
					keyList.add(key);
				}
			}
			
			speak(fromBook, keyList, queue, repeat);
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
			throw new AndRuntimeException("Error preparing Speech", e);
		}
	}
	
	public void speak(Book book, List<Key> keyList, boolean queue, boolean repeat) {
		Log.d(TAG, "Keys:"+keyList.size());
		// build a string containing the text to be spoken
		List<String> textToSpeak = new ArrayList<>();
		
    	// first concatenate the number of required chapters
		try {
			for (Key key : keyList) {
				// intro
				textToSpeak.add(key.getName()+". ");
//				textToSpeak.add("\n");
				
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

		speak(textToSpeak, book, queue);
	}
	
	/** prepare to speak
	 */
	private void speak(List<String> textsToSpeak, Book fromBook, boolean queue) {
		
		List<Locale> localePreferenceList = calculateLocalePreferenceList(fromBook);

		preSpeak();
		
		// speak current chapter or stop speech if already speaking
		Log.d(TAG, "Tell TTS to speak");
		textToSpeechServiceManager.get().speak(localePreferenceList, textsToSpeak, queue);
	}

	public void rewind() {
		if (isSpeaking() || isPaused()) {
			Log.d(TAG, "Rewind TTS speaking");
			textToSpeechServiceManager.get().rewind();
	    	Toast.makeText(BibleApplication.getApplication(), R.string.rewind, Toast.LENGTH_SHORT).show();
		}
	}
	
	public void forward() {
		if (isSpeaking() || isPaused()) {
			Log.d(TAG, "Forward TTS speaking");
			textToSpeechServiceManager.get().forward();
	    	Toast.makeText(BibleApplication.getApplication(), R.string.forward, Toast.LENGTH_SHORT).show();
		}
	}

	public void pause() {
		if (isSpeaking() || isPaused()) {
			Log.d(TAG, "Pause TTS speaking");
	    	TextToSpeechServiceManager tts = textToSpeechServiceManager.get();
			tts.pause();
			String pause = CommonUtils.getResourceString(R.string.pause);
			String timeProgress = CommonUtils.getHoursMinsSecs(tts.getPausedCompletedSeconds())+"/"+CommonUtils.getHoursMinsSecs(tts.getPausedTotalSeconds());
	    	Toast.makeText(BibleApplication.getApplication(), pause+"\n"+timeProgress, Toast.LENGTH_SHORT).show();
		}
	}

	public void continueAfterPause() {
		Log.d(TAG, "Continue TTS speaking after pause");
		preSpeak();
		textToSpeechServiceManager.get().continueAfterPause();
    	Toast.makeText(BibleApplication.getApplication(), R.string.speak, Toast.LENGTH_SHORT).show();
	}
	
	public void stop() {
		Log.d(TAG, "Stop TTS speaking");
		doStop();
    	Toast.makeText(BibleApplication.getApplication(), R.string.stop, Toast.LENGTH_SHORT).show();
	}
	
	private void doStop() {
		textToSpeechServiceManager.get().shutdown();
	}

	private void preSpeak() {
		// ensure volume controls adjust correct stream - not phone which is the default
		// STREAM_TTS does not seem to be available but this article says use STREAM_MUSIC instead: http://stackoverflow.com/questions/7558650/how-to-set-volume-for-text-to-speech-speak-method
        CurrentActivityHolder.getInstance().getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

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
}
