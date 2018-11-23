/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.speak;

import android.app.Activity;
import android.media.AudioManager;
import android.util.Log;
import android.widget.Toast;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.event.ToastEvent;
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.page.window.WindowRepository;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.AndRuntimeException;
import net.bible.service.common.CommonUtils;
import net.bible.service.db.bookmark.BookmarkDto;
import net.bible.service.device.speak.TextToSpeechServiceManager;

import net.bible.service.device.speak.event.SpeakProgressEvent;
import net.bible.service.sword.SwordDocumentFacade;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.KeyUtil;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;

import java.util.*;

import javax.inject.Inject;

import dagger.Lazy;
import org.jetbrains.annotations.NotNull;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class SpeakControl {

	private Lazy<TextToSpeechServiceManager> textToSpeechServiceManager;
	private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;
	private final WindowRepository windowRepository;
	private SwordDocumentFacade swordDocumentFacade;

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
	private Timer sleepTimer = new Timer("TTS sleep timer");
	private TimerTask timerTask;
	private CurrentPageManager speakPageManager;

	@Inject
	public SpeakControl(Lazy<TextToSpeechServiceManager> textToSpeechServiceManager,
						ActiveWindowPageManagerProvider activeWindowPageManagerProvider,
						SwordDocumentFacade swordDocumentFacade,
						WindowRepository windowRepository
	) {
		this.textToSpeechServiceManager = textToSpeechServiceManager;
		this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
		this.swordDocumentFacade = swordDocumentFacade;
		this.windowRepository = windowRepository;
		ABEventBus.getDefault().register(this);
	}

	@Override
	protected void finalize() {
		// Allow timer threads to be stopped on GC (good for tests)
		stopTimer();
		sleepTimer.cancel();
		sleepTimer = null;
	}

	public void onEventMainThread(SpeakProgressEvent event) {
		if(event.getSynchronize()) {
			if(speakPageManager == null) {
				speakPageManager = activeWindowPageManagerProvider.getActiveWindowPageManager();
			}
			speakPageManager.setCurrentDocumentAndKey(event.getBook(), event.getKey(), false);
		}
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
	public void toggleSpeak() {
		Log.d(TAG, "Speak toggle current page");

		// Continue
		if (isPaused()) {
			continueAfterPause();
		//Pause
		} else if (isSpeaking()) {
			pause();
		// Start Speak
		} else
		{
			try {
				CurrentPage page = activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage();
				Book fromBook = page.getCurrentDocument();
				if(fromBook.getBookCategory().equals(BookCategory.BIBLE))
				{
					speakBible();
				}
				else {
					speakText();
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
		return booksAvailable() && textToSpeechServiceManager.get().isSpeaking();
	}

	public boolean isPaused() {
		return booksAvailable() && textToSpeechServiceManager.get().isPaused();
	}

	private boolean booksAvailable() {
		// By this checking, try to avoid issues with isSpeaking and isPaused causing crash if window is not yet available
		// (such as headphone switching in the initial startup screen)
		return this.swordDocumentFacade.getBibles().size() > 0;
	}
	/** prepare to speak
	 */
	public void speakText() {
		SpeakSettings s = SpeakSettings.Companion.load();
		NumPagesToSpeakDefinition numPagesDefn = calculateNumPagesToSpeakDefinitions()[s.getNumPagesToSpeakId()];

		//, boolean queue, boolean repeat
		Log.d(TAG, "Chapters:"+numPagesDefn.getNumPages());
		// if a previous speak request is paused clear the cached text
		if (isPaused()) {
			Log.d(TAG, "Clearing paused Speak text");
			stop();
		}

		prepareForSpeaking();

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

			textToSpeechServiceManager.get().speakText(fromBook, keyList, s.getQueue(), s.getRepeat());
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
			throw new AndRuntimeException("Error preparing Speech", e);
		}
	}

	public void speakBible(SwordBook book, Verse verse) {
		// if a previous speak request is paused clear the cached text
		if (isPaused()) {
			stop();
		}

		prepareForSpeaking();

		try {
			textToSpeechServiceManager.get().speakBible(book, verse);
		} catch (Exception e) {
			Log.e(TAG, "Error getting chapters to speak", e);
			throw new AndRuntimeException("Error preparing Speech", e);
		}
	}

	public void speakBible() {
		speakPageManager = activeWindowPageManagerProvider.getActiveWindowPageManager();
		CurrentPage page = speakPageManager.getCurrentPage();
		speakBible((Verse) page.getSingleKey());
	}

	private Book getCurrentBook() {
        return activeWindowPageManagerProvider
                .getActiveWindowPageManager()
                .getCurrentPage()
                .getCurrentDocument();
	}

	private void speakBible(Verse verse) {
		speakBible((SwordBook) getCurrentBook(), verse);
	}


	public void speakKeyList(Book book, List<Key> keyList, boolean queue, boolean repeat) {
		prepareForSpeaking();

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

	public void pause(boolean willContinueAfterThis) {

		pause(willContinueAfterThis, !willContinueAfterThis);
	}

	public void pause() {
		pause(false, true);
	}

	public void setupMockedTts() {
		textToSpeechServiceManager.get().setupMockedTts();
	}

	public void pause(boolean willContinueAfterThis, boolean toast) {
		if(!willContinueAfterThis) {
			stopTimer();
		}
		if (isSpeaking() || isPaused()) {
			Log.d(TAG, "Pause TTS speaking");
			TextToSpeechServiceManager tts = textToSpeechServiceManager.get();
			tts.pause(willContinueAfterThis);
			String pauseToastText = CommonUtils.getResourceString(R.string.pause);

			long completedSeconds = tts.getPausedCompletedSeconds();
			long totalSeconds = tts.getPausedTotalSeconds();

			if(totalSeconds > 0) {
				String timeProgress = CommonUtils.getHoursMinsSecs(completedSeconds) + "/" + CommonUtils.getHoursMinsSecs(totalSeconds);
				pauseToastText += "\n" + timeProgress;
			}

			if(!willContinueAfterThis && toast) {
				Toast.makeText(BibleApplication.getApplication(), pauseToastText, Toast.LENGTH_SHORT).show();
			}
		}
	}

	public void continueAfterPause() {
		continueAfterPause(false);
	}

	private void continueAfterPause(boolean automated) {
		Log.d(TAG, "Continue TTS speaking after pause");
		if(!automated) {
			prepareForSpeaking();
		}
		textToSpeechServiceManager.get().continueAfterPause();
	}

	public void stop() {
		if (!isSpeaking() && !isPaused()) {
			return;
		}

		Log.d(TAG, "Stop TTS speaking");
		textToSpeechServiceManager.get().shutdown();
		stopTimer();
		Toast.makeText(BibleApplication.getApplication(), R.string.stop, Toast.LENGTH_SHORT).show();
	}

	private void prepareForSpeaking() {
		// ensure volume controls adjust correct stream - not phone which is the default
		// STREAM_TTS does not seem to be available but this article says use STREAM_MUSIC instead:
		// http://stackoverflow.com/questions/7558650/how-to-set-volume-for-text-to-speech-speak-method
		Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
		if(activity != null) {
			activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		}
		enableSleepTimer(SpeakSettings.Companion.load().getSleepTimer());
	}

	public void onEvent(NumberOfWindowsChangedEvent ev) {
		if (isSpeaking() && SpeakSettings.Companion.load().getMultiTranslation()) {
			Thread work = new Thread(new Runnable() {
				public void run() {
					pause(true);
					continueAfterPause(true);
				}
			});
			work.start();
		}
	}

	public void onEvent(final SpeakSettingsChangedEvent ev) {
		Thread work = new Thread(new Runnable() {
			public void run() {
				textToSpeechServiceManager.get().updateSettings(ev);
				if(!isPaused() && !isSpeaking()) {
					// if playback is stopped, we want to update bookmark of the verse that we are currently reading (if any)
					if(ev.getUpdateBookmark()) {
						bookmarkControl.updateBookmarkSettings(ev.getSpeakSettings().getPlaybackSettings());
					}
				}
				else if (isSpeaking()) {
					pause(true);
					if(ev.getSleepTimerChanged()){
						enableSleepTimer(ev.getSpeakSettings().getSleepTimer());
					}
					continueAfterPause(true);
				}
			}
		});
		work.start();
	}

	public String getStatusText(int showFlag) {
		if(!isSpeaking() && !isPaused()) {
			return "- " + BibleApplication.getApplication().getString(R.string.speak_status_stopped) + " -";
		} else {
			return textToSpeechServiceManager.get().getStatusText(showFlag);
		}
	}

	private void enableSleepTimer(int sleepTimerAmount) {
		stopTimer();
		if (sleepTimerAmount > 0) {
		    Log.d(TAG, "Activating sleep timer");
			BibleApplication app = BibleApplication.getApplication();
			ABEventBus.getDefault().post(new ToastEvent(app.getString(R.string.sleep_timer_started, sleepTimerAmount)));
			timerTask = new TimerTask() {
				@Override
				public void run() {
					pause(false, false);
					SpeakSettings s = SpeakSettings.Companion.load();
					s.setSleepTimer(0);
					s.save();
				}
			};
			sleepTimer.schedule(timerTask, sleepTimerAmount * 60000);
		} else {
			ABEventBus.getDefault().post(new ToastEvent(BibleApplication.getApplication().getString(R.string.speak)));
		}
	}

	private void stopTimer() {
		if(timerTask != null) {
			timerTask.cancel();
		}
		timerTask = null;
	}

	public Date getSleepTimerActivationTime() {
		if(timerTask == null) {
			return null;
		}
		else {
			return new Date(timerTask.scheduledExecutionTime());
		}
	}

	public boolean sleepTimerActive() {
		return timerTask != null;
	}

	public void speakFromBookmark(@NotNull BookmarkDto dto) {
		SwordBook book = null;
		PlaybackSettings playbackSettings = dto.getPlaybackSettings();
		if(playbackSettings != null) {
			book = (SwordBook) Books.installed().getBook(playbackSettings.getBookId());
		}
		if (isSpeaking() || isPaused()) {
			stop();
		}
		if(book != null) {
			speakBible(book, dto.getVerseRange().getStart());
		} else {
			speakBible(dto.getVerseRange().getStart());
		}
	}
}
