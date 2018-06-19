package net.bible.service.format.osistohtml.osishandlers

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.speak.SpeakSettings
import net.bible.service.device.speak.*
import net.bible.service.format.osistohtml.OSISUtil2
import net.bible.service.format.osistohtml.taghandler.DivHandler
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes

import java.util.Stack


class OsisToBibleSpeak(val speakSettings: SpeakSettings, val language: String) : OsisSaxHandler() {
    val speakCommands = SpeakCommandArray()

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
            speakCommands.add(PreTitleCommand(speakSettings))
        } else if (name == OSISUtil.OSIS_ELEMENT_DIV) {
            val type = attrs?.getValue("type") ?: ""
            val isVerseBeginning = attrs?.getValue("sID") != null
            val isParagraphType = DivHandler.PARAGRAPH_TYPE_LIST.contains(type)
            if(isParagraphType && !isVerseBeginning) {
                speakCommands.add(ParagraphChangeCommand(speakSettings))
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
                speakCommands.add(ParagraphChangeCommand(speakSettings))
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
        else if(state.tagType == TAG_TYPE.TITLE) {
            if(speakSettings.speakTitles) {
                speakCommands.add(SilenceCommand())
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
                    speakCommands.add(TextCommand(s))
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

