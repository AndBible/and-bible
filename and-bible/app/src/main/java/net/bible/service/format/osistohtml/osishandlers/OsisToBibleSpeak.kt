package net.bible.service.format.osistohtml.osishandlers

import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.speak.SpeakSettings
import net.bible.service.device.speak.TextToSpeechServiceManager.EARCON_PRE_TITLE
import net.bible.service.format.osistohtml.OSISUtil2
import net.bible.service.format.osistohtml.taghandler.DivHandler
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes

import java.util.Stack

abstract class SpeakCommand {
    open fun copy(): SpeakCommand {
        return this
    }

    abstract fun speak(tts: TextToSpeech, utteranceId: String)
}

class TextCommand(text: String) : SpeakCommand() {
    override fun speak(tts: TextToSpeech, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    var text: String = text.trim()
        set(value) {
            field = value.trim()
        }

    override fun toString(): String {
        return "${super.toString()} $text";
    }

    override fun copy(): SpeakCommand {
        return TextCommand(text)
    }
}


class TitleCommand(val text: String, val speakSettings: SpeakSettings): SpeakCommand() {
    override fun speak(tts: TextToSpeech, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val eBundle = Bundle()
            val tBundle = Bundle()
            eBundle.putFloat(KEY_PARAM_VOLUME, 0.1f)
            if(speakSettings.playEarCons) {
                tts.playEarcon(EARCON_PRE_TITLE, TextToSpeech.QUEUE_ADD, eBundle, null)
            }
            tts.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, null)
            tts.speak(text, TextToSpeech.QUEUE_ADD, tBundle, utteranceId)
            tts.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, null)
        }
    }

    override fun toString(): String {
        return "${super.toString()} $text";
    }
}


class ParagraphChange : SpeakCommand() {
    override fun speak(tts: TextToSpeech, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.playSilentUtterance(700, TextToSpeech.QUEUE_ADD, utteranceId)
        }
    }
}

class SpeakCommands: ArrayList<SpeakCommand>() {

    fun copy(): SpeakCommands {
        val cmds = SpeakCommands()
        for(cmd in this) {
            cmds.add(cmd.copy())
        }
        return cmds
    }
    private val maxLength = TextToSpeech.getMaxSpeechInputLength()
    private val endsWithSentenceBreak = Regex("(.*)([.?!]+[`´”“\"']*\\W*)")
    fun endsSentence(): Boolean {
        val lastCommand = this.last()
        if(lastCommand is TextCommand) {
            return lastCommand.text.matches(endsWithSentenceBreak)
        }
        return true
    }

    override fun add(index: Int, element: SpeakCommand) {
        if(element is TextCommand) {
            if(element.text.isEmpty())
                return

            val currentCmd =  this[index]
            if (currentCmd is TextCommand) {
                val newText = "${element.text} ${currentCmd.text}"
                if (newText.length > maxLength)
                    return super.add(index, element)
                else {
                    currentCmd.text = newText
                    return
                }
            }
            else {
                return super.add(index, element)
            }
        }
        else {
            return super.add(index, element)
        }
    }

    override fun add(element: SpeakCommand): Boolean {
        if(element is TextCommand) {
            if(element.text.isEmpty())
                return false

            val lastCommand = try {this.last()} catch (e: NoSuchElementException) {null}
            if(lastCommand is TextCommand) {
                val newText = "${lastCommand.text} ${element.text}"
                if (newText.length > maxLength)
                    return super.add(element)
                else {
                    lastCommand.text = newText
                    return true
                }
            }
            else {
                if(!element.text.isEmpty()) {
                    return super.add(element)
                }
                else {
                    return false;
                }
            }
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

    private val splitIntoTwoSentences = Regex("(.*)([.?!]+[`´”“\"']*\\W*)(.+)")

    fun addUntilSentenceBreak(commands: ArrayList<SpeakCommand>, rest: ArrayList<SpeakCommand>) {
        var sentenceBreakFound = false
        for(cmd in commands) {
            if(sentenceBreakFound) {
                rest.add(cmd)
            }
            else if(cmd is TextCommand) {
                val text = cmd.text
                val match = splitIntoTwoSentences.matchEntire(text)
                if(match != null) {
                    val (part1, delimiters, part2) = match.destructured
                    this.add(TextCommand("$part1$delimiters"))
                    rest.add(TextCommand(part2))
                    sentenceBreakFound = true
                }
                else {
                    this.add(cmd)
                }
            }
            else {
                this.add(cmd)
            }
        }
    }
}

class OsisToBibleSpeak(val speakSettings: SpeakSettings, val language: String) : OsisSaxHandler() {
    val speakCommands = SpeakCommands()

    private var anyTextWritten = false

    private enum class TAG_TYPE {NORMAL, TITLE, PARAGRPAH, DIVINE_NAME}

    private data class StackEntry(val visible: Boolean, val tagType: TAG_TYPE=TAG_TYPE.NORMAL)

    private val elementStack = Stack<StackEntry>()

    private var divineNameOriginal: String
    private var divineNameReplace: String

    init {
        val res = BibleApplication.getApplication().getLocalizedResources(language)
        divineNameOriginal = res.getString(R.string.divineNameOriginal)
        divineNameReplace = res.getString(R.string.divineNameReplace)
    }

    override fun startDocument() {
        reset()
        elementStack.push(StackEntry(true))
    }

    override fun endDocument() {
        elementStack.pop()
    }

    override fun startElement(namespaceURI: String,
                              sName: String, // simple name
                              qName: String, // qualified name
                              attrs: Attributes?) {
        val name = getName(sName, qName) // element name

        val peekVisible = elementStack.peek().visible

        if (name == OSISUtil.OSIS_ELEMENT_VERSE) {
            anyTextWritten = false
            elementStack.push(StackEntry(true))
        } else if (name == OSISUtil.OSIS_ELEMENT_NOTE) {
            elementStack.push(StackEntry(false))
        } else if (name == OSISUtil2.OSIS_ELEMENT_DIVINENAME) {
            elementStack.push(StackEntry(peekVisible, TAG_TYPE.DIVINE_NAME))
        } else if (name == OSISUtil.OSIS_ELEMENT_TITLE) {
            elementStack.push(StackEntry(peekVisible, TAG_TYPE.TITLE))
        } else if (name == OSISUtil.OSIS_ELEMENT_DIV) {
            val type = attrs?.getValue("type") ?: ""
            val isVerseBeginning = attrs?.getValue("sID") != null
            val isParagraphType = DivHandler.PARAGRAPH_TYPE_LIST.contains(type)
            if(isParagraphType && !isVerseBeginning) {
                speakCommands.add(ParagraphChange())
                elementStack.push(StackEntry(peekVisible, TAG_TYPE.PARAGRPAH))
            }
            else {
                elementStack.push(StackEntry(peekVisible))
            }
        } else if (name == OSISUtil.OSIS_ELEMENT_REFERENCE) {
            elementStack.push(StackEntry(peekVisible))
        } else if (name == OSISUtil.OSIS_ELEMENT_L
                || name == OSISUtil.OSIS_ELEMENT_LB ||
                name == OSISUtil.OSIS_ELEMENT_P) {
            if(anyTextWritten) {
                speakCommands.add(ParagraphChange())
            }
            elementStack.push(StackEntry(peekVisible, TAG_TYPE.PARAGRPAH))
        } else {
            elementStack.push(StackEntry(peekVisible))
        }
    }

    override fun endElement(namespaceURI: String,
                            simplifiedName: String,
                            qualifiedName: String
    ) {
        val state = elementStack.pop()

        if(state.tagType == TAG_TYPE.PARAGRPAH) {
            if(anyTextWritten) {
                anyTextWritten = false;
            }
        }
    }

    /*
     * Handle characters encountered in tags
    */
    override fun characters(buf: CharArray, offset: Int, len: Int) {
        val currentState = elementStack.peek()
        val s = String(buf, offset, len)
        if(currentState.visible) {
            if(currentState.tagType == TAG_TYPE.TITLE) {
                if(speakSettings.speakTitles) {
                    speakCommands.add(TitleCommand(s, speakSettings))
                }
            }
            else if(currentState.tagType == TAG_TYPE.DIVINE_NAME) {
                if(speakSettings.replaceDivineName) {
                    speakCommands.add(TextCommand(s.replace(divineNameOriginal, divineNameReplace, false)))
                }
                else {
                    speakCommands.add(TextCommand(s))
                }
            }
            else {
                speakCommands.add(TextCommand(s))
            }
        }
    }
}

