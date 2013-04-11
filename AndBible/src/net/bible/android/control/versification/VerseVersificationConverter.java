package net.bible.android.control.versification;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

public class VerseVersificationConverter {
	private Verse mainVerse;
	//todo implement cache to optimise verse creation time
//	private Map<Versification, Verse> versificationToVerse = new HashMap<Versification, Verse>();

	public VerseVersificationConverter(Verse verse) {
		this(verse.getVersification(), verse.getBook(), verse.getChapter(), verse.getVerse());
	}

	public VerseVersificationConverter(Versification versification, BibleBook book, int chapter, int verseNo) {
		mainVerse = new Verse(versification, book, chapter, verseNo, true);
//		versificationToVerse.put(versification, mainVerse);
	}
	
	public void setVerseNo(int verseNo) {
//		versificationToVerse.clear();
		mainVerse = new Verse(mainVerse.getVersification(), mainVerse.getBook(), mainVerse.getChapter(), verseNo);
//		versificationToVerse.put(mainVerse.getVersification(), mainVerse);
	}
	public int getVerseNo() {
		return mainVerse.getVerse();
	}

	public void setVerse(Versification versification, Verse verse) {
		mainVerse = new Verse(versification, verse.getBook(), verse.getChapter(), verse.getVerse());
	}

	public Verse getVerse(Versification versification) {
		return new Verse(versification, mainVerse.getBook(), mainVerse.getChapter(), mainVerse.getVerse());
	}

	/** books should be the same as they are enums
	 */
	public BibleBook getBook() {
		return mainVerse.getBook();
	}
}
