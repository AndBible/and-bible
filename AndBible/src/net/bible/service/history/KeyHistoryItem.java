package net.bible.service.history;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.page.MainBibleActivity;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.app.Activity;
import android.util.Log;

public class KeyHistoryItem implements HistoryItem {
	private Book document;
	private Key key;
	private float yOffsetRatio;

	private static final String TAG = "KeyHistoryItem"; 

	public KeyHistoryItem(Book doc, Key verse, float yOffsetRatio) {
		super();
		this.document = doc;
		this.key = verse;
		this.yOffsetRatio = yOffsetRatio;
	}
	
	/* (non-Javadoc)
	 * @see net.bible.service.history.HistoryItem#revertTo()
	 */
	@Override
	public void revertTo() {
		CurrentPageManager.getInstance().setCurrentDocumentAndKeyAndOffset(document, key, yOffsetRatio);

		// finish current activity if not the Main screen
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		if (!(currentActivity instanceof MainBibleActivity)) {
			currentActivity.finish();
		}

	}

	
	@Override
	public String getDescription() {
		return document.getInitials()+" "+key.getName();
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
		result = prime * result
				+ ((document == null) ? 0 : document.getInitials().hashCode());
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
		KeyHistoryItem other = (KeyHistoryItem) obj;
		if (document == null) {
			if (other.document != null)
				return false;
		} else if (!document.getInitials().equals(other.document.getInitials()))
			return false;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		return true;
	}
}
