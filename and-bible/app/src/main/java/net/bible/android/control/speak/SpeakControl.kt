package net.bible.android.control.speak

import android.media.AudioManager
import android.util.Log
import android.widget.Toast
import dagger.Lazy
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.AndRuntimeException
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import java.util.*
import javax.inject.Inject

/**
 * Handle requests from the Speak screen and Speak button.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * *
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
@ApplicationScope
class SpeakControl @Inject
constructor(private val textToSpeechServiceManager: Lazy<TextToSpeechServiceManager>,
            private val swordContentFacade: SwordContentFacade,
            private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider) {

    /** return a list of prompt ids for the speak screen associated with the current document type
     */
    fun calculateNumPagesToSpeakDefinitions(): Array<NumPagesToSpeakDefinition> {
        val definitions: Array<NumPagesToSpeakDefinition>

        val currentPage = activeWindowPageManagerProvider.activeWindowPageManager.currentPage
        val bookCategory = currentPage.currentDocument.bookCategory
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

    /** Toggle speech - prepare to speak single page OR if speaking then stop speaking
     */
    fun speakToggleCurrentPage() {
        Log.d(TAG, "Speak toggle current page")

        // Continue
        if (isPaused) {
            continueAfterPause()
            //Pause
        } else if (isSpeaking) {
            pause()
            // Start Speak
        } else {
            try {
                val page = activeWindowPageManagerProvider.activeWindowPageManager.currentPage
                val fromBook = page.currentDocument
                // first find keys to Speak
                val keyList = ArrayList<Key>()
                keyList.add(page.key)

                speak(fromBook, keyList, true, false)

                Toast.makeText(BibleApplication.getApplication(), R.string.speak, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting chapters to speak", e)
                throw AndRuntimeException("Error preparing Speech", e)
            }

        }
    }

    val isCurrentDocSpeakAvailable: Boolean
        get() {
            var isAvailable: Boolean
            try {
                val docLangCode = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument.language.code
                isAvailable = textToSpeechServiceManager.get().isLanguageAvailable(docLangCode)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking TTS lang available")
                isAvailable = false
            }

            return isAvailable
        }

    val isSpeaking: Boolean
        get() = textToSpeechServiceManager.get().isSpeaking

    val isPaused: Boolean
        get() = textToSpeechServiceManager.get().isPaused

    /** prepare to speak
     */
    fun speak(numPagesDefn: NumPagesToSpeakDefinition, queue: Boolean, repeat: Boolean) {
        Log.d(TAG, "Chapters:" + numPagesDefn.numPages)
        // if a previous speak request is paused clear the cached text
        if (isPaused) {
            Log.d(TAG, "Clearing paused Speak text")
            stop()
        }

        val page = activeWindowPageManagerProvider.activeWindowPageManager.currentPage
        val fromBook = page.currentDocument
        // first find keys to Speak
        val keyList = ArrayList<Key>()
        try {
            for (i in 0..numPagesDefn.numPages - 1) {
                val key = page.getPagePlus(i)
                if (key != null) {
                    keyList.add(key)
                }
            }

            speak(fromBook, keyList, queue, repeat)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapters to speak", e)
            throw AndRuntimeException("Error preparing Speech", e)
        }

    }

    fun speak(book: Book, keyList: List<Key>, queue: Boolean, repeat: Boolean) {
        Log.d(TAG, "Keys:" + keyList.size)
        // build a string containing the text to be spoken
        val textToSpeak = ArrayList<String>()

        // first concatenate the number of required chapters
        try {
            for (key in keyList) {
                // intro
                textToSpeak.add(key.name + ". ")
                //				textToSpeak.add("\n");

                // content
                textToSpeak.add(swordContentFacade.getTextToSpeak(book, key))

                // add a pause at end to separate passages
                textToSpeak.add("\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapters to speak", e)
            throw AndRuntimeException("Error preparing Speech", e)
        }

        // if repeat was checked then concatenate with itself
        if (repeat) {
            textToSpeak.add("\n")
            textToSpeak.addAll(textToSpeak)
        }

        speak(textToSpeak, book, queue)
    }

    /** prepare to speak
     */
    private fun speak(textsToSpeak: List<String>, fromBook: Book, queue: Boolean) {

        val localePreferenceList = calculateLocalePreferenceList(fromBook)

        preSpeak()

        // speak current chapter or stop speech if already speaking
        Log.d(TAG, "Tell TTS to speak")
        textToSpeechServiceManager.get().speak(localePreferenceList, textsToSpeak, queue)
    }

    fun rewind() {
        if (isSpeaking || isPaused) {
            Log.d(TAG, "Rewind TTS speaking")
            textToSpeechServiceManager.get().rewind()
            Toast.makeText(BibleApplication.getApplication(), R.string.rewind, Toast.LENGTH_SHORT).show()
        }
    }

    fun forward() {
        if (isSpeaking || isPaused) {
            Log.d(TAG, "Forward TTS speaking")
            textToSpeechServiceManager.get().forward()
            Toast.makeText(BibleApplication.getApplication(), R.string.forward, Toast.LENGTH_SHORT).show()
        }
    }

    fun pause() {
        if (isSpeaking || isPaused) {
            Log.d(TAG, "Pause TTS speaking")
            val tts = textToSpeechServiceManager.get()
            tts.pause()
            val pause = CommonUtils.getResourceString(R.string.pause)
            val timeProgress = CommonUtils.getHoursMinsSecs(tts.pausedCompletedSeconds) + "/" + CommonUtils.getHoursMinsSecs(tts.pausedTotalSeconds)
            Toast.makeText(BibleApplication.getApplication(), pause + "\n" + timeProgress, Toast.LENGTH_SHORT).show()
        }
    }

    fun continueAfterPause() {
        Log.d(TAG, "Continue TTS speaking after pause")
        preSpeak()
        textToSpeechServiceManager.get().continueAfterPause()
        Toast.makeText(BibleApplication.getApplication(), R.string.speak, Toast.LENGTH_SHORT).show()
    }

    fun stop() {
        Log.d(TAG, "Stop TTS speaking")
        doStop()
        Toast.makeText(BibleApplication.getApplication(), R.string.stop, Toast.LENGTH_SHORT).show()
    }

    private fun doStop() {
        textToSpeechServiceManager.get().shutdown()
    }

    private fun preSpeak() {
        // ensure volume controls adjust correct stream - not phone which is the default
        // STREAM_TTS does not seem to be available but this article says use STREAM_MUSIC instead: http://stackoverflow.com/questions/7558650/how-to-set-volume-for-text-to-speech-speak-method
        CurrentActivityHolder.getInstance().currentActivity.volumeControlStream = AudioManager.STREAM_MUSIC

    }

    private fun calculateLocalePreferenceList(fromBook: Book): List<Locale> {
        //calculate preferred locales to use for speech
        // Set preferred language to the same language as the book.
        // Note that a language may not be available, and so we have a preference list
        val bookLanguageCode = fromBook.language.code
        Log.d(TAG, "Book has language code:" + bookLanguageCode)

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
        if (language == "tr") return "TR"
        return null
    }

    companion object {
        private val NUM_LEFT_IDX = 3

        private val BIBLE_PAGES_TO_SPEAK_DEFNS = arrayOf(
                NumPagesToSpeakDefinition(1, R.plurals.num_chapters, true, R.id.numChapters1),
                NumPagesToSpeakDefinition(2, R.plurals.num_chapters, true, R.id.numChapters2),
                NumPagesToSpeakDefinition(5, R.plurals.num_chapters, true, R.id.numChapters3),
                NumPagesToSpeakDefinition(10, R.string.rest_of_book, false, R.id.numChapters4))

        private val COMMENTARY_PAGES_TO_SPEAK_DEFNS = arrayOf(
                NumPagesToSpeakDefinition(1, R.plurals.num_verses, true, R.id.numChapters1),
                NumPagesToSpeakDefinition(2, R.plurals.num_verses, true, R.id.numChapters2),
                NumPagesToSpeakDefinition(5, R.plurals.num_verses, true, R.id.numChapters3),
                NumPagesToSpeakDefinition(10, R.string.rest_of_chapter, false, R.id.numChapters4))

        private val DEFAULT_PAGES_TO_SPEAK_DEFNS = arrayOf(
                NumPagesToSpeakDefinition(1, R.plurals.num_pages, true, R.id.numChapters1),
                NumPagesToSpeakDefinition(2, R.plurals.num_pages, true, R.id.numChapters2),
                NumPagesToSpeakDefinition(5, R.plurals.num_pages, true, R.id.numChapters3),
                NumPagesToSpeakDefinition(10, R.plurals.num_pages, true, R.id.numChapters4))

        private val TAG = "SpeakControl"
    }
}
