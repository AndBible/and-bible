package net.bible.android.control.mynote;

import android.support.annotation.NonNull;

import net.bible.service.db.mynote.MyNoteDto;

import java.util.Comparator;

/**
 * Sort MyNotes by create date, most recent first
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class MyNoteCreationDateComparator implements Comparator<MyNoteDto> {

	public int compare(@NonNull MyNoteDto myNote1, @NonNull MyNoteDto myNote2) {
		// descending order
		return myNote2.getCreatedOn().compareTo(myNote1.getCreatedOn());
	}

}
