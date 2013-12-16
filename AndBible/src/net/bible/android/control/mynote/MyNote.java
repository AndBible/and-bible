/**
 * 
 */
package net.bible.android.control.mynote;

import java.util.List;

import net.bible.service.db.mynote.MyNoteDto;

import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;

/**
 * Control MyNote functionality
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public interface MyNote {

	/** get a list of Keys which have notes in the passage (normally a chapter) */
	List<Verse> getVersesWithNotesInPassage(Key passage);
	
	/** show add or edit depending on existence of note */
	public int getAddEditMenuText();
	
	/** go to MyNote edit screen and show this note */
	public void showNoteView(MyNoteDto noteDto);

	/** called by item adapter.  Returns key for v11n */
	public String getMyNoteVerseKey(MyNoteDto myNote);

	/** called at start of edit/view.  Returns note text or empty string */
	String getMyNoteTextByKey(Key verse);

	/** save text for current verse */
	boolean saveMyNoteText(String myNote);
	
	/** get a dto for current verse */
	public MyNoteDto getCurrentMyNoteDto();
	
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

	/** change the order the MyNotes are listed in */
	public void setSortOrder(MyNoteSortOrder sortOrder);
}
