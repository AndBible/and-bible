package net.bible.service.format;

import java.util.HashMap;

import net.bible.android.CurrentPassage;
import net.bible.service.sword.SwordApi;

import android.util.Log;


public class Note extends HashMap<String, String> {

	public enum NoteType {TYPE_GENERAL, TYPE_REFERENCE};
	
	private int verseNo;
	private String noteRef;
	private String noteText;
	private NoteType noteType;

	public static final String SUMMARY = "summary";
	public static final String DETAIL = "detail";
	
	private static final String TAG = "Note";
	
	public Note(int verseNo, String noteRef, String noteText, NoteType noteType) {
		super();
		this.verseNo = verseNo;
		this.noteRef = noteRef;
		this.noteText = noteText;
		this.noteType = noteType;
	}
	
	@Override
	public String get(Object key) {
		String retval = "";
		try {
    		if (key.equals(SUMMARY)) {
    			retval = "Ref "+getNoteRef()+": "+getNoteText();
    		} else if (key.equals(DETAIL)) {
    			if (noteType.equals(NoteType.TYPE_REFERENCE)) {
    				retval = SwordApi.getInstance().getPlainText(CurrentPassage.getInstance().getCurrentDocument(), getNoteText(), 1);
    			}
    		} else {
    			retval = "Error";//SwordApi.getInstance().getPlainText(CurrentPassage.getInstance().getCurrentDocument(), verse.getName(), 1);
    		}
		} catch (Exception e) {
			Log.e(TAG, "Error getting search result", e);
		}
		return retval;

	}
	
	public boolean isNavigable() {
		return noteType.equals(NoteType.TYPE_REFERENCE);
	}
	
	public void navigateTo() {
		CurrentPassage.getInstance().setKey(noteText);
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
	public String getNoteText() {
		return noteText;
	}
}
