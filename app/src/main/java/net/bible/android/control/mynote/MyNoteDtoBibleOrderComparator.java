package net.bible.android.control.mynote;

import net.bible.android.control.versification.sort.ConvertibleVerseRangeComparator;
import net.bible.service.db.mynote.MyNoteDto;

import java.util.Comparator;
import java.util.List;

/**
 * Complex comparison of dtos ensuring the best v11n is used for each comparison.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class MyNoteDtoBibleOrderComparator implements Comparator<MyNoteDto> {
	private final ConvertibleVerseRangeComparator convertibleVerseRangeComparator;

	public MyNoteDtoBibleOrderComparator(List<MyNoteDto> myNoteDtos) {
		this.convertibleVerseRangeComparator = new ConvertibleVerseRangeComparator.Builder().withMyNotes(myNoteDtos).build();
	}

	@Override
	public int compare(MyNoteDto o1, MyNoteDto o2) {
		return convertibleVerseRangeComparator.compare(o1, o2);
	}
}
