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

import net.bible.android.control.speak.SpeakPhrases
import org.crosswire.common.util.KLocale
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import net.bible.service.format.SaxTextPolicy
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.service.device.speak.*
import net.bible.service.format.osistohtml.OSISUtil2
import org.crosswire.jsword.book.OSISUtil
import fi.sykero.kdom.sax.Attributes
import fi.sykero.kdom.sax.DefaultHandler

val PARAGRAPH_TYPE_LIST = listOf("paragraph", "x-p", "x-end-paragraph")

class OsisToBibleSpeak(val speakSettings: SpeakSettings, phrases: SpeakPhrases, private val advancedSpeakSettings: AdvancedSpeakSettings, maxSpeechInputLength: Int, private val textPolicy: SaxTextPolicy, locale: KLocale = LocaleProviderManager.getLocale()) : DefaultHandler() {
    private val referenceText = SpeakReferenceText(locale)
    val speakCommands = SpeakCommandArray(maxSpeechInputLength)

    private enum class TagType {NORMAL, TITLE, PARAGRAPH, DIVINE_NAME, FOOTNOTE}
    private data class StackEntry(val visible: Boolean, val tagType: TagType=TagType.NORMAL)
    private val elementStack = ArrayList<StackEntry>()
    private val divineNameOriginal = phrases.divineNameOriginal
    private val divineNameReplace = phrases.divineNameReplace
    private var titleLevel = 0
    private var divineNameLevel = 0
    private var anyTextWritten = false
    /** What the previous visible raw text chunk left at the boundary to the next one. */
    private enum class RawEnd { NO_WHITESPACE, WHITESPACE, CROSSED_SEPARATOR }
    private var previousRawEnd = RawEnd.CROSSED_SEPARATOR

    private fun separatesText(name: String): Boolean = when (name) {
        OSISUtil.OSIS_ELEMENT_VERSE, OSISUtil.OSIS_ELEMENT_NOTE, OSISUtil.OSIS_ELEMENT_TITLE,
        OSISUtil.OSIS_ELEMENT_DIV, OSISUtil.OSIS_ELEMENT_L, OSISUtil.OSIS_ELEMENT_LB, OSISUtil.OSIS_ELEMENT_P,
        OSISUtil.OSIS_ELEMENT_LG, "milestone" -> true
        else -> false
    }

    private fun addCommand(command: SpeakCommand) {
        previousRawEnd = RawEnd.CROSSED_SEPARATOR
        speakCommands.add(command)
    }

    override fun startDocument() {
        elementStack.add(StackEntry(true))
    }

    override fun endDocument() {
        elementStack.removeAt(elementStack.lastIndex)
    }

    private fun getName(eName: String, qName: String): String {
        return if (eName.isNotEmpty()) {
            eName
        } else {
            qName // not namespace-aware
        }
    }

    override fun startElement(namespaceURI: String,
                              sName: String, // simple name
                              qName: String, // qualified name
                              attrs: Attributes) {
        val name = getName(sName, qName) // element name
        if (separatesText(name)) previousRawEnd = RawEnd.CROSSED_SEPARATOR

        val peekVisible = elementStack.last().visible

        if (name == OSISUtil.OSIS_ELEMENT_VERSE) {
            anyTextWritten = false
            elementStack.add(StackEntry(true))
        } else if (name == OSISUtil.OSIS_ELEMENT_NOTE) {
            if((attrs.getValue("type")?: "") == "study" && speakSettings.playbackSettings.speakFootnotes) {
                addCommand(PreFootnoteCommand(speakSettings))
                elementStack.add(StackEntry(true, TagType.FOOTNOTE))
            }
            else {
                elementStack.add(StackEntry(false))
            }
        } else if (name == OSISUtil.OSIS_ELEMENT_REFERENCE) {
            val expanded = referenceText.expand(attrs.getValue(OSISUtil.OSIS_ATTR_REF))
            if (peekVisible && expanded != null) {
                // The expansion is synthetic text, not source text: the source's (missing) whitespace around the
                // reference says nothing about it. Treat it like a separator on both sides, so the legacy delimiter
                // rule decides ("spirit, Psalms 51:17;" not "spirit,Psalms 51:17;").
                previousRawEnd = RawEnd.CROSSED_SEPARATOR
                addText(expanded)
                previousRawEnd = RawEnd.CROSSED_SEPARATOR
            }
            elementStack.add(StackEntry(peekVisible && expanded == null))
        }  else if (name == OSISUtil2.OSIS_ELEMENT_DIVINENAME) {
            elementStack.add(StackEntry(peekVisible, TagType.DIVINE_NAME))
            divineNameLevel ++
        } else if (name == OSISUtil.OSIS_ELEMENT_TITLE) {
            elementStack.add(StackEntry(peekVisible, TagType.TITLE))
            addCommand(PreTitleCommand(speakSettings))
            titleLevel++
        } else if (name == OSISUtil.OSIS_ELEMENT_DIV) {
            val type = attrs.getValue("type") ?: ""
            val isVerseBeginning = attrs.getValue("sID") != null
            val isParagraphType = PARAGRAPH_TYPE_LIST.contains(type)
            if(isParagraphType && !isVerseBeginning) {
                addCommand(ParagraphChangeCommand())
                elementStack.add(StackEntry(peekVisible, TagType.PARAGRAPH))
            }
            else {
                elementStack.add(StackEntry(peekVisible))
            }
        } else if (name == OSISUtil.OSIS_ELEMENT_L
                || name == OSISUtil.OSIS_ELEMENT_LB ||
                name == OSISUtil.OSIS_ELEMENT_P) {
            if(anyTextWritten) {
                addCommand(ParagraphChangeCommand())
            }
            elementStack.add(StackEntry(peekVisible, TagType.PARAGRAPH))
        } else {
            elementStack.add(StackEntry(peekVisible))
        }
    }

    override fun endElement(namespaceURI: String,
                            simplifiedName: String,
                            qualifiedName: String
    ) {
        if (separatesText(getName(simplifiedName, qualifiedName))) previousRawEnd = RawEnd.CROSSED_SEPARATOR
        val state = elementStack.removeAt(elementStack.lastIndex)

        if(state.tagType == TagType.PARAGRAPH) {
            if(anyTextWritten) {
                anyTextWritten = false
            }
        }
        else if(state.tagType == TagType.TITLE) {
            if (speakSettings.playbackSettings.speakTitles) {
                addCommand(SilenceCommand())
            }
            titleLevel--
        }
        else if(state.tagType == TagType.DIVINE_NAME) {
            divineNameLevel--
        }
        else if(state.tagType == TagType.FOOTNOTE) {
            if(speakSettings.playbackSettings.speakFootnotes) {
                addCommand(PostFootnoteCommand(speakSettings))
            }
        }
    }

    /*
     * Handle characters encountered in tags
    */
    override fun characters(buf: CharArray, offset: Int, len: Int) {
        addText(buf.concatToString(offset, offset + len))
    }

    private fun addText(text: String) {
        val currentState = elementStack.last()
        if (!currentState.visible || (titleLevel > 0 && !speakSettings.playbackSettings.speakTitles) || text.isEmpty()) return

        // Record RAW SAX boundaries before TextCommand trims them. Even whitespace-only callbacks separate words.
        // true: no whitespace at the boundary; false: whitespace in the source; null: a separator was crossed and the
        // legacy delimiter rule decides.
        val joinToPrevious: Boolean? = when {
            text.first().isWhitespace() -> false
            previousRawEnd == RawEnd.NO_WHITESPACE -> true
            previousRawEnd == RawEnd.WHITESPACE -> false
            else -> null
        }
        previousRawEnd = if (text.last().isWhitespace()) RawEnd.WHITESPACE else RawEnd.NO_WHITESPACE
        var s = text
        s = s.replace("”", "\"")
        s = s.replace("“", "\"")
        s = s.replace("`", "'")
        s = s.replace("´", "'")
        s = s.replace("’", "'")
        s = s.replace("ʼ", "'")
        s = textPolicy.speech(s)

        if(divineNameLevel > 0 && advancedSpeakSettings.replaceDivineName) {
            for(i in 0 until divineNameOriginal.size) {
                if(divineNameOriginal[i].isNotEmpty()) {
                    s = s.replace(divineNameOriginal[i], divineNameReplace[i], false)
                }
            }
        }
        val type = if (titleLevel > 0) TextCommand.TextType.TITLE else TextCommand.TextType.NORMAL
        speakCommands.add(TextCommand(s, type, joinToPrevious))
    }
}

