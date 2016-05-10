package net.bible.android.view.activity.page;

import java.util.HashSet;
import java.util.Set;

/**
 * Handle verse selection logic.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class VerseNoRange {

	private int startVerseNo;
	private int endVerseNo;

	private static final int NO_SELECTION = -1;

	public VerseNoRange(int startVerseNo) {
		this.startVerseNo = startVerseNo;
		this.endVerseNo = startVerseNo;
	}

	public VerseNoRange(int startVerseNo, int endVerseNo) {
		this.startVerseNo = startVerseNo;
		this.endVerseNo = endVerseNo;
	}

	public VerseNoRange clone() {
		return new VerseNoRange(startVerseNo, endVerseNo);
	}

	public void alter(int verse) {
		if (verse>endVerseNo) {
			endVerseNo = verse;
		} else if (verse<startVerseNo) {
			startVerseNo = verse;
		} else if (verse>startVerseNo) {
			endVerseNo = verse-1;
		} else if (verse==startVerseNo && startVerseNo==endVerseNo) {
			startVerseNo = NO_SELECTION;
			endVerseNo = NO_SELECTION;
		} else if (verse==startVerseNo) {
			startVerseNo++;
		}
	}

	public Set<Integer> getExtrasIn(VerseNoRange other) {
		Set<Integer> extras = new HashSet<>();
		for (int i=other.startVerseNo; i<=other.endVerseNo; i++) {
			if (!contains(i)) {
				extras.add(i);
			}
		}

		return extras;
	}

	public boolean contains(int verseNo) {
		return verseNo>=startVerseNo && verseNo<=endVerseNo;
	}

	private Set<Integer> getVerseSet() {
		Set<Integer> verses = new HashSet<>();
		for (int i=startVerseNo; i<=endVerseNo; i++) {
			verses.add(i);
		}

		return verses;
	}



	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		VerseNoRange that = (VerseNoRange) o;

		if (startVerseNo != that.startVerseNo) return false;
		return endVerseNo == that.endVerseNo;
	}

	@Override
	public int hashCode() {
		int result = startVerseNo;
		result = 31 * result + endVerseNo;
		return result;
	}

	public int getStartVerseNo() {
		return startVerseNo;
	}

	public int getEndVerseNo() {
		return endVerseNo;
	}

	public boolean isEmpty() {
		return startVerseNo==-1 || endVerseNo==-1;
	}
}
