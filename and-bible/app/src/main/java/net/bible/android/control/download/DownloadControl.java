package net.bible.android.control.download;

import android.util.Log;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.download.DownloadManager;
import net.bible.service.download.RepoBase;
import net.bible.service.download.RepoFactory;
import net.bible.service.download.XiphosRepo;
import net.bible.service.font.FontControl;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.util.Language;
import org.crosswire.common.util.LucidException;
import org.crosswire.common.util.Version;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.sword.SwordBookMetaData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static net.bible.android.control.download.DocumentStatus.DocumentInstallStatus.BEING_INSTALLED;
import static net.bible.android.control.download.DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING;
import static net.bible.android.control.download.DocumentStatus.DocumentInstallStatus.INSTALLED;
import static net.bible.android.control.download.DocumentStatus.DocumentInstallStatus.NOT_INSTALLED;
import static net.bible.android.control.download.DocumentStatus.DocumentInstallStatus.UPGRADE_AVAILABLE;

/** Support the download screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DownloadControl {

	private DocumentDownloadProgressCache documentDownloadProgressCache;

	private final DownloadQueue downloadQueue;

	private final XiphosRepo xiphosRepo;
	
	private final FontControl fontControl;

	private static final String TAG = "DownloadControl";
	
	public DownloadControl(DownloadQueue downloadQueue, XiphosRepo xiphosRepo, FontControl fontControl) {
		this.downloadQueue = downloadQueue;
		this.xiphosRepo = xiphosRepo;
		this.fontControl = fontControl;

		// Listen for Progress changes and update the ui
		documentDownloadProgressCache = new DocumentDownloadProgressCache();
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
	
	/** @return a list of all available docs that have not already been downloaded, have no lang, or don't work
	 */
	public List<Book> getDownloadableDocuments(boolean refresh) {
		List<Book> availableDocs;
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
			availableDocs = new ArrayList<>();
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
    	
    	// ensure SBMD is fully, not just partially, loaded
    	BookMetaData bmd = document.getBookMetaData();
    	if (bmd!=null && bmd instanceof SwordBookMetaData) {
    		// load full bmd but must retain repo key 
    		String repoKey = bmd.getProperty(DownloadManager.REPOSITORY_KEY);
    		((SwordBookMetaData)bmd).reload();
    		bmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey);
    	}

		if (!downloadQueue.isInQueue(document)) {

			if (xiphosRepo.needsPostDownloadAction(document)) {
				xiphosRepo.addHandler(document);
			}

			// the download happens in another thread
			RepoBase repo = RepoFactory.getInstance().getRepoForBook(document);
			downloadQueue.addDocumentToDownloadQueue(document, repo);

			// if a font is required then download that too
			String font = fontControl.getFontForBook(document);
			if (!StringUtils.isEmpty(font) && !fontControl.exists(font)) {
				// the download happens in another thread
				fontControl.downloadFont(font);
			}
		}
	}

	/** return install status - installed, not inst, or upgrade **/
	public DocumentStatus getDocumentStatus(Book document) {
		String initials = document.getInitials();
		if (downloadQueue.isInQueue(document)) {
			return new DocumentStatus(initials, BEING_INSTALLED, documentDownloadProgressCache.getPercentDone(document));
		}
		if (downloadQueue.isErrorDownloading(document)) {
			return new DocumentStatus(initials, ERROR_DOWNLOADING, 0);
		}

		Book installedBook = SwordDocumentFacade.getInstance().getDocumentByInitials(document.getInitials());
		if (installedBook!=null) {
			// see if the new document is a later version
			try {
	    		Version newVersionObj = new Version(document.getBookMetaData().getProperty("Version"));
	    		Version installedVersionObj = new Version(installedBook.getBookMetaData().getProperty("Version"));
	    		if (newVersionObj.compareTo(installedVersionObj)>0) {
	    			return new DocumentStatus(initials, UPGRADE_AVAILABLE, 100);
	    		}
			} catch (Exception e) {
				Log.e(TAG,  "Error comparing versions", e);
				// probably not the same version if an error occurred comparing
				return new DocumentStatus(initials, UPGRADE_AVAILABLE, 100);
			}
			// otherwise same document is already installed
			return new DocumentStatus(initials, INSTALLED, 100);
		} else {
			return new DocumentStatus(initials, NOT_INSTALLED, 0);
		}
	}

	public void startMonitoringDownloads() {
		documentDownloadProgressCache.startMonitoringDownloads();
	}

	public void stopMonitoringDownloads() {
		documentDownloadProgressCache.stopMonitoringDownloads();
	}
}
