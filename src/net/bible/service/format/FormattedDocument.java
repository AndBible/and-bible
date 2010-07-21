package net.bible.service.format;

import java.util.List;

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
