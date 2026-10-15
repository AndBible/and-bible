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

package net.bible.service.device.speak

import net.bible.sharedcore.platform.speak.SpeakEngine
import net.bible.sharedcore.platform.speak.SpeakLanguageResult
import net.bible.sharedcore.log.Log
import net.bible.android.database.bookmarks.SpeakSettings
import java.util.*

const val TAG = "SpeakCommands"

interface SpeakCommand {
    fun speak(engine: SpeakEngine, utteranceId: String)
}

class TextCommand(text: String, val type: TextType = TextType.NORMAL) : SpeakCommand {
    enum class TextType {NORMAL, TITLE}
    val text: String = text.trim()

    override fun speak(engine: SpeakEngine, utteranceId: String) {
        engine.speakText(text, utteranceId)
    }

    override fun toString(): String {
        return "${super.toString()} $text"
    }
}

abstract class EarconCommand(private val earcon: String, val enabled: Boolean): SpeakCommand {
    override fun speak(engine: SpeakEngine, utteranceId: String) {
        if (enabled && earcon.isNotEmpty()) {
            engine.playEarcon(earcon, 0.2f, utteranceId)
        } else {
            engine.playSilence(0, utteranceId)
        }
    }
}

class ChangeLanguageCommand(val language: Locale): SpeakCommand {
    override fun speak(engine: SpeakEngine, utteranceId: String) {
        val result = engine.setLanguage(language.toLanguageTag())
        if(result != SpeakLanguageResult.AVAILABLE) {
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
    override fun speak(engine: SpeakEngine, utteranceId: String) {
        engine.playSilence(if (enabled) 500 else 0, utteranceId)
    }
}

class ParagraphChangeCommand : SilenceCommand(true)

class SpeakCommandArray(private val maxLength: Int): ArrayList<SpeakCommand>() {
    private val endsWithSentenceBreak = Regex(""".*[.?!。]+[\p{Pf}"']*\W*""", RegexOption.DOT_MATCHES_ALL)
    private val splitIntoTwoSentences = Regex("""(.*)([.?!。]+[\p{Pf}"']*)(\W*.+)""", RegexOption.DOT_MATCHES_ALL)
    private val startsWithDelimiter = Regex("""([。,.?!"':;()，；]+|'s)(\p{Zs}.*|)""", RegexOption.DOT_MATCHES_ALL)

    fun copy(): SpeakCommandArray {
        val cmds = SpeakCommandArray(maxLength)
        cmds.addAll(this)
        return cmds
    }

    val endsSentence: Boolean
        get() {
            val lastCommand = this.lastOrNull()
            if (lastCommand is TextCommand) {
                return lastCommand.text.matches(endsWithSentenceBreak) || lastCommand.text.length > 250
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
                val newText = if(startsWithDelimiter.matches(element.text))
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
