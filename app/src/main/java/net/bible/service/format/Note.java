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

package net.bible.service.format;

import net.bible.service.common.Logger;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.versification.Versification;


/** Info on a note or cross reference
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class Note {

	public enum NoteType {TYPE_GENERAL, TYPE_REFERENCE}
	
	private int verseNo;
	private String noteRef;
	private String noteText;
	private NoteType noteType;
	private String osisRef;
	private Versification v11n;

	public static final String SUMMARY = "summary";
	public static final String DETAIL = "detail";
	
	private static final Logger log = new Logger("Note");
	
	public Note(int verseNo, String noteRef, String noteText, NoteType noteType, String osisRef, Versification v11n) {
		super();
		this.verseNo = verseNo;
		this.noteRef = noteRef;
		this.noteText = noteText;
		this.noteType = noteType;
		this.osisRef = osisRef;
		this.v11n = v11n;
	}

	public String getSummary() {
		return "Ref "+getNoteRef()+": "+getNoteText();
	}

	public boolean isNavigable() {
		return noteType.equals(NoteType.TYPE_REFERENCE);
	}
	
	@Override
	public String toString() {
		return noteRef+":"+noteText;
	}

	public int getVerseNo() {
		return verseNo;
	}
	public String getNoteRef() {
		return noteRef;
	}
	/**
	 * If note is reference specific then return the reference otherwise return the text within the note
	 */
	public String getNoteText() {
		String text=null;
		if (noteType.equals(NoteType.TYPE_REFERENCE)) {
			Key key = getReferenceKey();
			if (key!=null) {
				text = key.getName();
			}
		}
		// if not a reference or if reference was invalid return the notes text content
		if (text==null) {
			text = noteText;
		}
		return text;
	}
	
	private Key getReferenceKey() {
		Key key=null;
		try {
			if (noteType.equals(NoteType.TYPE_REFERENCE)) {
				String reference = StringUtils.isNotEmpty(osisRef) ? osisRef : noteText;
				key = PassageKeyFactory.instance().getValidKey(v11n, reference);
			}
		} catch (Exception e) {
			log.warn("Error getting note reference for osisRef "+osisRef, e);
		}
		return key;
	}

	public String getOsisRef() {
		return osisRef;
	}

	public NoteType getNoteType() {
		return noteType;
	}
}
