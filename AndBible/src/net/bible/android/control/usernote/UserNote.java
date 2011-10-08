/**
 * 
 */
package net.bible.android.control.usernote;

import java.util.List;

import android.text.Editable;

import net.bible.service.db.usernote.UserNoteDto;
//import net.bible.service.db.usernote.LabelDto;

/**
 * @author John D. Lewis
 * 
 * Based on corresponding Bookmark class(es)
 * 
 */
public interface UserNote {

	//** business method */
//	boolean usernoteCurrentVerse();
	boolean usernoteCurrentVerse(String usertext);
	
	/** get user notes with the given id */
	UserNoteDto getUserNoteById(Long id);

	// pure user note methods
	
	/** get all user notes */
	List<UserNoteDto> getAllUserNotes();
	
	/** create a new user note */
	UserNoteDto addUserNote(UserNoteDto bookmark);
	
	/** delete this user note (and any links to labels) */
	boolean deleteUserNote(UserNoteDto usernote);

//	String getUserNoteVerseText(UserNoteDto usernote);	
	String getUserNoteText(UserNoteDto usernote, boolean abbreviated);
}
