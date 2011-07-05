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
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleInfo;

import android.util.Log;

public class SpeakControl {

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

	/** return a list of prompt ids for the speak screen associated with the current document type
	 */
	public NumPagesToSpeakDefinition[] getNumPagesToSpeakDefinitions() {
		NumPagesToSpeakDefinition[] definitions = null;
		
		CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
		BookCategory bookCategory = currentPage.getCurrentDocument().getBookCategory();
		if (BookCategory.BIBLE.equals(bookCategory)) {
			Verse verse = KeyUtil.getVerse(currentPage.getSingleKey());
			int chaptersLeft = 0;
			try {
				chaptersLeft = BibleInfo.chaptersInBook(verse.getBook())-verse.getChapter()+1;
			} catch (Exception e) {
				Log.e(TAG, "Error in book no", e);
			}
			definitions = BIBLE_PAGES_TO_SPEAK_DEFNS;
			definitions[NUM_LEFT_IDX].setNumPages(chaptersLeft);
		} else if (BookCategory.COMMENTARY.equals(bookCategory)) {
			Verse verse = KeyUtil.getVerse(currentPage.getSingleKey());
			int versesLeft = 0;
			try {
				versesLeft = BibleInfo.versesInChapter(verse.getBook(), verse.getChapter())-verse.getVerse()+1;
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
	
	/** prepare to speak
	 */
	public void speak(NumPagesToSpeakDefinition numPagesDefn, boolean queue, boolean repeat) {
		Log.d(TAG, "Chapters:"+numPagesDefn.getNumPages());
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
		StringBuffer textToSpeak = new StringBuffer();
		
		textToSpeak.append(getIntro(page, numPagesDefn.getNumPages()));
		
    	// first concatenate the number of required chapters
		try {
			for (int i=0; i<numPagesDefn.getNumPages(); i++) {
				Key current = page.getPagePlus(i);
				if (current!=null) {
					textToSpeak.append( SwordApi.getInstance().getCanonicalText(page.getCurrentDocument(), current));
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
		}
		
		// if repeat was checked then concatenate with itself
		if (repeat) {
			// grab the text now before repeating is appended otherwise 'repeating..' is also appended at the end
			String baseText = textToSpeak.toString();
			textToSpeak.append("\n")
					   .append(baseText);
		}

		// speak current chapter or stop speech if already speaking
    	TextToSpeechController tts = TextToSpeechController.getInstance();
		Log.d(TAG, "Tell TTS to say current chapter");
    	tts.speak(speechLocale, textToSpeak.toString(), queue);
	}
	
	private String getIntro(CurrentPage page, int numPages) {
		StringBuilder text = new StringBuilder();
		text.append(page.getKey());
		if (numPages>1) {
			text.append(" to ").append(page.getPagePlus(numPages-1));
		}
		text.append(".\n");
		return text.toString();
	}
	
	public void stop() {
		Log.d(TAG, "Stop TTS speaking");
    	TextToSpeechController tts = TextToSpeechController.getInstance();
		tts.stop();
	}
}
