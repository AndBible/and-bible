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

import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import net.bible.android.control.speak.SpeakSettings
import java.util.*

const val TAG = "SpeakCommands"

interface SpeakCommand {
    fun speak(tts: TextToSpeech, utteranceId: String)
}

class TextCommand(text: String, val type: TextType = TextType.NORMAL) : SpeakCommand {
    enum class TextType {NORMAL, TITLE}
    val text: String = text.trim()

    override fun speak(tts: TextToSpeech, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
        else {
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            tts.speak(text, TextToSpeech.QUEUE_ADD, params)
        }
    }

    override fun toString(): String {
        return "${super.toString()} $text"
    }
}

abstract class EarconCommand(private val earcon: String, val enabled: Boolean): SpeakCommand {
    override fun speak(tts: TextToSpeech, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val eBundle = Bundle()
            eBundle.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.2f)
            eBundle.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            if (enabled && !earcon.isEmpty()) {
                tts.playEarcon(earcon, TextToSpeech.QUEUE_ADD, eBundle, utteranceId)
            }
            else {
                tts.playSilentUtterance(0, TextToSpeech.QUEUE_ADD, utteranceId)
            }
        }
    }
}

class ChangeLanguageCommand(val language: Locale): SpeakCommand {
    override fun speak(tts: TextToSpeech, utteranceId: String) {
        val result = tts.setLanguage(language)
        if(result != TextToSpeech.LANG_AVAILABLE) {
            Log.e(TAG, "Language $language not available!")
        }
    }
}

class PreBookChangeCommand: EarconCommand(TextToSpeechServiceManager.EARCON_PRE_BOOK_CHANGE, true)
class PreChapterChangeCommand(speakSettings: SpeakSettings): EarconCommand(
        TextToSpeechServiceManager.EARCON_PRE_CHAPTER_CHANGE,
        speakSettings.playbackSettings.speakChapterChanges)
class PreTitleCommand(speakSettings: SpeakSettings): EarconCommand(
        TextToSpeechServiceManager.EARCON_PRE_TITLE,
        speakSettings.playbackSettings.speakTitles)
class PreFootnoteCommand(speakSettings: SpeakSettings): EarconCommand(
        TextToSpeechServiceManager.EARCON_PRE_FOOTNOTE,
        speakSettings.playbackSettings.speakFootnotes)
class PostFootnoteCommand(speakSettings: SpeakSettings): EarconCommand(
        TextToSpeechServiceManager.EARCON_POST_FOOTNOTE,
        speakSettings.playbackSettings.speakFootnotes)

open class SilenceCommand(val enabled: Boolean=true) : SpeakCommand {
    override fun speak(tts: TextToSpeech, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.playSilentUtterance(if (enabled) 500 else 0, TextToSpeech.QUEUE_ADD, utteranceId)
        }
    }
}

class ParagraphChangeCommand : SilenceCommand(true)

class SpeakCommandArray: ArrayList<SpeakCommand>() {
    private val maxLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        TextToSpeech.getMaxSpeechInputLength()
    } else {
        4000
    }
    private val endsWithSentenceBreak = Regex(".*[.?!]+[\"']*\\W*")
    private val splitIntoTwoSentences = Regex("(.*)([.?!]+[\"']*)(\\W*.+)")
    private val startsWithDelimeter = Regex("([,.?!\"':;()]+|'s)( .*|)")

    fun copy(): SpeakCommandArray {
        val cmds = SpeakCommandArray()
        cmds.addAll(this)
        return cmds
    }

    val endsSentence: Boolean
        get() {
            val lastCommand = try {this.last()} catch(e: NoSuchElementException) {null}
            if (lastCommand is TextCommand) {
                return lastCommand.text.matches(endsWithSentenceBreak)
            }
            return true
        }

    override fun add(index: Int, element: SpeakCommand) {
        val currentCmd = try {
            this[index]
        } catch (e: IndexOutOfBoundsException) {null}
        if(element is TextCommand) {
            if(element.text.isEmpty())
                return

            if (currentCmd is TextCommand) {
                val newText = "${element.text} ${currentCmd.text}"
                if (newText.length > maxLength)
                    return super.add(index, element)
                else {
                    this[index] = TextCommand(newText, element.type)
                    return
                }
            }
            else {
                return super.add(index, element)
            }
        }
        else if(element is SilenceCommand && currentCmd is SilenceCommand && element.enabled && currentCmd.enabled) {
            return // Do not add another
        }
        else {
            return super.add(index, element)
        }
    }

    override fun add(element: SpeakCommand): Boolean {
        val lastCommand = try {this.last()} catch (e: NoSuchElementException) {null}
        if(element is TextCommand) {
            if(element.text.isEmpty())
                return false
            return if(lastCommand is TextCommand) {
                val newText = if(startsWithDelimeter.matches(element.text))
                    "${lastCommand.text}${element.text}"
                else
                    "${lastCommand.text} ${element.text}"
                if (newText.length > maxLength)
                    super.add(element)
                else {
                    this[this.size-1] = TextCommand(newText, lastCommand.type)
                    true
                }
            } else {
                super.add(element)
            }
        }
        else if(element is SilenceCommand && lastCommand is SilenceCommand) {
            return false // Do not add another
        }
        else {
            return super.add(element)
        }
    }

    override fun toString(): String {
        return this.joinToString(" ") { it.toString() }
    }

    override fun addAll(elements: Collection<SpeakCommand>): Boolean {
        for(e in elements) {
            add(e)
        }
        return true
    }


    fun addUntilSentenceBreak(commands: ArrayList<SpeakCommand>, rest: ArrayList<SpeakCommand>) {
        var sentenceBreakFound = false
        var textContinuation = false
        for(cmd in commands) {
            if(sentenceBreakFound) {
                rest.add(cmd)
            }
            else if(cmd is TextCommand) {
                val text = cmd.text
                val match = splitIntoTwoSentences.matchEntire(text)
                if(match != null && endsWithSentenceBreak.matchEntire(text) == null ) {
                    val (part1, delimiters, part2) = match.destructured
                    this.add(TextCommand("$part1$delimiters"))
                    rest.add(TextCommand(part2))
                    sentenceBreakFound = true
                }
                else {
                    this.add(cmd)
                    textContinuation = true
                }
            }
            // if there's some other command than TextCommand, we will intepret this as a sentence break too.
            else {
                if(textContinuation) {
                    this.add(cmd)
                    sentenceBreakFound = true
                } else {
                    this.add(cmd)
                }
            }
        }
    }
}
