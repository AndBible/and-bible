/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.util.formatHoursMinsSecs
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.log.Log

import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.service.common.AndRuntimeException
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.service.device.speak.TextToSpeechServiceManager

import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange

import java.util.*


import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeySerialized
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class SpeakControl constructor(
    private val textToSpeechServiceManager: Lazy<TextToSpeechServiceManager>,
    private val windowControl: WindowControl,
    private val platform: SpeakPlatform,
    private val notifier: UserNotifier,
    private val strings: CoreStrings,
    private val settings: AppSettings,
    private val appScope: AppCoroutineScope,
    private val advancedSpeakSettings: AdvancedSpeakSettings,
    private val launcher: OrderedLauncher,
    internal val fakeBookFactory: net.bible.service.download.FakeBookFactory,
) : KoinComponent {

    val bookmarkControl: BookmarkControl by inject()
    private var sleepTimer = lazy { Timer("TTS sleep timer") }
    private var timerTask: TimerTask? = null
    private var _speakPageManager: CurrentPageManager? = null

    private var originalSettingsBeforeMemLoop: SpeakSettings? = null
    private var ttsInitialized = false
    private val ttsServiceManager: TextToSpeechServiceManager get () {
        if(!ttsInitialized)
            ttsInitialized = true
        return textToSpeechServiceManager.value
    }
    

    val speakPageManager: CurrentPageManager
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

    private val currentlyPlayingKey: Key?
        get() = if (!booksAvailable || !ttsInitialized) null else ttsServiceManager.currentlyPlayingKey

    init {
        // Singleton lifetime: never cancelled.
        SpeakChanges.changes.subscribeOnMain { change ->
            val event = change as? SpeakChange.Progress ?: return@subscribeOnMain
            speakKey = event.key
            speakBook = event.book
            // Don't synchronize page during memorization loop — stay in memorize view
            if (!isMemorizationLoop && (advancedSpeakSettings.synchronize || event.forceFollow)) {
                val book = speakPageManager.currentPage.currentDocument
                speakPageManager.setCurrentDocumentAndKey(book, event.key,false)
            }
        }
        // Synchronous, as `on` was: save() pauses, applies and continues before it returns.
        SpeakSettingsChanges.changes.subscribe { ev ->
            ttsServiceManager.updateSettings(ev)
            if (!isPaused && !isSpeaking) {
                // if playback is stopped, we want to update bookmark of the verse that we are currently reading (if any)
                if (ev.updateBookmark) {
                    val playback = ev.speakSettings.playbackSettings.copy()
                    val page = windowControl.activeWindowPageManager
                    if (page.currentPage.documentCategory == net.bible.android.control.page.DocumentCategory.BIBLE) {
                        val verse = page.currentBible.singleKey
                        launcher.launch(SPEAK_KEY) { bookmarkControl.updateBookmarkPlaybackSettings(verse, playback) }
                    }
                }
            } else if (isSpeaking) {
                pause(true)
                if (ev.sleepTimerChanged) {
                    enableSleepTimer(ev.speakSettings.sleepTimer)
                }
                continueAfterPause(true)
            }
        }
        platform.attachMediaControls(this)
    }

    protected fun finalize() {
        // Allow timer threads to be stopped on GC (good for tests)
        stopTimer()
        if(sleepTimer.isInitialized()) {
            sleepTimer.value.cancel()
        }
        platform.detachMediaControls()
    }

    private var speakBook: Book? = null
    private var speakKey: Key? = null
    val speakBookAndKey: BookAndKey? get() = speakKey?.let {BookAndKey(it, speakBook) }


    /** return a list of prompt ids for the speak screen associated with the current document type
     */
    private fun resetPassageRepeatIfOutsideRange() {
        val settings = SpeakSettings.load()
        val range = settings.playbackSettings.verseRange
        val page = windowControl.activeWindowPageManager.currentPage
        val currentVerse = page.singleKey as? Verse
        
        // If singleKey is null or we don't have range playback mode, nothing to reset
        if (currentVerse == null || range == null) {
            return
        }

        // If we have range playback mode set up, and user starts playback not from within the range,
        // let's cancel the range playback mode.
        if (!(range.start.ordinal <= currentVerse.ordinal
                && range.end.ordinal >= currentVerse.ordinal)) {
            settings.playbackSettings.verseRange = null
            settings.save()
            notifier.showInfo(strings.verseRangeModeDisabled, long = true)
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
            notifier.showInfo(strings.speakNoBooksAvailable)
            return
        }
        try {
            val page = windowControl.activeWindowPageManager.currentPage
            if(!page.isSpeakable) {
                notifier.showInfo(strings.speakNoBooksAvailable)
                return
            }
            val fromBook = page.currentDocument
            if (fromBook?.bookCategory == BookCategory.BIBLE) {
                resetPassageRepeatIfOutsideRange()
                speakBible()
            } else {
                speakGeneric()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapters to speak", e)
            notifier.showInfo(strings.speakGeneralError)
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
            speakGeneric()
        }
    }

    // By this checking, try to avoid issues with isSpeaking and isPaused causing crash if window is not yet available
    // (such as headphone switching in the initial startup screen)
    private val booksAvailable: Boolean get() = SwordDocumentFacade.bibles.isNotEmpty()

    private fun speakGeneric() {
        val page = windowControl.activeWindowPageManager.currentPage
        val fromBook = page.currentDocument
        val key =
            if(page is CurrentCommentaryPage)
                page.displayKey!!
            else page.key!!

        val bookAndKey = BookAndKey(
            key,
            fromBook,
            page.anchorOrdinal ?: OrdinalRange(0)
        )
        speakGeneric(bookAndKey)
    }
    fun speakGeneric(bookAndKey: BookAndKey) {
        if (isPaused) {
            Log.i(TAG, "Clearing paused Speak text")
            stop()
        }

        prepareForSpeaking()

        ttsServiceManager.speakGeneric(bookAndKey)
    }

    fun speakBible(book: SwordBook, verse: Verse, force: Boolean = false) {
        // if a previous speak request is paused clear the cached text
        if (isPaused) {
            stop()
        }

        prepareForSpeaking()
        if(advancedSpeakSettings.synchronize || force) {
            speakPageManager.setCurrentDocumentAndKey(book, verse,false)
        }
        try {
            ttsServiceManager.speakBible(book, verse)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapters to speak", e)
            throw AndRuntimeException("Error preparing Speech", e)
        }

    }

    val isMemorizationLoop: Boolean get() = originalSettingsBeforeMemLoop != null

    fun speakMemorizationLoop(book: SwordBook, startVerse: Verse, endVerse: Verse) {
        if (isPaused) {
            stop()
        }

        val originalSettings = SpeakSettings.load()
        originalSettingsBeforeMemLoop = originalSettings.makeCopy()

        val memSettings = originalSettings.makeCopy()
        memSettings.playbackSettings = memSettings.playbackSettings.copy(
            speakChapterChanges = false,
            speakTitles = false,
            speakFootnotes = false,
            verseRange = VerseRange(book.versification, startVerse, endVerse),
            isMemorizationLoop = true
        )
        memSettings.save()

        prepareForSpeaking()
        // Don't synchronize page — stay in memorize view
        try {
            ttsServiceManager.speakBible(book, startVerse)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting memorization loop speak", e)
            originalSettingsBeforeMemLoop?.save()
            originalSettingsBeforeMemLoop = null
            throw AndRuntimeException("Error preparing memorization loop speech", e)
        }
    }

    private fun speakBible() {
        val page = speakPageManager.currentPage
        if(!page.isSpeakable) {
            notifier.showInfo(strings.speakNoBooksAvailable)
            return
        }
        val verse = page.singleKey as? Verse
        if (verse == null) {
            Log.e(TAG, "Cannot speak Bible - no valid verse key available")
            notifier.showInfo(strings.speakGeneralError)
            return
        }
        speakBible(verse)
    }

    private fun speakBible(verse: Verse) {
        speakBible(currentBook as SwordBook, verse)
    }

    private fun speakAny(bookRef: String, osisRef: String) {
        try {
            val book = Books.installed().getBook(bookRef)
            if((book as? SwordBook)?.bookCategory == BookCategory.BIBLE) {
                val verse = (book.getKey(osisRef) as RangedPassage).getVerseAt(0)
                speakBible(book, verse)
            } else {
                val key = BookAndKeySerialized.fromJSON(osisRef).resolve(fakeBookFactory)
                speakGeneric(key)
            }
        } catch (e: NoSuchKeyException) {
            Log.e(TAG, "Key not found $osisRef in $bookRef", e)
            // Fall back to default behavior instead of crashing
            startSpeakingFromDefault()
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring speaking position", e)
            startSpeakingFromDefault()
        }
    }

    fun speakKeyListLegacy(book: Book, keyList: List<Key>, queue: Boolean) {
        prepareForSpeaking()

        // speak current chapter or stop speech if already speaking
        Log.i(TAG, "Tell TTS to speak")
        ttsServiceManager.speakTextLegacy(book, keyList, queue)
    }

    fun rewind(amount: SpeakSettings.RewindAmount? = null) {
        if (isSpeaking || isPaused) {
            Log.i(TAG, "Rewind TTS speaking")
            ttsServiceManager.rewind(amount)
            notifier.showInfo(strings.rewind)
        }
    }

    fun forward(amount: SpeakSettings.RewindAmount? = null) {
        if (isSpeaking || isPaused) {
            Log.i(TAG, "Forward TTS speaking")
            ttsServiceManager.forward(amount)
            notifier.showInfo(strings.forward)
        }
    }

    fun pause() {
        pause(willContinueAfterThis = false, toast = true)
    }



    fun pause(willContinueAfterThis: Boolean, toast: Boolean = !willContinueAfterThis) {
        if (!willContinueAfterThis) {
            stopTimer()
        }
        if (isSpeaking || isPaused) {
            Log.i(TAG, "Pause TTS speaking")
            val tts = ttsServiceManager
            tts.pause(willContinueAfterThis)
            var pauseToastText = strings.pause

            val completedSeconds = tts.pausedCompletedSeconds
            val totalSeconds = tts.pausedTotalSeconds

            if (totalSeconds > 0) {
                val timeProgress = formatHoursMinsSecs(completedSeconds) + "/" + formatHoursMinsSecs(totalSeconds)
                pauseToastText += "\n" + timeProgress
            }

            if (!willContinueAfterThis && toast) {
                notifier.showInfo(pauseToastText)
            }
            saveCurrentPosition()
        }
    }

    private fun saveCurrentPosition() {
        val bookRef = currentlyPlayingBook?.initials
        val key = currentlyPlayingKey
        val osisRef = if(key is BookAndKey) {
            key.serialized
        } else {
            key?.getOsisRef()
        }
        if(bookRef != null && osisRef != null) {
            settings.setString("lastSpeakBook",bookRef);
            settings.setString("lastSpeakRef", osisRef);
        } else {
            settings.removeString("lastSpeakBook");
            settings.removeString("lastSpeakRef");
        }
    }

    fun continueAfterPause() {
        continueAfterPause(false)
    }

    fun continueLastPosition() {
        val bookRef = settings.getString("lastSpeakBook")
        val osisRef = settings.getString("lastSpeakRef")
        Log.i(TAG, "continueLastPosition $bookRef $osisRef")
        if(bookRef != null && osisRef != null) speakAny(bookRef, osisRef)
        else startSpeakingFromDefault()
    }

    private fun continueAfterPause(automated: Boolean) {
        Log.i(TAG, "Continue TTS speaking after pause")
        if (!automated) {
            prepareForSpeaking()
        }
        if(!ttsServiceManager.initialized) {
            continueLastPosition()
        } else {
            ttsServiceManager.continueAfterPause(reloadBookmark = !automated)
        }
    }

    fun stop(willContinueAfter: Boolean=false, force: Boolean=false) {
        if(!willContinueAfter) {
            _speakPageManager = null
        }

        val wasStopped = !isSpeaking && !isPaused
        if (!force && wasStopped) {
            ttsServiceManager.invalidatePendingStart()
        } else {
            Log.i(TAG, "Stop TTS speaking")
            ttsServiceManager.shutdown(willContinueAfter)
            saveCurrentPosition()
        }
        stopTimer()
        originalSettingsBeforeMemLoop?.let {
            // Clear the loop owner before save() synchronously notifies settings listeners.
            originalSettingsBeforeMemLoop = null
            it.save()
        }
        if(!force && !wasStopped) {
            notifier.showInfo(strings.stop)
        }
    }

    private fun prepareForSpeaking() {
        platform.onPrepareForSpeaking()
        enableSleepTimer(SpeakSettings.load().sleepTimer)
    }



    fun getStatusText(showFlag: Int): String {
        return if (!isSpeaking && !isPaused) {
            "- " + strings.speakStatusStopped + " -"
        } else {
            ttsServiceManager.getStatusText(showFlag)
        }
    }

    private fun enableSleepTimer(sleepTimerAmount: Int) {
        stopTimer()
        if (sleepTimerAmount > 0) {
            Log.i(TAG, "Activating sleep timer")
            notifier.showInfo(strings.sleepTimerStarted(sleepTimerAmount))
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
                val ordinalStart = dto.ordinalStart ?: return
                speakGeneric(
                    BookAndKey(
                        dto.bookKey?: dto.originalKey!!,
                        dto.book,
                        OrdinalRange(ordinalStart)
                    )

                )
            }
        }
    }

    companion object {
        private const val TAG = "SpeakControl"
    }
}
