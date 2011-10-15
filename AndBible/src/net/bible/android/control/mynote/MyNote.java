/**
 * 
 */
package net.bible.android.control.mynote;

import java.util.List;

import net.bible.service.db.mynote.MyNoteDto;

import org.crosswire.jsword.passage.Key;

/**
 * @author John D. Lewis
 * 
 * Based on corresponding Bookmark class(es)
 * 
 */
public interface MyNote {

	/** show add or edit depending on existence of note */
	public int getAddEditMenuText();

	/** called at start of edit/view.  Init environment and return a mynote dto for use */
	public MyNoteDto startMyNoteEdit();
	
	/** save the note to the database if it is new or has been updated */
	boolean saveMyNote(MyNoteDto myNoteDto);
	
	/** get all user notes */
	List<MyNoteDto> getAllMyNotes();
	
	/** look up an existing mynote */
	public MyNoteDto getMyNoteByKey(Key key);

	/** delete this user note (and any links to labels) */
	boolean deleteMyNote(MyNoteDto usernote);

	/** get abbreviated text for use in Notes list */
	String getMyNoteText(MyNoteDto usernote, boolean abbreviated);
}
