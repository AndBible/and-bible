package net.bible.service.format.osistohtml.osishandlers


import net.bible.service.common.Logger
import net.bible.service.format.osistohtml.taghandler.TagHandlerHelper
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes

import java.util.Stack

class OsisToBibleSpeak : OsisSaxHandler() {

    private var currentVerseNo: Int = 0

    private val writeContentStack = Stack<CONTENT_STATE>()

    // Avoid space at the start and, extra space between words
    private var spaceJustWritten = true

    private enum class CONTENT_STATE {
        WRITE, IGNORE
    }

    override fun startDocument() {
        reset()
        // default mode is to write
        writeContentStack.push(CONTENT_STATE.WRITE)
    }

    /*
     *Called when the Parser Completes parsing the Current XML File.
    */
    override fun endDocument() {
        // pop initial value
        writeContentStack.pop()

        // assert
        if (!writeContentStack.isEmpty()) {
            log.warn("OsisToCanonicalTextSaxHandler context stack should now be empty")
        }
    }

    /*
     * Called when the starting of the Element is reached. For Example if we have Tag
     * called <Title> ... </Title>, then this method is called when <Title> tag is
     * Encountered while parsing the Current XML File. The AttributeList Parameter has
     * the list of all Attributes declared for the Current Element in the XML File.
    */
    override fun startElement(namespaceURI: String,
                              sName: String, // simple name
                              qName: String, // qualified name
                              attrs: Attributes?) {
        val name = getName(sName, qName) // element name

        debug(name, attrs, true)

        // if encountering either a verse tag or if the current tag is marked as being canonical then turn on writing
        if (isAttrValue(attrs, "canonical", "true")) {
            writeContentStack.push(CONTENT_STATE.WRITE)
        } else if (name == OSISUtil.OSIS_ELEMENT_VERSE) {
            if (attrs != null) {
                currentVerseNo = TagHandlerHelper.osisIdToVerseNum(attrs.getValue("", OSISUtil.OSIS_ATTR_OSISID))
            }
            writeContentStack.push(CONTENT_STATE.WRITE)
        } else if (name == OSISUtil.OSIS_ELEMENT_NOTE) {
            writeContentStack.push(CONTENT_STATE.IGNORE)
        } else if (name == OSISUtil.OSIS_ELEMENT_TITLE) {
            writeContentStack.push(CONTENT_STATE.IGNORE)
        } else if (name == OSISUtil.OSIS_ELEMENT_REFERENCE) {
            // text content of top level references should be output but in notes it should not
            writeContentStack.push(writeContentStack.peek())
        } else if (name == OSISUtil.OSIS_ELEMENT_L ||
                name == OSISUtil.OSIS_ELEMENT_LB ||
                name == OSISUtil.OSIS_ELEMENT_P) {
            // these occur in Psalms to separate different paragraphs.
            // A space is needed for TTS not to be confused by punctuation with a missing space like 'toward us,and the'
            write(" ")
            //if writing then continue.  Also if ignoring then continue
            writeContentStack.push(writeContentStack.peek())
        } else {
            // unknown tags rely on parent tag to determine if content is canonical e.g. the italic tag in the middle of canonical text
            writeContentStack.push(writeContentStack.peek())
        }
    }

    /*
     * Called when the Ending of the current Element is reached. For example in the
     * above explanation, this method is called when </Title> tag is reached
    */
    override fun endElement(namespaceURI: String,
                            sName: String, // simple name
                            qName: String  // qualified name
    ) {
        val name = getName(sName, qName)
        debug(name, null, false)
        if (name == OSISUtil.OSIS_ELEMENT_VERSE) {
            // A space is needed to separate one verse from the next, otherwise the 2 verses butt up against each other
            // which looks bad and confuses TTS
            write(" ")
        }

        // now this tag has ended pop the write/ignore state for the parent tag
        writeContentStack.pop()
    }

    /*
     * Handle characters encountered in tags
    */
    override fun characters(buf: CharArray, offset: Int, len: Int) {
        if (CONTENT_STATE.WRITE == writeContentStack.peek()) {
            val s = String(buf, offset, len)
            write(s)
        }
    }

    override fun write(s: String) {
        // reduce amount of whitespace becasue a lot of space was occurring between verses in ESVS and several other books
        if (!StringUtils.isWhitespace(s)) {
            super.write(s)
            spaceJustWritten = false
        } else if (!spaceJustWritten) {
            super.write(" ")
            spaceJustWritten = true
        }
    }

    protected fun writeContent(writeContent: Boolean) {
        if (writeContent) {
            writeContentStack.push(CONTENT_STATE.WRITE)
        } else {
            writeContentStack.push(CONTENT_STATE.IGNORE)
        }
    }

    companion object {

        private val log = Logger("OsisToBibleSpeak")
    }
}

