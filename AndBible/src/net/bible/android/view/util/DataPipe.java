package net.bible.android.view.util;

import java.util.List;

import net.bible.service.format.Note;

/** avoid having to use Parcelable objects to pass to intents by passing data to intents using these simple methods
 * I don't really know if this will always work but it seems ok for now
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
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
