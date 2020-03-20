/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.speak

import android.media.AudioManager
import android.util.Log
import android.widget.Toast

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.AndRuntimeException
import net.bible.service.common.CommonUtils
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.device.speak.TextToSpeechServiceManager

import net.bible.service.device.speak.event.SpeakProgressEvent
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse

import java.util.*

import javax.inject.Inject

import dagger.Lazy
import de.greenrobot.event.EventBus

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class SpeakControl @Inject constructor(
        private val textToSpeechServiceManager: Lazy<TextToSpeechServiceManager>,
        private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider,
        private val swordDocumentFacade: SwordDocumentFacade
) {

    @Inject lateinit var bookmarkControl: BookmarkControl
    private var sleepTimer: Timer = Timer("TTS sleep timer")
    private var timerTask: TimerTask? = null
    private var _speakPageManager: CurrentPageManager? = null

    private var ttsInitialized = false
    private val ttsServiceManager: TextToSpeechServiceManager get () {
        if(!ttsInitialized)
            ttsInitialized = true
        return textToSpeechServiceManager.get()
    }
    

    private val speakPageManager: CurrentPageManager
        get() {
            var pageManager = _speakPageManager
            if(pageManager == null) {
                pageManager = activeWindowPageManagerProvider.activeWindowPageManager
                _speakPageManager = pageManager
            }
            return pageManager
        }

    val isCurrentDocSpeakAvailable: Boolean
        get() {
            return try {
                val docLangCode = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument?.language?.code
                if(docLangCode == null) return true else ttsServiceManager.isLanguageAvailable(docLangCode)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking TTS lang available")
                false
            }
        }

    val isSpeaking: Boolean
        get() = booksAvailable && ttsInitialized && ttsServiceManager.isSpeaking

    val isPaused: Boolean
        get() = booksAvailable && ttsInitialized && ttsServiceManager.isPaused

    val isStopped: Boolean
        get() = !isSpeaking && !isPaused

    private val currentBook: Book?
        get() = activeWindowPageManagerProvider
                .activeWindowPageManager
                .currentPage
                .currentDocument

    val sleepTimerActivationTime: Date?
        get() = if (timerTask == null) {
            null
        } else {
            Date(timerTask!!.scheduledExecutionTime())
        }
    val currentlyPlayingBook: Book?
        get() = if (!booksAvailable || !ttsInitialized) null else ttsServiceManager.currentlyPlayingBook

    val currentlyPlayingVerse: Verse?
        get() = if (!booksAvailable || !ttsInitialized) null else ttsServiceManager.currentlyPlayingVerse

    init {
        ABEventBus.getDefault().register(this)
    }

    protected fun finalize() {
        // Allow timer threads to be stopped on GC (good for tests)
        stopTimer()
        sleepTimer.cancel()
    }

    fun onEventMainThread(event: SpeakProgressEvent) {
        val settings = SpeakSettings.load()
        if (settings.synchronize) {
            val book = speakPageManager.currentPage.currentDocument
            if(setOf(BookCategory.BIBLE, BookCategory.COMMENTARY).contains(book?.bookCategory)) {
                speakPageManager.setCurrentDocumentAndKey(book, event.key, false)
            }
        }
    }

    /** return a list of prompt ids for the speak screen associated with the current document type
     */
    fun calculateNumPagesToSpeakDefinitions(): Array<NumPagesToSpeakDefinition> {
        val definitions: Array<NumPagesToSpeakDefinition>

        val currentPage = activeWindowPageManagerProvider.activeWindowPageManager.currentPage
        val bookCategory = currentPage.currentDocument?.bookCategory
        if (BookCategory.BIBLE == bookCategory) {
            val v11n = (currentPage.currentDocument as SwordBook).versification
            val verse = KeyUtil.getVerse(currentPage.singleKey)
            var chaptersLeft = 0
            try {
                chaptersLeft = v11n.getLastChapter(verse.book) - verse.chapter + 1
            } catch (e: Exception) {
                Log.e(TAG, "Error in book no", e)
            }

            definitions = BIBLE_PAGES_TO_SPEAK_DEFNS
            definitions[NUM_LEFT_IDX].numPages = chaptersLeft
        } else if (BookCategory.COMMENTARY == bookCategory) {
            val v11n = (currentPage.currentDocument as SwordBook).versification
            val verse = KeyUtil.getVerse(currentPage.singleKey)
            var versesLeft = 0
            try {
                versesLeft = v11n.getLastVerse(verse.book, verse.chapter) - verse.verse + 1
            } catch (e: Exception) {
                Log.e(TAG, "Error in book no", e)
            }

            definitions = COMMENTARY_PAGES_TO_SPEAK_DEFNS
            definitions[NUM_LEFT_IDX].numPages = versesLeft
        } else {
            definitions = DEFAULT_PAGES_TO_SPEAK_DEFNS
        }
        return definitions
    }

    private fun resetPassageRepeatIfOutsideRange() {
        val settings = SpeakSettings.load()
        val range = settings.playbackSettings.verseRange
        val page = activeWindowPageManagerProvider.activeWindowPageManager.currentPage
        val currentVerse = page.singleKey as Verse

        // If we have range playback mode set up, and user starts playback not from within the range,
        // let's cancel the range playback mode.
        if(range != null && !(range.start.ordinal <= currentVerse.ordinal
                && range.end.ordinal >= currentVerse.ordinal)) {
            settings.playbackSettings.verseRange = null
            settings.save()
            ABEventBus.getDefault().post(ToastEvent(
                messageId = R.string.verse_range_mode_disabled,
                duration = Toast.LENGTH_LONG
            ))
        }

    }

    /** Toggle speech - prepare to speak single page OR if speaking then stop speaking
     */
    fun toggleSpeak() {
        Log.d(TAG, "Speak toggle current page")
        // Continue
        if (isPaused) {
            continueAfterPause()
            //Pause
        } else if (isSpeaking) {
            pause()
            // Start Speak
        } else {
            if (!booksAvailable) {
                EventBus.getDefault().post(ToastEvent(R.string.speak_no_books_available))
                return
            }
            try {
                val page = activeWindowPageManagerProvider.activeWindowPageManager.currentPage
                val fromBook = page.currentDocument
                if (fromBook?.bookCategory == BookCategory.BIBLE) {
                    resetPassageRepeatIfOutsideRange()
                    speakBible()
                } else {
                    speakText()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error getting chapters to speak", e)
                throw AndRuntimeException("Error preparing Speech", e)
            }

        }
    }

    // By this checking, try to avoid issues with isSpeaking and isPaused causing crash if window is not yet available
    // (such as headphone switching in the initial startup screen)
    private val booksAvailable: Boolean get() = this.swordDocumentFacade.bibles.size > 0

    /** prepare to speak
     */
    fun speakText() {
        val settings = SpeakSettings.load()
        val numPagesDefn = calculateNumPagesToSpeakDefinitions()[settings.numPagesToSpeakId]

        //, boolean queue, boolean repeat
        Log.d(TAG, "Chapters:" + numPagesDefn.numPages)
        // if a previous speak request is paused clear the cached text
        if (isPaused) {
            Log.d(TAG, "Clearing paused Speak text")
            stop()
        }

        prepareForSpeaking()

        val page = activeWindowPageManagerProvider.activeWindowPageManager.currentPage
        val fromBook = page.currentDocument

        try {
            // first find keys to Speak
            val keyList = ArrayList<Key>()
            for (i in 0 until numPagesDefn.numPages) {
                val key = page.getPagePlus(i)
                if (key != null) {
                    keyList.add(key)
                }
            }
            if(fromBook == null) {
                Log.e(TAG, "currentdocument is null! Can't speak")
                return
            }
            ttsServiceManager.speakText(fromBook, keyList, settings.queue, settings.repeat)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapters to speak", e)
            throw AndRuntimeException("Error preparing Speech", e)
        }

    }

    fun speakBible(book: SwordBook, verse: Verse) {
        // if a previous speak request is paused clear the cached text
        if (isPaused) {
            stop()
        }

        prepareForSpeaking()
        if(SpeakSettings.load().synchronize) {
            speakPageManager.setCurrentDocumentAndKey(book, verse)
        }
        try {
            ttsServiceManager.speakBible(book, verse)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapters to speak", e)
            throw AndRuntimeException("Error preparing Speech", e)
        }

    }

    fun speakBible() {
        val page = speakPageManager.currentPage
        speakBible(page.singleKey as Verse)
    }

    private fun speakBible(verse: Verse) {
        speakBible(currentBook as SwordBook, verse)
    }

    fun speakBible(bookRef: String, osisRef: String) {
        val book = Books.installed().getBook(bookRef) as SwordBook

        try {
            val verse = (book.getKey(osisRef) as RangedPassage).getVerseAt(0)
            speakBible(book, verse)
        } catch (e: NoSuchKeyException) {
            Log.e(TAG, "Key not found $osisRef in $currentBook")
        }

    }

    fun speakKeyList(book: Book, keyList: List<Key>, queue: Boolean, repeat: Boolean) {
        prepareForSpeaking()

        // speak current chapter or stop speech if already speaking
        Log.d(TAG, "Tell TTS to speak")
        ttsServiceManager.speakText(book, keyList, queue, repeat)
    }

    @JvmOverloads
    fun rewind(amount: SpeakSettings.RewindAmount? = null) {
        if (isSpeaking || isPaused) {
            Log.d(TAG, "Rewind TTS speaking")
            ttsServiceManager.rewind(amount)
            ABEventBus.getDefault().post(ToastEvent(R.string.rewind))
        }
    }

    @JvmOverloads
    fun forward(amount: SpeakSettings.RewindAmount? = null) {
        if (isSpeaking || isPaused) {
            Log.d(TAG, "Forward TTS speaking")
            ttsServiceManager.forward(amount)
            ABEventBus.getDefault().post(ToastEvent(R.string.forward))
        }
    }

    fun pause() {
        pause(false, true)
    }

    fun setupMockedTts() {
        ttsServiceManager.setupMockedTts()
    }

    @JvmOverloads
    fun pause(willContinueAfterThis: Boolean, toast: Boolean = !willContinueAfterThis) {
        if (!willContinueAfterThis) {
            stopTimer()
        }
        if (isSpeaking || isPaused) {
            Log.d(TAG, "Pause TTS speaking")
            val tts = ttsServiceManager
            tts.pause(willContinueAfterThis)
            var pauseToastText = CommonUtils.getResourceString(R.string.pause)

            val completedSeconds = tts.pausedCompletedSeconds
            val totalSeconds = tts.pausedTotalSeconds

            if (totalSeconds > 0) {
                val timeProgress = CommonUtils.getHoursMinsSecs(completedSeconds) + "/" + CommonUtils.getHoursMinsSecs(totalSeconds)
                pauseToastText += "\n" + timeProgress
            }

            if (!willContinueAfterThis && toast) {
                ABEventBus.getDefault().post(ToastEvent(pauseToastText))
            }
        }
    }

    fun continueAfterPause() {
        continueAfterPause(false)
    }

    private fun continueAfterPause(automated: Boolean) {
        Log.d(TAG, "Continue TTS speaking after pause")
        if (!automated) {
            prepareForSpeaking()
        }
        ttsServiceManager.continueAfterPause()
    }

    fun stop(willContinueAfter: Boolean=false, force: Boolean=false) {
        if (!force && !isSpeaking && !isPaused) {
            return
        }
        // Reset page manager
        _speakPageManager = null

        Log.d(TAG, "Stop TTS speaking")
        ttsServiceManager.shutdown(willContinueAfter)
        stopTimer()
        if(!force) {
            ABEventBus.getDefault().post(ToastEvent(R.string.stop))
        }
    }

    private fun prepareForSpeaking() {
        // ensure volume controls adjust correct stream - not phone which is the default
        // STREAM_TTS does not seem to be available but this article says use STREAM_MUSIC instead:
        // http://stackoverflow.com/questions/7558650/how-to-set-volume-for-text-to-speech-speak-method
        CommonUtils.sharedPreferences.edit().putLong("speak-last-used", System.currentTimeMillis()).apply()
        val activity = CurrentActivityHolder.getInstance().currentActivity
        if (activity != null) {
            activity.volumeControlStream = AudioManager.STREAM_MUSIC
        }
        enableSleepTimer(SpeakSettings.load().sleepTimer)
    }


    fun onEvent(ev: SpeakSettingsChangedEvent) {
        ttsServiceManager.updateSettings(ev)
        if (!isPaused && !isSpeaking) {
            // if playback is stopped, we want to update bookmark of the verse that we are currently reading (if any)
            if (ev.updateBookmark) {
                bookmarkControl.updateBookmarkSettings(ev.speakSettings.playbackSettings)
            }
        } else if (isSpeaking) {
            pause(true)
            if (ev.sleepTimerChanged) {
                enableSleepTimer(ev.speakSettings.sleepTimer)
            }
            continueAfterPause(true)
        }
    }

    fun getStatusText(showFlag: Int): String {
        return if (!isSpeaking && !isPaused) {
            "- " + BibleApplication.application.getString(R.string.speak_status_stopped) + " -"
        } else {
            ttsServiceManager.getStatusText(showFlag)
        }
    }

    private fun enableSleepTimer(sleepTimerAmount: Int) {
        stopTimer()
        if (sleepTimerAmount > 0) {
            Log.d(TAG, "Activating sleep timer")
            val app = BibleApplication.application
            ABEventBus.getDefault().post(ToastEvent(app.getString(R.string.sleep_timer_started, sleepTimerAmount)))
            timerTask = object : TimerTask() {
                override fun run() {
                    pause(false, false)
                    val s = SpeakSettings.load()
                    s.sleepTimer = 0
                    s.save()
                }
            }
            sleepTimer.schedule(timerTask, (sleepTimerAmount * 60000).toLong())
        }
    }

    private fun stopTimer() {
        if (timerTask != null) {
            timerTask!!.cancel()
        }
        timerTask = null
    }

    fun sleepTimerActive(): Boolean {
        return timerTask != null
    }

    fun speakFromBookmark(dto: BookmarkDto) {
        val book = dto.speakBook as SwordBook?;
        if (isSpeaking || isPaused) {
            stop(true)
        }
        if (book != null) {
            speakBible(book, dto.verseRange.start)
        } else {
            speakBible(dto.verseRange.start)
        }
    }

    companion object {

        private const val NUM_LEFT_IDX = 3
        private val BIBLE_PAGES_TO_SPEAK_DEFNS = arrayOf(NumPagesToSpeakDefinition(1, R.plurals.num_chapters, true, R.id.numChapters1), NumPagesToSpeakDefinition(2, R.plurals.num_chapters, true, R.id.numChapters2), NumPagesToSpeakDefinition(5, R.plurals.num_chapters, true, R.id.numChapters3), NumPagesToSpeakDefinition(10, R.string.rest_of_book, false, R.id.numChapters4))

        private val COMMENTARY_PAGES_TO_SPEAK_DEFNS = arrayOf(NumPagesToSpeakDefinition(1, R.plurals.num_verses, true, R.id.numChapters1), NumPagesToSpeakDefinition(2, R.plurals.num_verses, true, R.id.numChapters2), NumPagesToSpeakDefinition(5, R.plurals.num_verses, true, R.id.numChapters3), NumPagesToSpeakDefinition(10, R.string.rest_of_chapter, false, R.id.numChapters4))

        private val DEFAULT_PAGES_TO_SPEAK_DEFNS = arrayOf(NumPagesToSpeakDefinition(1, R.plurals.num_pages, true, R.id.numChapters1), NumPagesToSpeakDefinition(2, R.plurals.num_pages, true, R.id.numChapters2), NumPagesToSpeakDefinition(5, R.plurals.num_pages, true, R.id.numChapters3), NumPagesToSpeakDefinition(10, R.plurals.num_pages, true, R.id.numChapters4))

        private const val TAG = "SpeakControl"
    }
}
