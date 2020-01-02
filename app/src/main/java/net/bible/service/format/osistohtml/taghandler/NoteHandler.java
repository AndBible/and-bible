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

package net.bible.service.format.osistohtml.taghandler;


import net.bible.service.common.Logger;
import net.bible.service.format.Note;
import net.bible.service.format.Note.NoteType;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Convert OSIS tags into html tags
 * 
 * Currently text and x-ref are mutually exclusive because of the clearing of tempstore 
 *
 * Study note
 * <note type="study">undefiled: or, perfect, or, sincere</note>
 *
 * Cross-reference note
 * In the <note n="a" osisID="Gen.1.1!crossReference.a" osisRef="Gen.1.1" type="crossReference"><reference osisRef="Job.38.4-Job.38.7">Job 38:4-7</reference>; <reference osisRef="Ps.33.6">Ps. 33:6</reference>; <reference osisRef="Ps.136.5">136:5</reference>; <reference osisRef="Isa.42.5">Isa. 42:5</reference>; <reference osisRef="Isa.45.18">45:18</reference>; <reference osisRef="John.1.1-John.1.3">John 1:1-3</reference>; <reference osisRef="Acts.14.15">Acts 14:15</reference>; <reference osisRef="Acts.17.24">17:24</reference>; <reference osisRef="Col.1.16-Col.1.17">Col. 1:16, 17</reference>; <reference osisRef="Heb.1.10">Heb. 1:10</reference>; <reference osisRef="Heb.11.3">11:3</reference>; <reference osisRef="Rev.4.11">Rev. 4:11</reference></note>beginning
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class NoteHandler implements OsisTagHandler {

    private OsisToHtmlParameters parameters;
    private VerseInfo verseInfo;

    private int noteCount = 0;

    //todo temporarily use a string but later switch to Map<int,String> of verse->note
    private List<Note> notesList = new ArrayList<>();
    private boolean isInNote = false;
    private String currentNoteRef;

    private HtmlTextWriter writer;
    
	@SuppressWarnings("unused")
    private static final Logger log = new Logger("NoteHandler");
    
    public NoteHandler(OsisToHtmlParameters osisToHtmlParameters, VerseInfo verseInfo, HtmlTextWriter theWriter) {
        this.parameters = osisToHtmlParameters;
        this.verseInfo = verseInfo;
        this.writer = theWriter;
    }

	@Override
	public String getTagName() {
		return OSISUtil.OSIS_ELEMENT_NOTE;
	}

	@Override
	public void start(Attributes attrs) {
		isInNote = true;
		currentNoteRef = getNoteRef(attrs);
		writeNoteRef(currentNoteRef);

		// prepare to fetch the actual note into the notes repo
		writer.writeToTempStore();
    }

    /*
     * Called when the Ending of the current Element is reached. For example in the
     * above explanation, this method is called when </Title> tag is reached
    */
	@Override
    public void end() {
		String noteText = writer.getTempStoreString();
		if (noteText.length()>0) {
			if (!StringUtils.containsOnly(noteText, "[];()., ")) {
				Note note = new Note(verseInfo.currentVerseNo, currentNoteRef, noteText, NoteType.TYPE_GENERAL, null, null);
				notesList.add(note);
			}
			// and clear the buffer
			writer.clearTempStore();
		}
		isInNote = false;
		writer.finishWritingToTempStore();
    }

    /** a reference is finished and now the note must be added
     */
    public void addNoteForReference(String refText, String osisRef) {
    	// add teh html to show a note character in the (bible) text
		// a few modules like HunUj have refs in the text but not surrounded by a Note tag (like esv) so need to add  Note here
		// special code to cope with HunUj problem
		if (parameters.isAutoWrapUnwrappedRefsInNote() && !isInNote() ) {
			currentNoteRef = createNoteRef();
			writeNoteRef(currentNoteRef);
		}

		// record the note information to show if user requests to see notes for this verse
		if (isInNote || parameters.isAutoWrapUnwrappedRefsInNote()) {
			Note note = new Note(verseInfo.currentVerseNo, currentNoteRef, refText, NoteType.TYPE_REFERENCE, osisRef, parameters.getDocumentVersification());
			notesList.add(note);
		}
    }
    
    /** either use the 'n' attribute for the note ref or just get the next character in a list a-z
     * 
     * @return a single char to use as a note ref
     */
    private String getNoteRef(Attributes attrs) {
    	// if the ref is specified as an attribute then use that
    	String noteRef = attrs.getValue("n");
		if (StringUtils.isEmpty(noteRef)) {
			noteRef = createNoteRef();
		}
    	return noteRef;
    }
    /** either use the character passed in or get the next character in a list a-z
     * 
     * @return a single char to use as a note ref
     */
	private String createNoteRef() {
		// else just get the next char
    	int inta = (int)'a';
    	char nextNoteChar = (char)(inta+(noteCount++ % 26));
    	return String.valueOf(nextNoteChar);
	}

	/** write noteref html to outputstream
	 */
	private void writeNoteRef(String noteRef) {
		if (parameters.isShowNotes()) {
			writer.write("<span class='noteRef'>" + noteRef + "</span> ");
		}
	}

	public boolean isInNote() {
		return isInNote;
	}

	public List<Note> getNotesList() {
		return notesList;
	}
}

