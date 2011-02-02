package net.bible.android.control.download;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;

import android.util.Log;

public class DownloadControl {

	private static final String TAG = "DownloadControl";
	
	/** return a list of all available docs that have not already been downloaded, have no lang, or don't work
	 * 
	 * @return
	 */
	public List<Book> getDownloadableDocuments() {
		List<Book> availableDocs = null;
		try {
			availableDocs = SwordApi.getInstance().getDownloadableDocuments();
			List<Book> downloadedDocs = SwordApi.getInstance().getDocuments();
			availableDocs.removeAll(downloadedDocs);
			
        	for (Iterator<Book> iter=availableDocs.iterator(); iter.hasNext(); ) {
        		Book doc = iter.next();
        		if (doc.getLanguage()==null) {
        			Log.d(TAG, "Ignoring "+doc.getName()+" because it has no language");
        			iter.remove();
        		}
        		if (doc.getInitials().equals("WebstersDict")) {
        			Log.d(TAG, "Removing "+doc.getName()+" because it is too big and crashed dictionary code");
        			iter.remove();
        		}
        	}
			
		} catch (Exception e) {
			Log.e(TAG, "Error downloading document list", e);
			availableDocs = new ArrayList<Book>();
		}
		return availableDocs;
	}
}
