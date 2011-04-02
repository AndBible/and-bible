package net.bible.android.control.download;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.bible.service.download.XiphosRepo;
import net.bible.service.sword.SwordApi;

import org.crosswire.common.util.LucidException;
import org.crosswire.jsword.book.Book;

import android.util.Log;

public class DownloadControl {

	private static final String TAG = "DownloadControl";
	
	private XiphosRepo postDownloadActions = new XiphosRepo();
	
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
			Map<String, Object> installedDocInitials = new HashMap<String, Object>();
			for (Book book : installedDocs) {
    			Log.d(TAG, "Install list "+book.getInitials()+"/"+book.getInitials().toLowerCase());
				installedDocInitials.put(book.getInitials().toLowerCase(), null);
			}
			
			// there are a number of books we need to filter out of the download list for various reasons
        	for (Iterator<Book> iter=availableDocs.iterator(); iter.hasNext(); ) {
        		Book doc = iter.next();
        		if (doc.getLanguage()==null) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because it has no language");
        			iter.remove();
        		} else if (installedDocInitials.containsKey(doc.getInitials().toLowerCase())) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because already installed");
        			iter.remove();
        		} else if (doc.isQuestionable()) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because it is questionable");
        			iter.remove();
        		} else if (doc.getInitials().equalsIgnoreCase("westminster")) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because some sections are too large for a mobile phone e.g. Q91-150");
        			iter.remove();
        		} else if (doc.getInitials().equalsIgnoreCase("passion")) {
        			Log.d(TAG, "Ignoring "+doc.getInitials());
        			iter.remove();
        		} else if (doc.getInitials().equals("WebstersDict")) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because it is too big and crashes dictionary code");
        			iter.remove();
        		}
        	}
			
		} catch (Exception e) {
			Log.e(TAG, "Error downloading document list", e);
			availableDocs = new ArrayList<Book>();
		}
		return availableDocs;
	}
	
	public void downloadDocument(Book document) throws LucidException {
    	Log.d(TAG, "Download requested");
    	if (postDownloadActions.needsPostDownloadAction(document)) {
    		postDownloadActions.addHandler(document);
    	}
    	
		// the download happens in another thread
		SwordApi.getInstance().downloadDocument(document);
	}
}
