package net.bible.android.control.download;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;

import android.util.Log;

public class DownloadControl {

	private static final String TAG = "DownloadControl";
	
	/** return a list of all available docs that have not already been downloaded, have no lang, or don't work
	 * 
	 * @return
	 */
	public List<Book> getDownloadableDocuments(boolean refresh) {
		List<Book> availableDocs = null;
		try {
			availableDocs = SwordApi.getInstance().getDownloadableDocuments(refresh);
			
			// create a Map of installed doc names so we can remove them from the list of downloadable books
			// need to compare using lower case because Xiphos repo books are lower case
			List<Book> installedDocs = SwordApi.getInstance().getDocuments();
			Map<String, Object> installedDocNames = new HashMap<String, Object>();
			for (Book book : installedDocs) {
				installedDocNames.put(book.getName().toLowerCase(), null);
			}
			
        	for (Iterator<Book> iter=availableDocs.iterator(); iter.hasNext(); ) {
        		Book doc = iter.next();
        		if (doc.getLanguage()==null) {
        			Log.d(TAG, "Ignoring "+doc.getName()+" because it has no language");
        			iter.remove();
        		} else if (installedDocNames.containsKey(doc.getName().toLowerCase())) {
        			Log.d(TAG, "Ignoring "+doc.getName()+" because already installed");
        			iter.remove();
        		} else if (doc.isQuestionable()) {
        			Log.d(TAG, "Ignoring "+doc.getName()+" because it is questionable");
        			iter.remove();
        		} else if (doc.getInitials().equalsIgnoreCase("westminster")) {
        			Log.d(TAG, "Removing "+doc.getName()+" because some sections are too large for a mobile phone e.g. Q91-150");
        			iter.remove();
        		} else if (doc.getInitials().equalsIgnoreCase("passion")) {
        			Log.d(TAG, "Removing "+doc.getName());
        			iter.remove();
        		} else if (doc.getInitials().equals("WebstersDict")) {
        			Log.d(TAG, "Removing "+doc.getName()+" because it is too big and crashes dictionary code");
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
