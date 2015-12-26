package net.bible.android.control.download;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.download.RepoFactory;
import net.bible.service.download.XiphosRepo;
import net.bible.service.font.FontControl;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.util.Language;
import org.crosswire.common.util.LucidException;
import org.crosswire.common.util.Version;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;

import android.util.Log;

/** Support the download screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DownloadControl {

	public enum BookInstallStatus {INSTALLED, NOT_INSTALLED, BEING_INSTALLED, UPGRADE_AVAILABLE};

	private XiphosRepo xiphosRepo;
	
	private FontControl fontControl;

	private static final String TAG = "DownloadControl";
	
	public DownloadControl() {
		this.xiphosRepo = RepoFactory.getInstance().getXiphosRepo();
		this.fontControl = FontControl.getInstance();
	}
	
	/** pre-download document checks
	 */
	public boolean checkDownloadOkay() {
		boolean okay = true;
		
    	if (CommonUtils.getSDCardMegsFree()<SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
        	Dialogs.getInstance().showErrorMsg(R.string.storage_space_warning);
        	okay = false;
    	} else if (!CommonUtils.isInternetAvailable()) {
        	Dialogs.getInstance().showErrorMsg(R.string.no_internet_connection);
        	okay = false;
    	}
    	
		return okay;
	}
	
	/** return a list of all available docs that have not already been downloaded, have no lang, or don't work
	 * 
	 * @return
	 */
	public List<Book> getDownloadableDocuments(boolean refresh) {
		List<Book> availableDocs = null;
		try {
			availableDocs = SwordDocumentFacade.getInstance().getDownloadableDocuments(refresh);
			
			// there are a number of books we need to filter out of the download list for various reasons
        	for (Iterator<Book> iter=availableDocs.iterator(); iter.hasNext(); ) {
        		Book doc = iter.next();
        		if (doc.getLanguage()==null) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because it has no language");
        			iter.remove();
        		} else if (doc.isQuestionable()) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because it is questionable");
        			iter.remove();
        		} else if (doc.getInitials().equalsIgnoreCase("westminster")) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because some sections are too large for a mobile phone e.g. Q91-150");
        			iter.remove();
        		} else if (doc.getInitials().equalsIgnoreCase("BDBGlosses_Strongs")) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because I still need to make it work");
        			iter.remove();
        		} else if (doc.getInitials().equalsIgnoreCase("passion")) {
        			Log.d(TAG, "Ignoring "+doc.getInitials());
        			iter.remove();
        		} else if (doc.getInitials().equals("WebstersDict")) {
        			Log.d(TAG, "Ignoring "+doc.getInitials()+" because it is too big and crashes dictionary code");
        			iter.remove();
        		}
        	}
        	
        	// get fonts.properties at the same time as repo list, or if not yet downloaded
       		// the download happens in another thread
       		fontControl.checkFontPropertiesFile(refresh);
       		
       		Collections.sort(availableDocs);
       		
		} catch (Exception e) {
			Log.e(TAG, "Error downloading document list", e);
			availableDocs = new ArrayList<Book>();
		}
		return availableDocs;
	}

	public List<Language> sortLanguages(Collection<Language> languages) {
		List<Language> languageList = new ArrayList<>();

		if (languages!=null) {
			languageList.addAll(languages);

			// sort languages alphabetically
        	Collections.sort(languageList, new RelevantLanguageSorter(Books.installed().getBooks()));
		}
		return languageList;

	}
	
	public void downloadDocument(Book document) throws LucidException {
    	Log.d(TAG, "Download requested");
    	if (xiphosRepo.needsPostDownloadAction(document)) {
    		xiphosRepo.addHandler(document);
    	}
    	
		// the download happens in another thread
		SwordDocumentFacade.getInstance().downloadDocument(document);

		// if a font is required then download that too
		String font = fontControl.getFontForBook(document);
    	if (!StringUtils.isEmpty(font) && !fontControl.exists(font)) {
    		// the download happens in another thread
    		fontControl.downloadFont(font);
    	}
	}

	/** return install status - installed, not inst, or upgrade **/
	public BookInstallStatus getBookInstallStatus(Book book) {
		Book installedBook = SwordDocumentFacade.getInstance().getDocumentByInitials(book.getInitials());
		if (installedBook!=null) {
			// see if the new book is a later version
			try {
	    		Version newVersionObj = new Version(book.getBookMetaData().getProperty("Version"));
	    		Version installedVersionObj = new Version(installedBook.getBookMetaData().getProperty("Version"));
	    		if (newVersionObj!=null && installedVersionObj!=null && 
	    			newVersionObj.compareTo(installedVersionObj)>0) {
	    			return BookInstallStatus.UPGRADE_AVAILABLE;
	    		}
			} catch (Exception e) {
				Log.e(TAG,  "Error comparing versions", e);
				// probably not the same version if an error occurred comparing
    			return BookInstallStatus.UPGRADE_AVAILABLE;
			}
			// otherwise same book is already installed
			return BookInstallStatus.INSTALLED;
		} else {
			return BookInstallStatus.NOT_INSTALLED;
		}
	}
}
