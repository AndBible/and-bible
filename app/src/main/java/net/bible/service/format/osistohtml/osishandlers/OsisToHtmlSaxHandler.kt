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
package net.bible.service.format.osistohtml.osishandlers

import net.bible.service.common.Logger
import net.bible.service.font.FontControl
import net.bible.service.format.Note
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.preprocessor.HebrewCharacterPreprocessor
import net.bible.service.format.osistohtml.preprocessor.TextPreprocessor
import net.bible.service.format.osistohtml.strongs.StrongsHandler
import net.bible.service.format.osistohtml.strongs.StrongsLinkCreator
import net.bible.service.format.osistohtml.taghandler.BookmarkMarker
import net.bible.service.format.osistohtml.taghandler.ChapterDivider
import net.bible.service.format.osistohtml.taghandler.DivHandler
import net.bible.service.format.osistohtml.taghandler.DivineNameHandler
import net.bible.service.format.osistohtml.taghandler.FigureHandler
import net.bible.service.format.osistohtml.taghandler.HiHandler
import net.bible.service.format.osistohtml.taghandler.LHandler
import net.bible.service.format.osistohtml.taghandler.LbHandler
import net.bible.service.format.osistohtml.taghandler.LgHandler
import net.bible.service.format.osistohtml.taghandler.ListHandler
import net.bible.service.format.osistohtml.taghandler.ListItemHandler
import net.bible.service.format.osistohtml.taghandler.MilestoneHandler
import net.bible.service.format.osistohtml.taghandler.MyNoteMarker
import net.bible.service.format.osistohtml.taghandler.NoteHandler
import net.bible.service.format.osistohtml.taghandler.OsisTagHandler
import net.bible.service.format.osistohtml.taghandler.PHandler
import net.bible.service.format.osistohtml.taghandler.QHandler
import net.bible.service.format.osistohtml.taghandler.ReferenceHandler
import net.bible.service.format.osistohtml.taghandler.TableCellHandler
import net.bible.service.format.osistohtml.taghandler.TableHandler
import net.bible.service.format.osistohtml.taghandler.TableRowHandler
import net.bible.service.format.osistohtml.taghandler.TitleHandler
import net.bible.service.format.osistohtml.taghandler.TransChangeHandler
import net.bible.service.format.osistohtml.taghandler.VerseHandler
import net.bible.service.format.osistohtml.tei.OrthHandler
import net.bible.service.format.osistohtml.tei.PronHandler
import net.bible.service.format.osistohtml.tei.RefHandler
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.OSISUtil
import org.xml.sax.Attributes
import java.security.InvalidParameterException
import java.util.*

/**
 * Convert OSIS tags into html tags
 *
 * Example OSIS tags from KJV Ps 119 v1 showing title, w, note <title canonical="true" subType="x-preverse" type="section"> <foreign n="?">ALEPH.</foreign> </title> <w lemma="strong:H0835">Blessed</w>
 * <transChange type="added">are</transChange> <w lemma="strong:H08549">the
 * undefiled</w> ... <w lemma="strong:H01980" morph="strongMorph:TH8802">who
 * walk</w> ... <w lemma="strong:H03068">of the
 * <seg><divineName>Lord</divineName></seg></w>. <note type="study">undefiled:
 * or, perfect, or, sincere</note>
 *
 * Example of notes cross references from ESV In the <note n="a" osisID="Gen.1.1!crossReference.a" osisRef="Gen.1.1" type="crossReference"><reference osisRef="Job.38.4-Job.38.7">Job
 * 38:4-7</reference>; <reference osisRef="Ps.33.6">Ps. 33:6</reference>;
 * <reference osisRef="Ps.136.5">136:5</reference>; <reference osisRef="Isa.42.5">Isa. 42:5</reference>; <reference osisRef="Isa.45.18">45:18</reference>; <reference osisRef="John.1.1-John.1.3">John 1:1-3</reference>; <reference osisRef="Acts.14.15">Acts 14:15</reference>; <reference osisRef="Acts.17.24">17:24</reference>; <reference osisRef="Col.1.16-Col.1.17">Col. 1:16, 17</reference>; <reference osisRef="Heb.1.10">Heb. 1:10</reference>; <reference osisRef="Heb.11.3">11:3</reference>; <reference osisRef="Rev.4.11">Rev.
 * 4:11</reference></note>beginning
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class OsisToHtmlSaxHandler(// properties
    private val parameters: OsisToHtmlParameters) : OsisSaxHandler() {

    // tag handlers for the different OSIS tags
    private val osisTagHandlers: MutableMap<String, OsisTagHandler?>
    private val noteHandler: NoteHandler
    private val chapterDivider: ChapterDivider

    // processor for the tag content
    private var textPreprocessor: TextPreprocessor? = null

    // internal logic
    private val verseInfo = VerseInfo()

    class VerseInfo {
        var osisID:String? = null
        var currentVerseNo = 0
        var positionToInsertBeforeVerse = 0
        var isTextSinceVerse = false
    }

    private val passageInfo = PassageInfo()

    class PassageInfo {
        var isAnyTextWritten = false
    }

    private fun registerHandler(handler: OsisTagHandler) {
        if (osisTagHandlers.put(handler.tagName, handler) != null) {
            throw InvalidParameterException("Duplicate handlers for tag " + handler.tagName)
        }
    }

    override fun startDocument() {
        // if not fragment then add head section
        if (!parameters.isAsFragment) {
            //jsTag += "\n<script type='text/javascript' src='file:///android_asset/web/loader.js.map'></script>\n";
            val styleSheetTags = parameters.cssStylesheets
            val customFontStyle: String = FontControl.instance.getHtmlFontStyle(parameters.font, parameters.cssClassForCustomFont)
            write("""<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"> <html xmlns='http://www.w3.org/1999/xhtml' lang='${parameters.languageCode}' dir='$direction'><head>$styleSheetTags
$customFontStyle<meta charset='utf-8'/></head><body><div id='start'></div><div id='content' style='visibility: hidden;'>""")
        }

        // force rtl for rtl languages - rtl support on Android is poor but
        // forcing it seems to help occasionally
        if (!parameters.isLeftToRight) {
            write("<span dir='rtl'>")
        }

        // only put top/bottom insert positions in main/non-fragment page
        if (!parameters.isAsFragment) {
            write("<div id='topOfBibleText'></div>")
        }
        chapterDivider.doStart()
        contentWritten = false
    }

    private var contentWritten = false
    override fun write(s: String?): Boolean {
        val written = super.write(s)
        if(written) {
            contentWritten = true
        }
        return written
    }
    override fun endDocument() {
        if(!contentWritten) {
            reset()
            return
        }
        // close last verse
        if (parameters.isVersePerline) {
            //close last verse
            if (verseInfo.currentVerseNo > 1) {
                write("</div>")
            }
        }

        // add optional footer e.g. Strongs show all occurrences link
        if (StringUtils.isNotEmpty(parameters.extraFooter)) {
            write(parameters.extraFooter)
        }
        if (!parameters.isLeftToRight) {
            write("</span>")
        }
        val jsTag = "\n<script type='text/javascript' src='file:///android_asset/web/loader.js'></script>\n"

        // only put top/bottom insert positions in main/non-fragment page
        if (!parameters.isAsFragment) {
            write("<div id='bottomOfBibleText'></div></div>$jsTag<script type='text/javascript'>andbible.initialize(INITIALIZE_SETTINGS);</script></body></html>")
        }
    }

    /*
	 * Called when the starting of the Element is reached. For Example if we
	 * have Tag called <Title> ... </Title>, then this method is called when
	 * <Title> tag is Encountered while parsing the Current XML File. The
	 * AttributeList Parameter has the list of all Attributes declared for the
	 * Current Element in the XML File.
	 */
    override fun startElement(namespaceURI: String,
                              sName: String,  // simple name
                              qName: String,  // qualified name
                              attrs: Attributes) {
        val name = getName(sName, qName) // element name
        debug(name, attrs, true)
        val tagHandler = osisTagHandlers[name]
        if (tagHandler != null) {
            tagHandler.start(attrs)
        } else {
            if (!IGNORED_TAGS.contains(name)) {
                log.info("Verse " + verseInfo.currentVerseNo + " unsupported OSIS tag:" + name)
            }
        }
    }

    /*
	 * Called when the Ending of the current Element is reached. For example in
	 * the above explanation, this method is called when </Title> tag is reached
	 */
    override fun endElement(namespaceURI: String, sName: String,  // simple name
                            qName: String // qualified name
    ) {
        val name = getName(sName, qName)
        debug(name, null, false)
        val tagHandler = osisTagHandlers[name]
        tagHandler?.end()
    }

    /*
	 * While Parsing the XML file, if extra characters like space or enter
	 * Character are encountered then this method is called. If you don't want
	 * to do anything special with these characters, then you can normally leave
	 * this method blank.
	 */
    override fun characters(buf: CharArray, offset: Int, len: Int) {
        var s: String? = String(buf, offset, len)

        val textPreprocessor = textPreprocessor
        if (textPreprocessor != null) {
            s = textPreprocessor.process(s)
        }
        val written = write(s)

        // record that we are now beyond the verse, but do it quickly so as not to slow down parsing
        verseInfo.isTextSinceVerse = verseInfo.isTextSinceVerse ||
            ((len > 2 || StringUtils.isNotBlank(s)) && written)
        passageInfo.isAnyTextWritten = passageInfo.isAnyTextWritten || verseInfo.isTextSinceVerse
    }

    /*
	 * In the XML File if the parser encounters a Processing Instruction which
	 * is declared like this <?ProgramName:BooksLib
	 * QUERY="author, isbn, price"?> Then this method is called where Target
	 * parameter will have "ProgramName:BooksLib" and data parameter will have
	 * QUERY="author, isbn, price". You can invoke a External Program from this
	 * Method if required.
	 */
    override fun processingInstruction(target: String, data: String) {
        // noop
    }

    private val direction: String
        private get() = if (parameters.isLeftToRight) "ltr" else "rtl"

    val notesList: List<Note>
        get() = noteHandler.notesList

    companion object {
        private const val HEBREW_LANGUAGE_CODE = "he"
        private val IGNORED_TAGS: Set<String> = HashSet(Arrays.asList(OSISUtil.OSIS_ELEMENT_CHAPTER))
        private val log = Logger("OsisToHtmlSaxHandler")
    }

    init {

        // chapter marker is manually called at correct time
        chapterDivider = ChapterDivider(parameters, verseInfo, writer)
        osisTagHandlers = HashMap()
        val bookmarkMarker = BookmarkMarker(parameters, verseInfo)
        val myNoteMarker = MyNoteMarker(parameters, verseInfo, writer)
        registerHandler(VerseHandler(parameters, verseInfo, bookmarkMarker, myNoteMarker, writer))
        noteHandler = NoteHandler(parameters, verseInfo, writer)
        registerHandler(noteHandler)
        registerHandler(ReferenceHandler(parameters, noteHandler, writer))
        registerHandler(RefHandler(parameters, noteHandler, writer))
        registerHandler(DivineNameHandler(writer))
        registerHandler(TitleHandler(parameters, verseInfo, writer))
        registerHandler(QHandler(parameters, writer))
        registerHandler(MilestoneHandler(parameters, passageInfo, verseInfo, writer))
        registerHandler(HiHandler(parameters, writer))
        registerHandler(TransChangeHandler(parameters, writer))
        registerHandler(OrthHandler(parameters, writer))
        registerHandler(PronHandler(parameters, writer))
        registerHandler(LbHandler(parameters, passageInfo, writer))
        registerHandler(LgHandler(parameters, writer))
        registerHandler(LHandler(parameters, writer))
        registerHandler(PHandler(parameters, writer))
        registerHandler(StrongsHandler(parameters, writer))
        registerHandler(FigureHandler(parameters, writer))
        registerHandler(DivHandler(parameters, verseInfo, passageInfo, writer))
        registerHandler(TableHandler(writer))
        registerHandler(TableRowHandler(writer))
        registerHandler(TableCellHandler(writer))
        registerHandler(ListHandler(writer))
        registerHandler(ListItemHandler(writer))

        //TODO at the moment we can only have a single TextPreprocesor, need to chain them and maybe make the writer a TextPreprocessor and put it at the end of the chain
        if (HEBREW_LANGUAGE_CODE == parameters.languageCode) {
            textPreprocessor = HebrewCharacterPreprocessor()
        } else if (parameters.isConvertStrongsRefsToLinks) {
            textPreprocessor = StrongsLinkCreator()
        }
    }
}
