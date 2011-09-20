package net.bible.android.control.page;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;

public class CurrentBibleVerse {
	
	private Verse verseSelected = new Verse(BibleBook.GEN,1,1,true);

	public int getCurrentBibleBookNo() {
		return verseSelected.getBook().ordinal();
	}

	public BibleBook getCurrentBibleBook() {
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
