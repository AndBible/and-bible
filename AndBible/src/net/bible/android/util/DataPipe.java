package net.bible.android.util;

import java.util.List;

import net.bible.service.format.Note;

/** avoid having to use Parcelable objects to pass to intents by passing data to intents using these simple methods
 * 
 * @author denha1m
 *
 */
public class DataPipe {

	private List<Note> notes;
	
	static private DataPipe singleton = new DataPipe();
	
	private DataPipe() {
	}
	
	public static DataPipe getInstance() {
		return singleton;
	}
	
	public void pushNotes(List<Note> notes) {
		this.notes = notes;
	}
	public List<Note> popNotes() {
		List<Note> notes = this.notes;
		this.notes = null;
		return notes;
	}
}
