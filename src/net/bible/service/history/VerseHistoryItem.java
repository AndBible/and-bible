package net.bible.service.history;

import net.bible.android.CurrentPassage;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

public class VerseHistoryItem implements HistoryItem {
	private Key verse;

	public VerseHistoryItem(Key verse) {
		super();
		this.verse = verse;
	}
	
	/* (non-Javadoc)
	 * @see net.bible.service.history.HistoryItem#revertTo()
	 */
	@Override
	public void revertTo() {
		CurrentPassage.getInstance().setKey(verse);
	}

	
	@Override
	public String getDescription() {
		return verse.getName();
	}

	public Key getVerse() {
		return verse;
	}

	@Override
	public String toString() {
		return getDescription();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((verse == null) ? 0 : verse.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VerseHistoryItem other = (VerseHistoryItem) obj;
		if (verse == null) {
			if (other.verse != null)
				return false;
		} else if (!verse.equals(other.verse))
			return false;
		return true;
	}
	
	
}
