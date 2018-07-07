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

    private enum class TAG_TYPE {NORMAL, TITLE, PARAGRPAH, DIVINE_NAME}
    private data class StackEntry(val visible: Boolean, val tagType: TAG_TYPE=TAG_TYPE.NORMAL)
    private val elementStack = Stack<StackEntry>()
    private var divineNameOriginal: Array<String>
    private var divineNameReplace: Array<String>
    private var titleLevel = 0
    private var anyTextWritten = false

    init {
        val res = BibleApplication.getApplication().getLocalizedResources(language)
        divineNameOriginal = res.getStringArray(R.array.divinename_original)
        divineNameReplace = res.getStringArray(R.array.divinename_replace)
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
        } else if (name == OSISUtil.OSIS_ELEMENT_REFERENCE) {
            elementStack.push(StackEntry(false))
        } else if (name == OSISUtil2.OSIS_ELEMENT_DIVINENAME) {
            elementStack.push(StackEntry(peekVisible, TAG_TYPE.DIVINE_NAME))
        } else if (name == OSISUtil.OSIS_ELEMENT_TITLE) {
            elementStack.push(StackEntry(peekVisible, TAG_TYPE.TITLE))
            speakCommands.add(PreTitleCommand(speakSettings))
            titleLevel++;
        } else if (name == OSISUtil.OSIS_ELEMENT_DIV) {
            val type = attrs?.getValue("type") ?: ""
            val isVerseBeginning = attrs?.getValue("sID") != null
            val isParagraphType = DivHandler.PARAGRAPH_TYPE_LIST.contains(type)
            if(isParagraphType && !isVerseBeginning) {
                speakCommands.add(ParagraphChangeCommand())
                elementStack.push(StackEntry(peekVisible, TAG_TYPE.PARAGRPAH))
            }
            else {
                elementStack.push(StackEntry(peekVisible))
            }
        } else if (name == OSISUtil.OSIS_ELEMENT_L
                || name == OSISUtil.OSIS_ELEMENT_LB ||
                name == OSISUtil.OSIS_ELEMENT_P) {
            if(anyTextWritten) {
                speakCommands.add(ParagraphChangeCommand())
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
            if(speakSettings.playbackSettings.speakTitles) {
                speakCommands.add(SilenceCommand())
            }
            titleLevel--;
        }
    }

    /*
     * Handle characters encountered in tags
    */
    override fun characters(buf: CharArray, offset: Int, len: Int) {
        val currentState = elementStack.peek()
        var s = String(buf, offset, len)
        s = s.replace("”", "\"")
        s = s.replace("“", "\"")
        s = s.replace("`", "'")
        s = s.replace("´", "'")
        if(currentState.visible) {
            if(currentState.tagType == TAG_TYPE.DIVINE_NAME) {
                if(speakSettings.replaceDivineName) {
                    for(i in 0 until divineNameOriginal.size) {
                        if(divineNameOriginal[i].isNotEmpty()) {
                            s = s.replace(divineNameOriginal[i], divineNameReplace[i], false)
                        }
                    }
                }
            }
            if(titleLevel > 0) {
                if(speakSettings.playbackSettings.speakTitles) {
                    speakCommands.add(TextCommand(s, TextCommand.TextType.TITLE))
                }
            }
            else {
                speakCommands.add(TextCommand(s))
            }
        }
    }
}

