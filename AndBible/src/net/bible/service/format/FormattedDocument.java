package net.bible.service.format;

import java.util.List;

/** A holder to allow return of 2 values - html & notes
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class FormattedDocument {
	String htmlPassage;
	List<Note> notesList;
	
	public String getHtmlPassage() {
		return htmlPassage;
	}
	public void setHtmlPassage(String htmlPassage) {
		this.htmlPassage = htmlPassage;
	}
	public List<Note> getNotesList() {
		return notesList;
	}
	public void setNotesList(List<Note> notesList) {
		this.notesList = notesList;
	}
}
