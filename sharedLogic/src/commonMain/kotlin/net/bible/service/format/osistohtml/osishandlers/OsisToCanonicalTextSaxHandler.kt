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

import net.bible.service.format.SaxTextPolicy
import net.bible.sharedcore.log.Log
import net.bible.service.format.osistohtml.taghandler.TagHandlerHelper.osisIdToVerseNum
import androidx.annotation.VisibleForTesting
import org.crosswire.jsword.book.OSISUtil
import fi.sykero.kdom.sax.Attributes

/**
 * Same result as `java.lang.Character.isWhitespace(Char)`: unlike Kotlin's `Char.isWhitespace()`, it is false for the
 * non-breaking spaces U+00A0, U+2007 and U+202F, which the search index text must keep. Pinned over all chars by
 * `JavaWhitespaceParityTest`.
 */
@VisibleForTesting
fun isJavaWhitespace(c: Char): Boolean = when (c) {
    in '\u0009'..'\u000D', in '\u001C'..'\u001F' -> true
    '\u00A0', '\u2007', '\u202F' -> false
    else -> c.category.let { it == CharCategory.SPACE_SEPARATOR || it == CharCategory.LINE_SEPARATOR || it == CharCategory.PARAGRAPH_SEPARATOR }
}

/**
 * Convert OSIS input into Canonical text (used when creating search index)
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class OsisToCanonicalTextSaxHandler(private val compatibleOffsets: Boolean = false, private val textPolicy: SaxTextPolicy): OsisSaxHandler() {
    private var currentVerseNo = 0
    protected var writeContentStack = ArrayList<CONTENT_STATE>()

    protected enum class CONTENT_STATE {
        WRITE, IGNORE
    }

    // Avoid space at the start and, extra space between words
    private var spaceJustWritten = true
    override fun startDocument() {
        reset()
        // default mode is to write
        writeContentStack.add(CONTENT_STATE.WRITE)
    }

    /*
     *Called when the Parser Completes parsing the Current XML File.
    */
    override fun endDocument() {
        // pop initial value
        writeContentStack.removeAt(writeContentStack.lastIndex)

        // assert
        if (!writeContentStack.isEmpty()) {
            Log.w("OsisToCanonicalTextSaxHandler", "OsisToCanonicalTextSaxHandler context stack should now be empty")
        }
    }

    /*
     * Called when the starting of the Element is reached. For Example if we have Tag
     * called <Title> ... </Title>, then this method is called when <Title> tag is
     * Encountered while parsing the Current XML File. The AttributeList Parameter has
     * the list of all Attributes declared for the Current Element in the XML File.
    */
    private var insideVerse = false;
    override fun startElement(namespaceURI: String,
                          sName: String,  // simple name
                          qName: String,  // qualified name
                          attrs: Attributes) {
        val name: String = getName(sName, qName) // element name
        debug(name, attrs, true)

        // if encountering either a verse tag or if the current tag is marked as being canonical then turn on writing
        if (!compatibleOffsets && isAttrValue(attrs, "canonical", "true")) {
            writeContentStack.add(CONTENT_STATE.WRITE)
        } else if (name == OSISUtil.OSIS_ELEMENT_VERSE) {
            currentVerseNo = osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID))
            writeContentStack.add(CONTENT_STATE.WRITE)
            insideVerse = true
            spaceJustWritten = true
        } else if (name == "milestone") {
            write(attrs.getValue("marker")?:"")
            writeContentStack.add(CONTENT_STATE.IGNORE)
        } else if (name == OSISUtil.OSIS_ELEMENT_NOTE) {
            writeContentStack.add(CONTENT_STATE.IGNORE)
        } else if (name == OSISUtil.OSIS_ELEMENT_TITLE) {
            writeContentStack.add(CONTENT_STATE.IGNORE)
        } else if (name == OSISUtil.OSIS_ELEMENT_Q) {
            write(attrs.getValue("marker")?:"")
            writeContentStack.add(CONTENT_STATE.WRITE)
        } else if (name == OSISUtil.OSIS_ELEMENT_REFERENCE) {
            // text content of top level references should be output but in notes it should not
            writeContentStack.add(writeContentStack.last())
        } else if (name == OSISUtil.OSIS_ELEMENT_L || name == OSISUtil.OSIS_ELEMENT_LB || name == OSISUtil.OSIS_ELEMENT_P) {
            // these occur in Psalms to separate different paragraphs.  
            // A space is needed for TTS not to be confused by punctuation with a missing space like 'toward us,and the'
            if(!compatibleOffsets) write(" ")
            //if writing then continue.  Also if ignoring then continue
            writeContentStack.add(writeContentStack.last())
        } else {
            // unknown tags rely on parent tag to determine if content is canonical e.g. the italic tag in the middle of canonical text
            writeContentStack.add(writeContentStack.last())
        }

        if(name != OSISUtil.OSIS_ELEMENT_VERSE && insideVerse && compatibleOffsets) {
            spaceJustWritten = false
        }
    }

    /*
     * Called when the Ending of the current Element is reached. For example in the
     * above explanation, this method is called when </Title> tag is reached
    */
    override open fun endElement(namespaceURI: String,
                        sName: String,  // simple name
                        qName: String // qualified name
    ) {
        val name: String = getName(sName, qName)
        debug(name, null, false)
        if (name == OSISUtil.OSIS_ELEMENT_VERSE) {
            // A space is needed to separate one verse from the next, otherwise the 2 verses butt up against each other
            // which looks bad and confuses TTS
            write(" ")
            if(compatibleOffsets)
                insideVerse = false
        }

        // now this tag has ended pop the write/ignore state for the parent tag
        writeContentStack.removeAt(writeContentStack.lastIndex)
        if(insideVerse && compatibleOffsets) {
            spaceJustWritten = false
        }
    }

    /*
     * Handle characters encountered in tags
    */
    override fun characters(buf: CharArray, offset: Int, len: Int) {
        if (CONTENT_STATE.WRITE == writeContentStack.last()) {
            var s = buf.concatToString(offset, offset + len)
            if (textPolicy.restoreLeadingSpace) {
                if (s.length > 0 && s[0] == ' ') {
                    // fromHtml strips leading whitespaces, which is not desirable
                    write(" ")
                }
                s = textPolicy.canonical(s)
            }
            write(s)
        }
    }

    private fun isWhitespace(s: String): Boolean {
        if(s.isEmpty()) return false
        return s.all { isJavaWhitespace(it) }
    }

    override fun write(s: String?): Boolean {
        if((compatibleOffsets && !insideVerse) || s == null || s.isEmpty()) return false

        // reduce amount of whitespace because a lot of space was occurring between verses in ESVS and several other books
        var rv = false
        if (!isWhitespace(s)) {
            rv = super.write(s)
            spaceJustWritten = isJavaWhitespace(s[s.length - 1])
        } else if (!spaceJustWritten) {
            rv = super.write(" ")
            spaceJustWritten = true
        }
        return rv
    }

    protected fun writeContent(writeContent: Boolean) {
        if (writeContent) {
            writeContentStack.add(CONTENT_STATE.WRITE)
        } else {
            writeContentStack.add(CONTENT_STATE.IGNORE)
        }
    }

}
