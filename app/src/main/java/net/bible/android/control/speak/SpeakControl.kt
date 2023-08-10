/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
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
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.AndRuntimeException
import net.bible.service.common.CommonUtils
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.common.AdvancedSpeakSettings
import net.bible.service.device.speak.MediaButtonHandler
import net.bible.service.sword.BookAndKey

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class SpeakControl @Inject constructor(
    private val textToSpeechServiceManager: Lazy<TextToSpeechServiceManager>,
    private val windowControl: WindowControl,
) {

    @Inject lateinit var bookmarkControl: BookmarkControl
    private var sleepTimer = lazy { Timer("TTS sleep timer") }
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
                pageManager = windowControl.activeWindowPageManager
                _speakPageManager = pageManager
            }
            return pageManager
        }

    val isCurrentDocSpeakAvailable: Boolean
        get() {
            return try {
                val docLangCode = windowControl.activeWindowPageManager.currentPage.currentDocument?.language?.code
                if(docLangCode == null) return true else ttsServiceManager.isLanguageAvailable(docLangCode)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking TTS lang available")
                false
            }
        }

    val isSpeaking: Boolean
        get() = booksAvailable && ttsServiceManager.isSpeaking

    val isPaused: Boolean
        get() = booksAvailable && ttsServiceManager.isPaused

    val isStopped: Boolean
        get() = !isSpeaking && !isPaused

    private val currentBook: Book?
        get() = windowControl
                .activeWindowPageManager
                .currentPage
                .currentDocument

    val sleepTimerActivationTime: Date?
        get() = if (timerTask == null) {
            null
        } else {
            Date(timerTask!!.scheduledExecutionTime())
        }

    private val currentlyPlayingBook: Book?
        get() = if (!booksAvailable || !ttsInitialized) null else ttsServiceManager.currentlyPlayingBook

    private val currentlyPlayingVerse: Key?
        get() = if (!booksAvailable || !ttsInitialized) null else ttsServiceManager.currentlyPlayingKey

    init {
        ABEventBus.register(this)
        MediaButtonHandler.initialize(this)
    }

    protected fun finalize() {
        // Allow timer threads to be stopped on GC (good for tests)
        stopTimer()
        if(sleepTimer.isInitialized()) {
            sleepTimer.value.cancel()
        }
        MediaButtonHandler.release()
    }

    fun onEventMainThread(event: SpeakProgressEvent) {
        if (AdvancedSpeakSettings.synchronize) {
            val book = speakPageManager.currentPage.currentDocument
            speakPageManager.setCurrentDocumentAndKey(book, event.key)
        }
    }

    /** return a list of prompt ids for the speak screen associated with the current document type
     */
    fun calculateNumPagesToSpeakDefinitions(): Array<NumPagesToSpeakDefinition> {
        val definitions: Array<NumPagesToSpeakDefinition>

        val currentPage = windowControl.activeWindowPageManager.currentPage
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
        val page = windowControl.activeWindowPageManager.currentPage
        val currentVerse = page.singleKey as Verse

        // If we have range playback mode set up, and user starts playback not from within the range,
        // let's cancel the range playback mode.
        if(range != null && !(range.start.ordinal <= currentVerse.ordinal
                && range.end.ordinal >= currentVerse.ordinal)) {
            settings.playbackSettings.verseRange = null
            settings.save()
            ABEventBus.post(ToastEvent(
                messageId = R.string.verse_range_mode_disabled,
                duration = Toast.LENGTH_LONG
            ))
        }

    }

    /** Toggle speech - prepare to speak single page OR if speaking then stop speaking
     */
    fun toggleSpeak(preferLast: Boolean = false) {
        Log.i(TAG, "Speak toggle current page")
        // Continue
        when {
            isPaused -> {
                continueAfterPause()
                //Pause
            }
            isSpeaking -> {
                pause()
                // Start Speak
            }
            else -> {
                if(preferLast)
                    continueLastPosition()
                else
                    startSpeakingFromDefault()
            }
        }
    }

    private fun startSpeakingFromDefault() {
        Log.i(TAG, "startSpeakingFromDefault")
        if (!booksAvailable) {
            ABEventBus.post(ToastEvent(R.string.speak_no_books_available))
            return
        }
        try {
            val page = windowControl.activeWindowPageManager.currentPage
            if(!page.isSpeakable) {
                ABEventBus.post(ToastEvent(R.string.speak_no_books_available))
                return
            }
            val fromBook = page.currentDocument
            if (fromBook?.bookCategory == BookCategory.BIBLE) {
                resetPassageRepeatIfOutsideRange()
                speakBible()
            } else {
                speakText()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapters to speak", e)
            ABEventBus.post(ToastEvent(R.string.speak_general_error))
            return
        }
    }

    fun speakAny() {
        val page = speakPageManager.currentPage
        val fromBook = page.currentDocument
        if (fromBook?.bookCategory == BookCategory.BIBLE) {
            resetPassageRepeatIfOutsideRange()
            speakBible()
        } else {
            speakTextNg()
        }
    }

    // By this checking, try to avoid issues with isSpeaking and isPaused causing crash if window is not yet available
    // (such as headphone switching in the initial startup screen)
    private val booksAvailable: Boolean get() = SwordDocumentFacade.bibles.isNotEmpty()

    fun speakTextNg() {
        val page = windowControl.activeWindowPageManager.currentPage
        val fromBook = page.currentDocument
        val key =
            if(page is CurrentCommentaryPage)
                page.displayKey!!
            else page.key!!

        val bookAndKey = BookAndKey(
            key,
            fromBook,
            page.anchorOrdinal
        )
        speakTextNg(bookAndKey)
    }
    fun speakTextNg(bookAndKey: BookAndKey) {
        if (isPaused) {
            Log.i(TAG, "Clearing paused Speak text")
            stop()
        }

        prepareForSpeaking()

        ttsServiceManager.speakText(bookAndKey)
    }

    fun speakText() {
        val settings = SpeakSettings.load()
        val numPagesDefn = calculateNumPagesToSpeakDefinitions()[settings.numPagesToSpeakId]

        //, boolean queue, boolean repeat
        Log.i(TAG, "Chapters:" + numPagesDefn.numPages)
        // if a previous speak request is paused clear the cached text
        if (isPaused) {
            Log.i(TAG, "Clearing paused Speak text")
            stop()
        }

        prepareForSpeaking()

        val page = windowControl.activeWindowPageManager.currentPage
        val fromBook = page.currentDocument

        try {
            // first find keys to Speak
            val keyList = ArrayList<Key>()
            for (i in 0 until numPagesDefn.numPages) {
                val key = page.getPagePlus(i)
                keyList.add(key)
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

    fun speakBible(book: SwordBook, verse: Verse, force: Boolean = false) {
        // if a previous speak request is paused clear the cached text
        if (isPaused) {
            stop()
        }

        prepareForSpeaking()
        if(AdvancedSpeakSettings.synchronize || force) {
            speakPageManager.setCurrentDocumentAndKey(book, verse)
        }
        try {
            ttsServiceManager.speakBible(book, verse)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapters to speak", e)
            throw AndRuntimeException("Error preparing Speech", e)
        }

    }

    private fun speakBible() {
        val page = speakPageManager.currentPage
        if(!page.isSpeakable) {
            ABEventBus.post(ToastEvent(R.string.speak_no_books_available))
            return
        }
        speakBible(page.singleKey as Verse)
    }

    private fun speakBible(verse: Verse) {
        speakBible(currentBook as SwordBook, verse)
    }

    private fun speakBible(bookRef: String, osisRef: String) {
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
        Log.i(TAG, "Tell TTS to speak")
        ttsServiceManager.speakText(book, keyList, queue, repeat)
    }

    fun rewind(amount: SpeakSettings.RewindAmount? = null) {
        if (isSpeaking || isPaused) {
            Log.i(TAG, "Rewind TTS speaking")
            ttsServiceManager.rewind(amount)
            ABEventBus.post(ToastEvent(R.string.rewind))
        }
    }

    fun forward(amount: SpeakSettings.RewindAmount? = null) {
        if (isSpeaking || isPaused) {
            Log.i(TAG, "Forward TTS speaking")
            ttsServiceManager.forward(amount)
            ABEventBus.post(ToastEvent(R.string.forward))
        }
    }

    fun pause() {
        pause(false, true)
    }

    fun setupMockedTts() {
        ttsServiceManager.setupMockedTts()
    }

    fun pause(willContinueAfterThis: Boolean, toast: Boolean = !willContinueAfterThis) {
        if (!willContinueAfterThis) {
            stopTimer()
        }
        if (isSpeaking || isPaused) {
            Log.i(TAG, "Pause TTS speaking")
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
                ABEventBus.post(ToastEvent(pauseToastText))
            }
        }
        saveCurrentPosition()
    }

    private fun saveCurrentPosition() {
        val bookRef = currentlyPlayingBook?.initials
        val osisRef = currentlyPlayingVerse?.osisRef
        if(bookRef != null && osisRef != null) {
            CommonUtils.settings.setString("lastSpeakBook",bookRef);
            CommonUtils.settings.setString("lastSpeakRef",osisRef);
        } else {
            CommonUtils.settings.removeString("lastSpeakBook");
            CommonUtils.settings.removeString("lastSpeakRef");
        }
    }

    fun continueAfterPause() {
        continueAfterPause(false)
    }

    fun continueLastPosition() {
        val bookRef = CommonUtils.settings.getString("lastSpeakBook")
        val osisRef = CommonUtils.settings.getString("lastSpeakRef")
        Log.i(TAG, "continueLastPosition $bookRef $osisRef")
        if(bookRef != null && osisRef != null) speakBible(bookRef, osisRef)
        else startSpeakingFromDefault()
    }

    private fun continueAfterPause(automated: Boolean) {
        Log.i(TAG, "Continue TTS speaking after pause")
        if (!automated) {
            prepareForSpeaking()
        }
        ttsServiceManager.continueAfterPause()
    }

    fun stop(willContinueAfter: Boolean=false, force: Boolean=false) {
        if(!willContinueAfter) {
            _speakPageManager = null
        }

        if (!force && !isSpeaking && !isPaused) {
            return
        }

        Log.i(TAG, "Stop TTS speaking")
        ttsServiceManager.shutdown(willContinueAfter)
        saveCurrentPosition()
        stopTimer()
        if(!force) {
            ABEventBus.post(ToastEvent(R.string.stop))
        }
    }

    private fun prepareForSpeaking() {
        if(CommonUtils.isDiscrete) {
            GlobalScope.launch {
                CommonUtils.requestNotificationPermission()
            }
        }
        // ensure volume controls adjust correct stream - not phone which is the default
        // STREAM_TTS does not seem to be available but this article says use STREAM_MUSIC instead:
        // http://stackoverflow.com/questions/7558650/how-to-set-volume-for-text-to-speech-speak-method
        CommonUtils.settings.setLong("speak-last-used", System.currentTimeMillis())
        val activity = CurrentActivityHolder.currentActivity
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
                bookmarkControl.updateBookmarkPlaybackSettings(ev.speakSettings.playbackSettings)
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
            Log.i(TAG, "Activating sleep timer")
            val app = BibleApplication.application
            ABEventBus.post(ToastEvent(app.getString(R.string.sleep_timer_started, sleepTimerAmount)))
            timerTask = object : TimerTask() {
                override fun run() {
                    pause(false, false)
                    val s = SpeakSettings.load()
                    s.sleepTimer = 0
                    s.save()
                }
            }
            sleepTimer.value.schedule(timerTask, (sleepTimerAmount * 60000).toLong())
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

    fun speakFromBookmark(dto: BookmarkEntities.BaseBookmarkWithNotes) {
        if (isSpeaking || isPaused) {
            stop(true)
        }
        when(dto) {
            is BibleBookmarkWithNotes -> {
                val book = dto.speakBook as SwordBook?;
                if (book != null) {
                    speakBible(book, dto.verseRange.start)
                } else {
                    speakBible(dto.verseRange.start)
                }
            }
            is BookmarkEntities.GenericBookmarkWithNotes -> {
                speakTextNg(
                    BookAndKey(
                        dto.bookKey?: dto.originalKey!!,
                        dto.book,
                        OrdinalRange(dto.ordinalStart, dto.ordinalEnd)
                    )

                )
            }
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
