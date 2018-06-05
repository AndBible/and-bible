package net.bible.service.format.osistohtml.osishandlers

import net.bible.service.format.osistohtml.taghandler.DivHandler
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes

import java.util.Stack

abstract class SpeakCommand

class TextCommand(var text: String) : SpeakCommand() {
    override fun toString(): String {
        return text;
    }
}
class TitleCommand(val text: String): SpeakCommand() {
    override fun toString(): String {
        return text;
    }
}

class ParagraphChange : SpeakCommand()

class OsisToBibleSpeak : OsisSaxHandler() {
    val speakCommands: ArrayList<SpeakCommand> = ArrayList()

    private var anyTextWritten = false

    private enum class TAG_TYPE {NORMAL, TITLE, PARAGRPAH}

    private data class StackEntry(val visible: Boolean, val tagType: TAG_TYPE=TAG_TYPE.NORMAL)

    private val elementStack = Stack<StackEntry>()

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
        } else if (name == OSISUtil.OSIS_ELEMENT_TITLE) {
            elementStack.push(StackEntry(peekVisible, TAG_TYPE.TITLE))
        } else if (name == OSISUtil.OSIS_ELEMENT_DIV) {
            val type = attrs?.getValue("type") ?: ""
            val isVerseBeginning = attrs?.getValue("sID") != null
            val isParagraphType = DivHandler.PARAGRAPH_TYPE_LIST.contains(type)
            if(isParagraphType && !isVerseBeginning) {
                speakCommandsAdd(ParagraphChange())
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
                speakCommandsAdd(ParagraphChange())
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
        val name = getName(simplifiedName, qualifiedName)
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
                speakCommandsAdd(TitleCommand(s))
            }
            else {
                speakCommandsAdd(TextCommand(s))
            }
        }
    }

    fun speakCommandsAdd(v: TextCommand)
    {
        val lastCommand = try {speakCommands.last()} catch (e: NoSuchElementException) {null}
        if(lastCommand is TextCommand) {
            lastCommand.text = lastCommand.text.trim() + " " +  v.text.trim()
        }
        else {
            if(v.text.trim().length > 0) {
                speakCommands.add(v)
            }
        }
    }

    fun speakCommandsAdd(v: TitleCommand) {
        speakCommands.add(v)
    }

    fun speakCommandsAdd(v: ParagraphChange)
    {
        speakCommands.add(v)
    }
}

