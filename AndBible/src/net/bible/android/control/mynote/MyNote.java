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

	void editStarted();
	
	//** business method */
	boolean saveUsernoteCurrentVerse(String usertext);
	
	/** get user notes with the given id */
	MyNoteDto getUserNoteById(Long id);

	// pure user note methods
	
	/** get all user notes */
	List<MyNoteDto> getAllUserNotes();
	
	/** create a new user note */
	MyNoteDto addUserNote(MyNoteDto bookmark);
	
	/** look up an existing mynote */
	public MyNoteDto getUserNoteByKey(Key key);

	/** delete this user note (and any links to labels) */
	boolean deleteUserNote(MyNoteDto usernote);

	String getUserNoteText(MyNoteDto usernote, boolean abbreviated);
}
