package net.bible.service.history;

import net.bible.android.control.ControlFactory;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.util.Log;

/**
 * A normal item in the history list that relates to a document being shown in the main activity view 
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class KeyHistoryItem extends HistoryItemBase {
	private Book document;
	private Key key;
	private float yOffsetRatio;

	@Override
	public boolean isFromPersistentHistory() {
		return fromPersistent;
	}

	public void setFromPersistent(boolean fromPersistent) {
		this.fromPersistent = fromPersistent;
	}

	private boolean fromPersistent;

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
		ControlFactory.getInstance().getCurrentPageControl().setCurrentDocumentAndKeyAndOffset(document, key, yOffsetRatio);
	}

	
	@Override
	public String getDescription() {
		StringBuffer desc = new StringBuffer();
		try {
			String verseDesc = CommonUtils.getKeyDescription(key);
			desc.append(document.getAbbreviation()).append(" ").append(verseDesc);
		} catch (Exception e) {
			Log.e(TAG, "Error getting description", e);
		}
		return desc.toString();
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

	//TODO use Book.equals and Key.equals in the below
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
		} else if (document.getInitials() == null) {
			if (other.document.getInitials() != null)
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

	public Book getDoc() {
		return document;
	}
}
