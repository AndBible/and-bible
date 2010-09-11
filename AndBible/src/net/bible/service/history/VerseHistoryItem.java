package net.bible.service.history;

import net.bible.android.CurrentPassage;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

public class VerseHistoryItem {
	private Key verse;

	public VerseHistoryItem(Key verse) {
		super();
		this.verse = verse;
	}
	
	public void revertTo() {
		CurrentPassage.getInstance().setKey(verse);
	}

	public Key getVerse() {
		return verse;
	}

	@Override
	public String toString() {
		return "VerseHistoryItem [verse=" + verse + "]";
	}
}
