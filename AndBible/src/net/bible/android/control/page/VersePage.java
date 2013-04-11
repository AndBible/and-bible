package net.bible.android.control.page;

import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

import android.util.Log;


public abstract class VersePage extends CurrentPageBase {

	private static final String TAG = "CurrentPageBase";
	
	protected VersePage(boolean shareKeyBetweenDocs) {
		super(shareKeyBetweenDocs);
	}

	//TODO av11n - need to make this method shared between cmtry and bible
	public Versification getVersification() {
		try {
			// Bibles must be a PassageBook
			return ((AbstractPassageBook)getCurrentDocument()).getVersification();
		} catch (Exception e) {
			Log.e(TAG, "Error getting versification for Book", e);
			return Versifications.instance().getVersification("KJV");
		}
	}


}
