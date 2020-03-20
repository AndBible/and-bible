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

package net.bible.service.device.speak

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.phonecall.PhoneCallMonitor
import net.bible.android.control.event.phonecall.PhoneCallEvent
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.speak.SpeakSettingsChangedEvent
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakEvent.SpeakState

import net.bible.service.sword.SwordContentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse

import java.util.ArrayList
import java.util.Locale

import javax.inject.Inject


/**
 *
 * text-to-speech (TTS). Please note the following steps:
 *
 *
 *  1. Construct the TextToSpeech object.
 *  1. Handle initialization callback in the onInit method.
 * The activity implements TextToSpeech.OnInitListener for this purpose.
 *  1. Call TextToSpeech.speak to synthesize speech.
 *  1. Shutdown TextToSpeech in onDestroy.
 *
 *
 *
 * Documentation:
 * http://developer.android.com/reference/android/speech/tts/package-summary.html
 *
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class TextToSpeechServiceManager @Inject constructor(
		swordContentFacade: SwordContentFacade,
		bibleTraverser: BibleTraverser,
		windowControl: WindowControl,
		bookmarkControl: BookmarkControl,
		val speakControl: SpeakControl
) {

    private var mTts: TextToSpeech? = null

    private var localePreferenceList: MutableList<Locale> = ArrayList()
    private var currentLocale: Locale = Locale.getDefault()

    private var mSpeakTextProvider: SpeakTextProvider

    private val generalSpeakTextProvider: GeneralSpeakTextProvider
    private val bibleSpeakTextProvider: BibleSpeakTextProvider

    private val mSpeakTiming: SpeakTiming

    private val ttsLanguageSupport = TTSLanguageSupport()
    private var uniqueUtteranceNo: Long = 0

    // tts.isSpeaking() returns false when multiple text is queued on some older versions of Android so maintain it manually
    var isSpeaking = false
        private set

    var isPaused = false
        private set
    private var temporary = false
    private var mockedTts = false

    init {
        Log.d(TAG, "Creating TextToSpeechServiceManager")
        generalSpeakTextProvider = GeneralSpeakTextProvider(swordContentFacade)
        val book = windowControl.activeWindowPageManager.currentBible.currentDocument as SwordBook
        val verse = windowControl.activeWindowPageManager.currentBible.singleKey

        bibleSpeakTextProvider = BibleSpeakTextProvider(
                swordContentFacade, bibleTraverser, bookmarkControl,
                windowControl.windowRepository, book, verse
        )
        mSpeakTextProvider = bibleSpeakTextProvider

        mSpeakTiming = SpeakTiming()
        ABEventBus.getDefault().safelyRegister(this)
        restorePauseState()
    }

    /** only check timing when paused to prevent concurrency problems
     */
    val pausedTotalSeconds: Long
        get() = mSpeakTiming.getSecsForChars(mSpeakTextProvider.getTotalChars())

    val pausedCompletedSeconds: Long
        get() = mSpeakTiming.getSecsForChars(mSpeakTextProvider.getSpokenChars())

    private var wasPaused = false

    // Implements TextToSpeech.OnInitListener.
    private var onInitListener: TextToSpeech.OnInitListener = TextToSpeech.OnInitListener { status ->
        Log.d(TAG, "Tts initialised")
        var isOk = false

		val tts = mTts

        // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
        if (tts != null && status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "Tts initialisation succeeded")

            // Add earcons
            tts.addEarcon(EARCON_PRE_FOOTNOTE, BibleApplication.application.packageName, R.raw.short_pling) // TODO: change
            tts.addEarcon(EARCON_POST_FOOTNOTE, BibleApplication.application.packageName, R.raw.short_pling_reverse)
            tts.addEarcon(EARCON_PRE_TITLE, BibleApplication.application.packageName, R.raw.pageflip)
            tts.addEarcon(EARCON_PRE_CHAPTER_CHANGE, BibleApplication.application.packageName, R.raw.medium_pling)
            tts.addEarcon(EARCON_PRE_BOOK_CHANGE, BibleApplication.application.packageName, R.raw.long_pling)

            // set speech rate
            setRate(SpeakSettings.load().playbackSettings.speed)

            var localeOK = false
            var locale: Locale? = null
            var i = 0
            while (i < localePreferenceList.size && !localeOK) {
                locale = localePreferenceList[i]
                Log.d(TAG, "Checking for locale:$locale")
                val result = tts.setLanguage(locale)
                localeOK = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                if (localeOK) {
                    Log.d(TAG, "Successful locale:$locale")
                    currentLocale = locale
                }
                i++
            }

            if (!localeOK) {
                Log.e(TAG, "TTS missing or not supported")
                // Language data is missing or the language is not supported.
                ttsLanguageSupport.addUnsupportedLocale(locale)
                showError(R.string.tts_lang_not_available, Exception("Tts missing or not supported"))
            } else {
                // The TTS engine has been successfully initialized.
                ttsLanguageSupport.addSupportedLocale(locale)
                val ok = tts.setOnUtteranceProgressListener(utteranceProgressListener)
                if (ok == TextToSpeech.ERROR) {
                    Log.e(TAG, "Error registering utteranceProgressListener")
                } else {
                    isOk = true
                    mSpeakTextProvider.prepareForStartSpeaking()
                    startSpeaking()
                    stopIfPhoneCall()
                }
            }
        } else {
            Log.d(TAG, "Tts initialisation failed")
            showError(R.string.error_occurred, Exception("Tts Initialisation failed"))
        }

        if (!isOk) {
            speakControl.stop(false, true)
        }
    }

    private val utteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            Log.d(TAG, "onStart $utteranceId")
            mSpeakTextProvider.startUtterance(utteranceId)
            mSpeakTiming.started(utteranceId, mSpeakTextProvider.getText(utteranceId).length)
        }

        override fun onDone(utteranceId: String) {
            Log.d(TAG, "onUtteranceCompleted:$utteranceId")
            // pause/rew/ff can sometimes allow old messages to complete so need to prevent move to next sentence if completed utterance is out of date

            // estimate cps
            mSpeakTextProvider.finishedUtterance(utteranceId)
            mSpeakTiming.finished(utteranceId)

            if (!isPaused && isSpeaking && StringUtils.startsWith(utteranceId, UTTERANCE_PREFIX)) {
                val utteranceNo = java.lang.Long.valueOf(StringUtils.removeStart(utteranceId, UTTERANCE_PREFIX))
                if (utteranceNo == uniqueUtteranceNo - 1) {

                    // ask TTs to say the text
                    if (mSpeakTextProvider.isMoreTextToSpeak()) {
                        speakNextChunk()
                    } else {
                        Log.d(TAG, "Shutting down TTS")
                        speakControl.stop()
                    }
                }
            }
        }

        override fun onError(utteranceId: String) {
            Log.d(TAG, "onError $utteranceId")
        }
    }

    val currentlyPlayingVerse: Verse?
        get() = mSpeakTextProvider.getCurrentlyPlayingVerse()

    val currentlyPlayingBook: Book?
        get() = mSpeakTextProvider.getCurrentlyPlayingBook()

    fun isLanguageAvailable(langCode: String): Boolean {
        return ttsLanguageSupport.isLangKnownToBeSupported(langCode)
    }

    @Synchronized
    fun speakBible(book: SwordBook, verse: Verse) {
        switchProvider(bibleSpeakTextProvider)
        clearTtsQueue()
        bibleSpeakTextProvider.setupReading(book, verse)
        localePreferenceList = calculateLocalePreferenceList(book)
        initializeTtsOrStartSpeaking()
    }

    @Synchronized
    fun speakText(book: Book, keyList: List<Key>, queue: Boolean, repeat: Boolean) {
        switchProvider(generalSpeakTextProvider)
        generalSpeakTextProvider.setupReading(book, keyList, repeat)
        handleQueue(queue)
        localePreferenceList = calculateLocalePreferenceList(book)
        initializeTtsOrStartSpeaking()
    }

    private fun switchProvider(newProvider: SpeakTextProvider) {
        if (newProvider !== mSpeakTextProvider) {
            mSpeakTextProvider.reset()
            mSpeakTextProvider = newProvider
        }
    }

    private fun handleQueue(queue: Boolean) {
        if (!queue) {
            Log.d(TAG, "Queue is false so requesting stop")
            clearTtsQueue()
        } else if (isPaused) {
            Log.d(TAG, "New speak request while paused so clearing paused speech")
            clearTtsQueue()
            isPaused = false
        }
    }

    private fun calculateLocalePreferenceList(fromBook: Book): MutableList<Locale> {
        //calculate preferred locales to use for speech
        // Set preferred language to the same language as the book.
        // Note that a language may not be available, and so we have a preference list
        var bookLanguageCode = fromBook.language.code
        Log.d(TAG, "Book has language code:$bookLanguageCode")

        val localePreferenceList = ArrayList<Locale>()
        if (bookLanguageCode == Locale.getDefault().language) {
            // for people in UK the UK accent is preferable to the US accent
            localePreferenceList.add(Locale.getDefault())
        }

        // try to get the native country for the lang
        val countryCode = getDefaultCountryCode(bookLanguageCode)
        if (countryCode != null) {
            localePreferenceList.add(Locale(bookLanguageCode, countryCode))
        }

        // Speak ancient greek with modern greece.
        if (bookLanguageCode == "grc") {
            bookLanguageCode = "el"
        }

        // finally just add the language of the book
        localePreferenceList.add(Locale(bookLanguageCode))
        return localePreferenceList
    }

    private fun getDefaultCountryCode(language: String): String? {
        if (language == "en") return Locale.UK.country
        if (language == "fr") return Locale.FRANCE.country
        if (language == "de") return Locale.GERMANY.country
        if (language == "zh") return Locale.CHINA.country
        if (language == "it") return Locale.ITALY.country
        if (language == "jp") return Locale.JAPAN.country
        if (language == "ko") return Locale.KOREA.country
        if (language == "hu") return "HU"
        if (language == "cs") return "CZ"
        if (language == "fi") return "FI"
        if (language == "pl") return "PL"
        if (language == "pt") return "PT"
        if (language == "ru") return "RU"
        return if (language == "tr") "TR" else null
    }

    fun setupMockedTts() {
        // for tests
        mockedTts = true
        onInitListener = TextToSpeech.OnInitListener {
            mSpeakTextProvider.prepareForStartSpeaking()
            startSpeaking()
            stopIfPhoneCall()
        }
    }

    private fun initializeTtsOrStartSpeaking() {
        if (mTts == null) {
            Log.d(TAG, "mTts was null so initialising Tts")

            try {
                // Initialize text-to-speech. This is an asynchronous operation.
                // The OnInitListener (second argument) (this class) is called after initialization completes.
                mTts = TextToSpeech(BibleApplication.application.applicationContext, this.onInitListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error initialising Tts", e)
                showError(R.string.error_occurred, e)
            }

        } else {
            startSpeaking()
        }
    }

    /**
     * Add event listener to stop on call
     */
    private fun stopIfPhoneCall() {
        PhoneCallMonitor.ensureMonitoringStarted()
    }

    @Synchronized
    fun rewind(amount: SpeakSettings.RewindAmount?) {
        Log.d(TAG, "Rewind TTS")
        // prevent onUtteranceCompleted causing next text to be grabbed
        uniqueUtteranceNo++
        val wasPaused = isPaused
        isPaused = true
        if (isSpeaking) {
            mTts?.stop()
        }
        isSpeaking = false

        if (!wasPaused) {
            // ensure current position is saved which is done during pause
            mSpeakTextProvider.savePosition(mSpeakTiming.fractionCompleted)
        }

        // move current position back a bit
        mSpeakTextProvider.rewind(amount)

        isPaused = wasPaused
        if (!isPaused) {
            continueAfterPause()
        }
    }

    @Synchronized
    fun forward(amount: SpeakSettings.RewindAmount?) {
        Log.d(TAG, "Forward TTS")
        // prevent onUtteranceCompleted causing next text to be grabbed
        uniqueUtteranceNo++
        val wasPaused = isPaused
        isPaused = true
        if (isSpeaking) {
            mTts?.stop()
        }
        isSpeaking = false

        if (!wasPaused) {
            // ensure current position is saved which is done during pause
            mSpeakTextProvider.savePosition(mSpeakTiming.fractionCompleted)
        }

        mSpeakTextProvider.forward(amount)

        isPaused = wasPaused
        if (!isPaused) {
            continueAfterPause()
        }
    }

    @Synchronized
    fun pause(willContinueAfterThis: Boolean) {
        Log.d(TAG, "Pause TTS")

        if (isSpeaking) {
            isPaused = true
            isSpeaking = false

            mSpeakTextProvider.savePosition(mSpeakTiming.fractionCompleted)
            mSpeakTextProvider.pause()

            if (willContinueAfterThis) {
                clearTtsQueue()
                mTts?.stop()
            } else {
                //kill the tts engine because it could be a long ime before restart and the engine may
                // become corrupted or used elsewhere
                shutdownTtsEngine()
            }

            fireStateChangeEvent()
        }
    }

    @Synchronized
    fun continueAfterPause() {
        try {
            Log.d(TAG, "continue after pause")
            isPaused = false
            clearPauseState()
            // ask TTs to say the text
            initializeTtsOrStartSpeaking()
        } catch (e: Exception) {
            Log.e(TAG, "TTS Error continuing after Pause", e)
            mSpeakTextProvider.reset()
            isSpeaking = false
            speakControl.stop()
        }

        // should be able to clear this because we are now speaking
        isPaused = false
    }

    private fun startSpeaking() {
        Log.d(TAG, "about to send some text to TTS")
        if (!isSpeaking) {
            speakNextChunk()
            isSpeaking = true
            isPaused = false
            mSpeakTextProvider.isSpeaking = true
            fireStateChangeEvent()
        } else {
            isPaused = false
        }
    }

    private fun speakNextChunk() {
        var utteranceId = ""
        Log.d(TAG, "Adding items to TTS queue. first utterance id: $uniqueUtteranceNo")
        for (i in 0 until mSpeakTextProvider.numItemsToTts) {
            utteranceId = UTTERANCE_PREFIX + uniqueUtteranceNo++
            val cmd = mSpeakTextProvider.getNextSpeakCommand(utteranceId, i == 0)
            if (!mockedTts) {
                cmd.speak(mTts!!, utteranceId)
            }
        }
        Log.d(TAG, "Added items to TTS queue. Last utterance id: $utteranceId")
    }


    /** flush cached text
     */
    private fun clearTtsQueue() {
        Log.d(TAG, "Stop TTS")

        // Don't forget to shutdown!
        if (isSpeaking) {
            Log.d(TAG, "Flushing speech")
            // flush remaining text
            mTts?.speak(" ", TextToSpeech.QUEUE_FLUSH, null)
        }

        mSpeakTextProvider.reset()
        isSpeaking = false
    }

    private fun showError(msgId: Int, e: Exception) {
        Dialogs.instance.showErrorMsg(msgId)
    }

    fun shutdown(willContinueAfter: Boolean) {
        Log.d(TAG, "Shutdown TTS")

        isSpeaking = false
        isPaused = false
        temporary = willContinueAfter

        // tts.stop can trigger onUtteranceCompleted so set above flags first to avoid sending of a further text and setting isSpeaking to true
        shutdownTtsEngine()
        mSpeakTextProvider.stop(willContinueAfter)
        clearPauseState()
        fireStateChangeEvent()
    }

    private fun shutdownTtsEngine() {
        Log.d(TAG, "Shutdown TTS Engine")
        try {
            // Don't forget to shutdown!
			try {
				mTts?.stop()
			} catch (e: Exception) {
				Log.e(TAG, "Error stopping Tts engine", e)
			}
			mTts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down Tts engine", e)
        } finally {
            mTts = null
        }
    }

    private fun fireStateChangeEvent() {
		when {
			isPaused -> {
				temporary = false
				ABEventBus.getDefault().post(SpeakEvent(SpeakState.PAUSED))
			}
			isSpeaking -> {
				temporary = false
				ABEventBus.getDefault().post(SpeakEvent(SpeakState.SPEAKING))
			}
			else -> ABEventBus.getDefault().post(SpeakEvent(if (temporary) SpeakState.TEMPORARY_STOP else SpeakState.SILENT))
		}

    }

    /**
     * Pause speak if phone call starts
     */
    fun onEvent(event: PhoneCallEvent) {
        if (event.callActivating) {
            if (isSpeaking) {
                wasPaused = true
                pause(false)
            }
            if (isPaused) {
                persistPauseState()
            } else {
                // ensure a previous pause does not hang around and be restored incorrectly
                clearPauseState()
            }
        } else {
            if (isPaused && wasPaused) {
                wasPaused = false
                continueAfterPause()
            }
        }
    }

    /** persist and restore pause state to allow pauses to continue over an app exit
     */
    private fun persistPauseState() {
        Log.d(TAG, "Persisting Pause state")
        val isBible = mSpeakTextProvider === bibleSpeakTextProvider

        mSpeakTextProvider.persistState()
        CommonUtils.sharedPreferences
                .edit()
                .putString(PERSIST_LOCALE_KEY, currentLocale.toString())
                .putBoolean(PERSIST_BIBLE_PROVIDER, isBible)
                .apply()
    }

    private fun restorePauseState() {
        // ensure no relevant current state is overwritten accidentally
        if (!isSpeaking && !isPaused) {
            Log.d(TAG, "Attempting to restore any Persisted Pause state")
            val isBible = CommonUtils.sharedPreferences.getBoolean(PERSIST_BIBLE_PROVIDER, true)
            switchProvider(if (isBible) bibleSpeakTextProvider else generalSpeakTextProvider)

            isPaused = mSpeakTextProvider.restoreState()

            // restore locale information so tts knows which voice to load when it initialises
            currentLocale = Locale(CommonUtils.sharedPreferences.getString(PERSIST_LOCALE_KEY, Locale.getDefault().toString()))
            localePreferenceList = ArrayList()
            localePreferenceList.add(currentLocale)
        }
    }

    private fun clearPauseState() {
        Log.d(TAG, "Clearing Persisted Pause state")
        mSpeakTextProvider.clearPersistedState()
        CommonUtils.sharedPreferences.edit().remove(PERSIST_LOCALE_KEY).apply()
    }

    private fun setRate(speechRate: Int) {
		mTts?.setSpeechRate(speechRate / 100f)
    }

    fun getStatusText(showFlag: Int): String {
        return mSpeakTextProvider.getStatusText(showFlag)
    }

    fun updateSettings(ev: SpeakSettingsChangedEvent) {
        mSpeakTextProvider.updateSettings(ev)
        setRate(ev.speakSettings.playbackSettings.speed)

    }

    companion object {

        private const val TAG = "Speak"
        private const val PERSIST_LOCALE_KEY = "SpeakLocale"
        private const val PERSIST_BIBLE_PROVIDER = "SpeakBibleProvider"
        var EARCON_PRE_FOOTNOTE = "[pre-footnote]"
        var EARCON_POST_FOOTNOTE = "[post-footnote]"
        var EARCON_PRE_TITLE = "[pre-title]"
        var EARCON_PRE_CHAPTER_CHANGE = "[pre-chapter-change]"
        var EARCON_PRE_BOOK_CHANGE = "[pre-book-change]"

        private const val UTTERANCE_PREFIX = "AND-BIBLE-"
    }
}
