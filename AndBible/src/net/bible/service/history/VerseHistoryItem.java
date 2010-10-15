package net.bible.service.history;

import net.bible.android.currentpagecontrol.CurrentBiblePage;
import net.bible.android.currentpagecontrol.CurrentPageManager;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

public class VerseHistoryItem implements HistoryItem {
	private Book document;
	private Key key;

	public VerseHistoryItem(Book doc, Key verse) {
		super();
		this.document = doc;
		this.key = verse;
	}
	
	/* (non-Javadoc)
	 * @see net.bible.service.history.HistoryItem#revertTo()
	 */
	@Override
	public void revertTo() {
		CurrentPageManager.getInstance().setCurrentDocument(document);
		CurrentPageManager.getInstance().getCurrentPage().setKey(key);
	}

	
	@Override
	public String getDescription() {
		return key.getName();
	}

	public Key getKey() {
		return key;
	}

	@Override
	public String toString() {
		return getDescription();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
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
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		return true;
	}
	
	
}
