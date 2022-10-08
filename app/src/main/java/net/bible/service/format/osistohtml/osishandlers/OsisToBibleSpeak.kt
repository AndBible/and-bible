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

package net.bible.service.format.osistohtml.osishandlers

import android.os.Build
import android.text.Html
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.device.speak.*
import net.bible.service.format.osistohtml.OSISUtil2
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.util.*

val PARAGRAPH_TYPE_LIST = Arrays.asList("paragraph", "x-p", "x-end-paragraph")

class OsisToBibleSpeak(val speakSettings: SpeakSettings, val language: String) : DefaultHandler() {
    val speakCommands = SpeakCommandArray()

    private enum class TagType {NORMAL, TITLE, PARAGRAPH, DIVINE_NAME, FOOTNOTE}
    private data class StackEntry(val visible: Boolean, val tagType: TagType=TagType.NORMAL)
    private val elementStack = Stack<StackEntry>()
    private var divineNameOriginal: Array<String>
    private var divineNameReplace: Array<String>
    private var titleLevel = 0
    private var divineNameLevel = 0
    private var anyTextWritten = false

    init {
        val res = BibleApplication.application.getLocalizedResources(language)
        divineNameOriginal = res.getStringArray(R.array.speak_divinename_original)
        divineNameReplace = res.getStringArray(R.array.speak_divinename_replace)
    }

    override fun startDocument() {
        elementStack.push(StackEntry(true))
    }

    override fun endDocument() {
        elementStack.pop()
    }

    private fun getName(eName: String?, qName: String): String {
        return if (eName != null && eName.length > 0) {
            eName
        } else {
            qName // not namespace-aware
        }
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
            if((attrs?.getValue("type")?: "") == "study" && speakSettings.playbackSettings.speakFootnotes) {
                speakCommands.add(PreFootnoteCommand(speakSettings))
                elementStack.push(StackEntry(true, TagType.FOOTNOTE))
            }
            else {
                elementStack.push(StackEntry(false))
            }
        } else if (name == OSISUtil.OSIS_ELEMENT_REFERENCE) {
            elementStack.push(StackEntry(false))
        }  else if (name == OSISUtil2.OSIS_ELEMENT_DIVINENAME) {
            elementStack.push(StackEntry(peekVisible, TagType.DIVINE_NAME))
            divineNameLevel ++
        } else if (name == OSISUtil.OSIS_ELEMENT_TITLE) {
            elementStack.push(StackEntry(peekVisible, TagType.TITLE))
            speakCommands.add(PreTitleCommand(speakSettings))
            titleLevel++
        } else if (name == OSISUtil.OSIS_ELEMENT_DIV) {
            val type = attrs?.getValue("type") ?: ""
            val isVerseBeginning = attrs?.getValue("sID") != null
            val isParagraphType = PARAGRAPH_TYPE_LIST.contains(type)
            if(isParagraphType && !isVerseBeginning) {
                speakCommands.add(ParagraphChangeCommand())
                elementStack.push(StackEntry(peekVisible, TagType.PARAGRAPH))
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
            elementStack.push(StackEntry(peekVisible, TagType.PARAGRAPH))
        } else {
            elementStack.push(StackEntry(peekVisible))
        }
    }

    override fun endElement(namespaceURI: String,
                            simplifiedName: String,
                            qualifiedName: String
    ) {
        val state = elementStack.pop()

        if(state.tagType == TagType.PARAGRAPH) {
            if(anyTextWritten) {
                anyTextWritten = false
            }
        }
        else if(state.tagType == TagType.TITLE) {
            if (speakSettings.playbackSettings.speakTitles) {
                speakCommands.add(SilenceCommand())
            }
            titleLevel--
        }
        else if(state.tagType == TagType.DIVINE_NAME) {
            divineNameLevel--
        }
        else if(state.tagType == TagType.FOOTNOTE) {
            if(speakSettings.playbackSettings.speakFootnotes) {
                speakCommands.add(PostFootnoteCommand(speakSettings))
            }
        }
    }

    /*
     * Handle characters encountered in tags
    */
    override fun characters(buf: CharArray, offset: Int, len: Int) {
        addText(String(buf, offset, len))
    }

    private fun addText(text: String) {
        val currentState = elementStack.peek()
        var s = text
        s = s.replace("”", "\"")
        s = s.replace("“", "\"")
        s = s.replace("`", "'")
        s = s.replace("´", "'")
        s = s.replace("’", "'")
        s = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(s, 0).toString()
        }
        else {
            s.replace("&quot;", "\"")
        }

        if(currentState.visible) {
            if(divineNameLevel > 0) {
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

