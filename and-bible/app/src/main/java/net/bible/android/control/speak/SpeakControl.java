package net.bible.android.control.speak;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import de.greenrobot.event.EventBus;
import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.AndRuntimeException;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.speak.BibleSpeakTextProvider;
import net.bible.service.device.speak.TextToSpeechNotificationService;
import net.bible.service.device.speak.TextToSpeechServiceManager;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

import dagger.Lazy;
import org.jetbrains.annotations.Nullable;

import static net.bible.service.device.speak.TextToSpeechNotificationService.ACTION_REMOVE;
import static net.bible.service.device.speak.TextToSpeechNotificationService.ACTION_START;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class SpeakControl {

	private Lazy<TextToSpeechServiceManager> textToSpeechServiceManager;
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

	@Inject public BookmarkControl bookmarkControl;
	private Timer sleepTimer = new Timer();

	@Inject
	public SpeakControl(Lazy<TextToSpeechServiceManager> textToSpeechServiceManager, ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
		this.textToSpeechServiceManager = textToSpeechServiceManager;
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
		if(isPaused()) {
			showNotification();
		}
		EventBus.getDefault().register(this);
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
				if(fromBook.getBookCategory().equals(BookCategory.BIBLE))
				{
					speakBible();
				}
				else {
					// first find keys to Speak
					List<Key> keyList = new ArrayList<>();
					keyList.add(page.getKey());

					speakKeyList(fromBook, keyList, true, false);
				}

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
	public void speakText(NumPagesToSpeakDefinition numPagesDefn, boolean queue, boolean repeat) {
		Log.d(TAG, "Chapters:"+numPagesDefn.getNumPages());
		// if a previous speak request is paused clear the cached text
		if (isPaused()) {
			Log.d(TAG, "Clearing paused Speak text");
			stop();
		}

		preSpeak();

		CurrentPage page = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage();
		Book fromBook = page.getCurrentDocument();

		try {
			// first find keys to Speak
			List<Key> keyList = new ArrayList<>();
			for (int i=0; i<numPagesDefn.getNumPages(); i++) {
				Key key = page.getPagePlus(i);
				if (key!=null) {
					keyList.add(key);
				}
			}

			textToSpeechServiceManager.get().speakText(fromBook, keyList, queue, repeat);
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
			throw new AndRuntimeException("Error preparing Speech", e);
		}
	}

	/** prepare to speak
	 */
	public void speakBible() {
		// if a previous speak request is paused clear the cached text
		if (isPaused()) {
			stop();
		}

		preSpeak();

		CurrentPage page = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage();
		Book fromBook = page.getCurrentDocument();

		try {
			textToSpeechServiceManager.get().speakBible((SwordBook)fromBook, (Verse)page.getSingleKey());
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
			throw new AndRuntimeException("Error preparing Speech", e);
		}
	}

	public BibleSpeakTextProvider getBibleSpeakTextProvider() {
		return textToSpeechServiceManager.get().getBibleSpeakTextProvider();
	}

	public void speakKeyList(Book book, List<Key> keyList, boolean queue, boolean repeat) {
		preSpeak();

		// speak current chapter or stop speech if already speaking
		Log.d(TAG, "Tell TTS to speak");
		textToSpeechServiceManager.get().speakText(book, keyList, queue, repeat);
	}

	public void rewind() {
		rewind(null);
	}

	public void rewind(SpeakSettings.RewindAmount amount) {
		if (isSpeaking() || isPaused()) {
			Log.d(TAG, "Rewind TTS speaking");
			textToSpeechServiceManager.get().rewind(amount);
	    	Toast.makeText(BibleApplication.getApplication(), R.string.rewind, Toast.LENGTH_SHORT).show();
		}
	}

	public void forward() {
		forward(null);
	}

	public void forward(SpeakSettings.RewindAmount amount) {
		if (isSpeaking() || isPaused()) {
			Log.d(TAG, "Forward TTS speaking");
			textToSpeechServiceManager.get().forward(amount);
	    	Toast.makeText(BibleApplication.getApplication(), R.string.forward, Toast.LENGTH_SHORT).show();
		}
	}

	public void pause() {
		pause(false);
	}

	// automated: pause & continue triggered due to settings change event
	public void pause(boolean automated) {
		if(!automated) {
			sleepTimer.cancel();
		}
		if (isSpeaking() || isPaused()) {
			showNotification();
			Log.d(TAG, "Pause TTS speaking");
	    	TextToSpeechServiceManager tts = textToSpeechServiceManager.get();
			tts.pause();
			String pauseToastText = CommonUtils.getResourceString(R.string.pause);

			long completedSeconds = tts.getPausedCompletedSeconds();
			long totalSeconds = tts.getPausedTotalSeconds();

			if(totalSeconds > 0) {
				String timeProgress = CommonUtils.getHoursMinsSecs(completedSeconds) + "/" + CommonUtils.getHoursMinsSecs(totalSeconds);
				pauseToastText += "\n" + timeProgress;
			}

			if(!automated) {
				Toast.makeText(BibleApplication.getApplication(), pauseToastText, Toast.LENGTH_SHORT).show();
			}
		}
	}

	public void continueAfterPause() {
		continueAfterPause(false);
	}

	private void continueAfterPause(boolean automated) {
		Log.d(TAG, "Continue TTS speaking after pause");
		preSpeak(automated);
		textToSpeechServiceManager.get().continueAfterPause(automated);
	}

	public void stop() {
		Log.d(TAG, "Stop TTS speaking");
		doStop();
		sleepTimer.cancel();
    	Toast.makeText(BibleApplication.getApplication(), R.string.stop, Toast.LENGTH_SHORT).show();
	}

	private void doStop() {
		textToSpeechServiceManager.get().shutdown();
		removeNotification();
	}

	private void preSpeak() {
		preSpeak(false);
	}

	// automated: pause & continue triggered due to settings change event
	private void preSpeak(boolean automated) {
		showNotification();
		// ensure volume controls adjust correct stream - not phone which is the default
		// STREAM_TTS does not seem to be available but this article says use STREAM_MUSIC instead: http://stackoverflow.com/questions/7558650/how-to-set-volume-for-text-to-speech-speak-method
        Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
        if(activity != null) {
			activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		}
		if(!automated) {
			int sleepTimerAmount = SpeakSettings.Companion.load().getSleepTimer();
			sleepTimer.cancel();
			if (sleepTimerAmount > 0) {
				BibleApplication app = BibleApplication.getApplication();
				Toast.makeText(app, app.getString(R.string.sleep_timer_started, sleepTimerAmount), Toast.LENGTH_SHORT).show();
				sleepTimer = new Timer("TTS Sleep timer");
				sleepTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						pause(true);
					}
				}, sleepTimerAmount * 60000);
			} else {
				Toast.makeText(BibleApplication.getApplication(), R.string.speak, Toast.LENGTH_SHORT).show();
			}
		}
	}

	public void onEvent(SpeakSettings ev) {

		if(!isPaused() && !isSpeaking()) {
			bookmarkControl.updateBookmarkSettings(ev.getPlaybackSettings());
		}
        else if (isSpeaking()) {
        	pause(true);
        	continueAfterPause(true);
		}
	}

    private void showNotification() {
		notificationAction(ACTION_START);
	}

	public void removeNotification() {
		if(isSpeaking()) {
			pause();
		}
		notificationAction(ACTION_REMOVE);
	}

	private void notificationAction(String actionName) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			BibleApplication app = BibleApplication.getApplication();
			Intent intent = new Intent(app, TextToSpeechNotificationService.class);
			intent.setAction(actionName);
			app.startService(intent);
		}
	}

	@Nullable
	public CharSequence getStatusText() {
		return textToSpeechServiceManager.get().getStatusText();
	}
}
