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

import android.util.Log
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.SpeakSettingsChangedEvent
import net.bible.android.database.bookmarks.SpeakSettings.RewindAmount
import net.bible.service.common.AndRuntimeException
import net.bible.service.common.CommonUtils.settings
import net.bible.service.device.speak.event.SpeakProgressEvent
import net.bible.service.sword.SwordContentFacade.getTextToSpeak
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import java.text.BreakIterator
import java.util.*
import java.util.regex.Pattern

/** Keep track of a list of chunks of text being fed to TTS
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class GeneralSpeakTextProvider : SpeakTextProvider {
    private var mTextToSpeak: MutableList<String?>? = ArrayList()
    private var nextTextToSpeak: Long = 0

    // this fraction supports pause/rew/ff; if o then speech occurs normally, if 0.5 then next speech chunk is half completed...
    private var fractionOfNextSentenceSpoken = 0.0
    private var currentText = ""
    private var book: Book? = null
    private var keyList: List<Key>? = null
    override fun startUtterance(utteranceId: String) {
        if (keyList != null && keyList!!.size > 0) {
            ABEventBus.getDefault().post(SpeakProgressEvent(book!!, keyList!![0],
                TextCommand(currentText, TextCommand.TextType.NORMAL)))
            ABEventBus.getDefault().post(SpeakProgressEvent(book!!, keyList!![0],
                TextCommand(book!!.name, TextCommand.TextType.TITLE)))
        }
    }

    override val numItemsToTts: Int
        get() = 1

    override fun getStatusText(showFlag: Int): String {
        return if (keyList != null && keyList!!.size > 0) {
            keyList!![0].name
        } else {
            ""
        }
    }

    override fun updateSettings(speakSettingsChangedEvent: SpeakSettingsChangedEvent) {}
    override fun getCurrentlyPlayingVerse(): Verse? {
        return null
    }

    override fun getCurrentlyPlayingBook(): Book? {
        return null
    }

    override var isSpeaking: Boolean
        get() = false
        set(isSpeaking) {}

    private class StartPos {
        var found = false
        var startPosition = 0
        var text = ""
        var actualFractionOfWhole = 1f
    }

    private fun setupReading(textsToSpeak: List<String>) {
        for (text in textsToSpeak) {
            mTextToSpeak!!.addAll(breakUpText(text))
        }
        Log.i(TAG, "Total Num blocks in speak queue:" + mTextToSpeak!!.size)
    }

    fun setupReading(book: Book?, keyList: List<Key>, repeat: Boolean) {
        this.book = book
        Log.i(TAG, "Keys:" + keyList.size)
        // build a string containing the text to be spoken
        val textToSpeak: MutableList<String> = ArrayList()
        this.keyList = keyList
        // first concatenate the number of required chapters
        try {
            for (key in keyList) {
                // intro
                textToSpeak.add(key.name + ". ")

                // content
                textToSpeak.add(getTextToSpeak(book!!, key))

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

        // speak current chapter or stop speech if already speaking
        setupReading(textToSpeak)
    }

    override fun isMoreTextToSpeak(): Boolean {
        //TODO: there seems to be an occasional problem when using ff/rew/pause in the last chunk
        return nextTextToSpeak < mTextToSpeak!!.size
    }

    override fun getNextSpeakCommand(utteranceId: String, isCurrent: Boolean): SpeakCommand {
        var text = nextTextChunk

        // if a pause occurred then skip the first part
        if (fractionOfNextSentenceSpoken > 0) {
            Log.i(TAG, "Getting part of text to read.  Fraction:$fractionOfNextSentenceSpoken")
            val textFraction = getPrevTextStartPos(text, fractionOfNextSentenceSpoken)
            if (textFraction.found) {
                fractionOfNextSentenceSpoken = textFraction.actualFractionOfWhole.toDouble()
                text = textFraction.text
            } else {
                Log.e(TAG, "Eror finding next text. fraction:$fractionOfNextSentenceSpoken")
                // try to prevent recurrence of error, but do not say anything
                fractionOfNextSentenceSpoken = 0.0
                text = ""
            }
        }
        currentText = text
        return TextCommand(text, TextCommand.TextType.NORMAL)
    }

    override fun getText(utteranceId: String): String {
        return currentText
    }

    private val nextTextChunk: String
        private get() {
            val text = peekNextTextChunk()
            nextTextToSpeak++
            return text!!
        }

    private fun peekNextTextChunk(): String? {
        if (!isMoreTextToSpeak()) {
            Log.e(TAG, "Error: passed end of Speaktext.  nextText:" + nextTextToSpeak + " textToSpeak size:" + mTextToSpeak!!.size)
            return ""
        }
        return mTextToSpeak!![nextTextToSpeak.toInt()]
    }

    /** fractionCompleted may be a fraction of a fraction of the current block if this is not the first pause in this block
     *
     * @param fractionCompleted of last block of text returned by getNextSpeakCommand
     */
    override fun savePosition(fractionCompleted: Double) {
        Log.i(TAG, "Pause CurrentSentence:$nextTextToSpeak")

        // accumulate these fractions until we reach the end of a chunk of text
        // if pause several times the fraction of text completed becomes a fraction of the fraction left i.e. 1-previousFractionCompleted
        // also ensure the fraction is never greater than 1/all text
        fractionOfNextSentenceSpoken += Math.min(1.0,
            (1.0 - fractionOfNextSentenceSpoken) * fractionCompleted)
        Log.i(TAG, "Fraction of current sentence spoken:$fractionOfNextSentenceSpoken")
        backOneChunk()
    }

    override fun pause() {}
    override fun stop() {
        reset()
    }

    override fun rewind(amount: RewindAmount?) {
        // go back to start of current sentence
        var textFraction = getPrevTextStartPos(peekNextTextChunk(), fractionOfNextSentenceSpoken)

        // if could not find a previous sentence end
        if (!textFraction.found) {
            if (backOneChunk()) {
                textFraction = getPrevTextStartPos(peekNextTextChunk(), 1.0)
            }
        } else {
            // go back a little bit further in the current chunk
            val extraFraction = getPrevTextStartPos(peekNextTextChunk(), getStartPosFraction(textFraction.startPosition, peekNextTextChunk()).toDouble())
            if (extraFraction.found) {
                textFraction = extraFraction
            }
        }
        if (textFraction.found) {
            fractionOfNextSentenceSpoken = textFraction.actualFractionOfWhole.toDouble()
        } else {
            Log.e(TAG, "Could not rewind")
        }
        Log.i(TAG, "Rewind chunk length start position:$fractionOfNextSentenceSpoken")
    }

    override fun forward(amount: RewindAmount?) {
        Log.i(TAG, "Forward nextText:$nextTextToSpeak")

        // go back to start of current sentence
        var textFraction = getForwardTextStartPos(peekNextTextChunk(), fractionOfNextSentenceSpoken)

        // if could not find the next sentence start
        if (!textFraction.found && forwardOneChunk()) {
            textFraction = getForwardTextStartPos(peekNextTextChunk(), 0.0)
        }
        if (textFraction.found) {
            fractionOfNextSentenceSpoken = textFraction.actualFractionOfWhole.toDouble()
        } else {
            Log.e(TAG, "Could not forward")
        }
        Log.i(TAG, "Forward chunk length start position:$fractionOfNextSentenceSpoken")
    }

    override fun finishedUtterance(utteranceId: String) {
        // reset pause info as a chunk is now finished and it may have been started using continue
        fractionOfNextSentenceSpoken = 0.0
    }

    /** current chunk needs to be re-read (at least a fraction of it after pause)
     */
    private fun backOneChunk(): Boolean {
        return if (nextTextToSpeak > 0) {
            nextTextToSpeak--
            true
        } else {
            false
        }
    }

    /** current chunk needs to be re-read (at least a fraction of it after pause)
     */
    private fun forwardOneChunk(): Boolean {
        return if (nextTextToSpeak < mTextToSpeak!!.size - 1) {
            nextTextToSpeak++
            true
        } else {
            false
        }
    }

    override fun reset() {
        if (mTextToSpeak != null) {
            mTextToSpeak!!.clear()
        }
        nextTextToSpeak = 0
        fractionOfNextSentenceSpoken = 0.0
    }

    /** save state to allow long pauses
     */
    override fun persistState() {
        val txt = mTextToSpeak
        if (txt!!.size > 0) {
            settings
                .setString(PERSIST_SPEAK_TEXT, txt.joinToString(PERSIST_SPEAK_TEXT_SEPARATOR))
            settings
                .setLong(PERSIST_NEXT_TEXT, nextTextToSpeak)
            settings
                .setDouble(PERSIST_FRACTION_SPOKEN, fractionOfNextSentenceSpoken)
        }
    }

    /** restore state to allow long pauses
     *
     * @return state restored
     */
    override fun restoreState(): Boolean {
        var isRestored = false
        val sharedPreferences = settings
        if (sharedPreferences.getString(PERSIST_SPEAK_TEXT, null) != null) {
            mTextToSpeak = sharedPreferences.getString(PERSIST_SPEAK_TEXT, "")!!.split(PERSIST_SPEAK_TEXT_SEPARATOR.toRegex()).toTypedArray().toMutableList()
            nextTextToSpeak = sharedPreferences.getLong(PERSIST_NEXT_TEXT, 0)
            fractionOfNextSentenceSpoken = sharedPreferences.getDouble(PERSIST_FRACTION_SPOKEN, 0.0)
            clearPersistedState()
            isRestored = true
        }
        return isRestored
    }

    override fun clearPersistedState() {
        settings.removeString(PERSIST_SPEAK_TEXT)
        settings.removeString(PERSIST_NEXT_TEXT)
        settings.removeString(PERSIST_FRACTION_SPOKEN)
    }

    private fun getPrevTextStartPos(text: String?, fraction: Double): StartPos {
        val retVal = StartPos()
        val allTextLength = text!!.length
        val nextTextOffset = (Math.min(1.0, fraction) * allTextLength).toInt()
        val breakIterator = BreakIterator.getSentenceInstance()
        breakIterator.setText(text)
        var startPos = 0
        try {
            // this can rarely throw an Exception
            startPos = breakIterator.preceding(nextTextOffset)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding previous sentence start", e)
        }
        retVal.found = startPos >= 0
        if (retVal.found) {
            retVal.startPosition = startPos

            // because we don't return an exact fraction, but go to the beginning of a sentence, we need to update the fractionAlreadySpoken  
            retVal.actualFractionOfWhole = retVal.startPosition.toFloat() / allTextLength
            retVal.text = text.substring(retVal.startPosition)
        }
        return retVal
    }

    private fun getForwardTextStartPos(text: String?, fraction: Double): StartPos {
        val retVal = StartPos()
        val allTextLength = text!!.length
        val nextTextOffset = (Math.min(1.0, fraction) * allTextLength).toInt()
        val breakIterator = BreakIterator.getSentenceInstance()
        breakIterator.setText(text)
        var startPos = 0
        try {
            // this can rarely throw an Exception
            startPos = breakIterator.following(nextTextOffset)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding next sentence start", e)
        }
        retVal.found = startPos >= 0
        if (retVal.found) {
            // nudge the startPos past the beginning of sentence so this sentence start is found when searching for previous block in getNextSentence
            retVal.startPosition = if (startPos < text.length - 1 - 1) startPos + 1 else startPos

            // because we don't return an exact fraction, but go to the beginning of a sentence, we need to update the fractionAlreadySpoken  
            retVal.actualFractionOfWhole = retVal.startPosition.toFloat() / allTextLength
            retVal.text = text.substring(retVal.startPosition)
        }
        return retVal
    }

    /** ICS rejects text longer than 4000 chars so break it up
     *
     */
    private fun breakUpText(text: String): List<String?> {
        //
        // first try to split text nicely at the end of sentences
        //
        val chunks1: MutableList<String> = ArrayList()

        // is the text short enough to use as is
        if (text.length < MAX_SPEECH_ITEM_CHAR_LENGTH) {
            chunks1.add(text)
        } else {
            // break up the text at sentence ends
            val matcher = BREAK_PATTERN.matcher(text)
            var matchedUpTo = 0
            while (matcher.find()) {
                val nextEnd = matcher.end()
                chunks1.add(text.substring(matchedUpTo, nextEnd))
                matchedUpTo = nextEnd
            }

            // add on the final part of the text, if there is any
            if (matchedUpTo < text.length) {
                chunks1.add(text.substring(matchedUpTo))
            }
        }

        //
        // If any text is still too long because the regexp was not matched then forcefully split it up
        // All chunks are probably now less than 4000 chars as required by tts but go through again for languages that don't have '. ' at the end of sentences
        //
        val chunks2: MutableList<String?> = ArrayList()
        for (chunk in chunks1) {
            if (chunk.length < MAX_SPEECH_ITEM_CHAR_LENGTH) {
                chunks2.add(chunk)
            } else {
                // force chunks to be correct length -10 is just to allow a bit of extra room
                chunks2.addAll(splitEqually(chunk, MAX_SPEECH_ITEM_CHAR_LENGTH - 10))
            }
        }
        return chunks2
    }

    private fun splitEqually(text: String, size: Int): List<String?> {
        // Give the list the right capacity to start with. You could use an array instead if you wanted.
        val ret: MutableList<String?> = ArrayList((text.length + size - 1) / size)
        var start = 0
        while (start < text.length) {
            ret.add(text.substring(start, Math.min(text.length, start + size)))
            start += size
        }
        return ret
    }

    private fun getStartPosFraction(startPos: Int, text: String?): Float {
        var startFraction = startPos.toFloat() / text!!.length

        // ensure fraction is between 0 and 1
        startFraction = Math.max(0f, startFraction)
        startFraction = Math.min(1f, startFraction)
        return startFraction
    }

    override fun getTotalChars(): Long {
        var totChars: Long = 0
        for (chunk in mTextToSpeak!!) {
            totChars += chunk!!.length.toLong()
        }
        return totChars
    }

    /** this relies on fraction which is set at pause
     */
    override fun getSpokenChars(): Long {
        var spokenChars: Long = 0
        if (mTextToSpeak!!.size > 0) {
            for (i in 0 until nextTextToSpeak - 1) {
                val chunk = mTextToSpeak!![i.toInt()]
                spokenChars += chunk!!.length.toLong()
            }
            if (nextTextToSpeak < mTextToSpeak!!.size) {
                spokenChars += (fractionOfNextSentenceSpoken * mTextToSpeak!![nextTextToSpeak.toInt()]!!.length.toFloat()).toLong()
            }
        }
        return spokenChars
    }

    override fun prepareForStartSpeaking() {}

    companion object {
        // Before ICS Android would split up long text for you but since ICS this error occurs:
        //    if (mText.length() >= MAX_SPEECH_ITEM_CHAR_LENGTH) {
        //        Log.w(TAG, "Text too long: " + mText.length() + " chars");
        private const val MAX_SPEECH_ITEM_CHAR_LENGTH = 4000

        // require DOTALL to allow . to match new lines which occur in books like JOChrist
        private val BREAK_PATTERN = Pattern.compile(".{100,2000}[a-z]+[.?!][\\s]{1,}+", Pattern.DOTALL)

        // enable state to be persisted if paused for a long time
        private const val PERSIST_SPEAK_TEXT = "SpeakText"
        private const val PERSIST_SPEAK_TEXT_SEPARATOR = "XXSEPXX"
        private const val PERSIST_NEXT_TEXT = "NextText"
        private const val PERSIST_FRACTION_SPOKEN = "FractionSpoken"
        private const val TAG = "Speak"
    }
}
