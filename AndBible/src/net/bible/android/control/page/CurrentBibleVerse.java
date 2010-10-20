package net.bible.android.control.page;

import org.crosswire.jsword.passage.Verse;

public class CurrentBibleVerse {
	
	private Verse verseSelected = new Verse(1,1,1,true);

	public int getCurrentBibleBookNo() {
		return verseSelected.getBook();
	}
	
	public Verse getVerseSelected() {
		return verseSelected;
	}
	public void setVerseSelected(Verse verseSelected) {
		this.verseSelected = verseSelected;
	}
	public void setVerseNo(int verseNo) {
		verseSelected = new Verse(verseSelected.getBook(), verseSelected.getChapter(), verseNo, true);
	}
	public int getVerseNo() {
		return verseSelected.getVerse();
	}
}
