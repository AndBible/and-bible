package net.bible.service.sword;

import net.bible.android.SharedConstants;
import net.bible.android.control.versification.VersificationMappingInitializer;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;
import net.bible.service.download.DownloadManager;
import net.bible.service.download.RepoBookDeduplicator;
import net.bible.service.download.RepoFactory;
import net.bible.service.sword.index.IndexCreator;

import org.crosswire.common.util.CWProject;
import org.crosswire.common.util.Version;
import org.crosswire.common.util.WebResource;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.Defaults;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.install.InstallException;
import org.crosswire.jsword.book.sword.SwordBookMetaData;
import org.crosswire.jsword.book.sword.SwordBookPath;
import org.crosswire.jsword.book.sword.SwordConstants;
import org.crosswire.jsword.index.IndexManager;
import org.crosswire.jsword.index.IndexManagerFactory;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.passage.PassageKeyFactory;
import org.crosswire.jsword.passage.PassageType;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** JSword facade
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SwordDocumentFacade {
	private static SwordDocumentFacade singleton;

	private static final String LUCENE_DIR = "lucene";
	
	private static BookFilter SUPPORTED_DOCUMENT_TYPES = new AcceptableBookTypeFilter();

	private static boolean isSwordLoaded;
	
	// set to false for testing
	public static boolean isAndroid = true; //CommonUtils.isAndroid();
	
	private static final Logger log = new Logger(SwordDocumentFacade.class.getName()); 

	public static SwordDocumentFacade getInstance() {
		if (singleton==null) {
			synchronized(SwordDocumentFacade.class)  {
				if (singleton==null) {
					SwordDocumentFacade instance = new SwordDocumentFacade();
					instance.initialise();
					singleton = instance;
				}
			}
		}
		return singleton;
	}

	private SwordDocumentFacade() {
	}
	
	private void initialise() {
		try {
			if (isAndroid) {
				// ensure required module directories exist and register them with jsword
				File moduleDir = SharedConstants.MODULE_DIR;

				// main module dir
				CommonUtils.ensureDirExists(moduleDir);
				// mods.d
				CommonUtils.ensureDirExists(new File(moduleDir, SwordConstants.DIR_CONF));
				// modules
				CommonUtils.ensureDirExists(new File(moduleDir, SwordConstants.DIR_DATA));
				// indexes
				CommonUtils.ensureDirExists(new File(moduleDir, LUCENE_DIR));
				//fonts
				CommonUtils.ensureDirExists(SharedConstants.FONT_DIR);

				// Optimize for less memory
				PassageKeyFactory.setDefaultType(PassageType.MIX);
				
				// the following are required to set the read and write dirs for module properties, initialised during the following call to setHome
				System.setProperty("jsword.home", moduleDir.getAbsolutePath());
				CWProject.instance().setFrontendName("and-bible");

				// the second value below is the one which is used in effectively all circumstances
				CWProject.setHome("jsword.home", moduleDir.getAbsolutePath(), SharedConstants.MANUAL_INSTALL_DIR.getAbsolutePath());

				// the following causes Sword to initialise itself and can take quite a few seconds
				SwordBookPath.setAugmentPath(new File[] {SharedConstants.MANUAL_INSTALL_DIR});  // add manual install dir to this list
				
				// 10 sec is too low, 15 may do but put it at 20 secs
				WebResource.setTimeout(20000);
				
				// because the above line causes initialisation set the is initialised flag here
				isSwordLoaded = true;
				
				new VersificationMappingInitializer().startListening();

				log.debug(("Sword paths:"+getPaths()));
			}
			
		} catch (Exception e) {
			log.error("Error initialising", e);
		}
	}

	public void reset() {
		singleton = null;
		isSwordLoaded = false;
	}
	
	public List<Book> getBibles() {
		log.debug("Getting bibles");
		List<Book> documents = Books.installed().getBooks(BookFilters.getBibles());
		log.debug("Got bibles, Num="+documents.size());
		isSwordLoaded = true;
		return documents;
	}

	public List<Book> getBooks(final BookCategory bookCategory) {
		log.debug("Getting books of type "+bookCategory.getName());
		List<Book> documents = Books.installed().getBooks(new BookFilter() {
			@Override
	        public boolean test(Book book) {
	            return book.getBookCategory().equals(bookCategory) && !book.isLocked();
			}
		});
		log.debug("Got books, Num="+documents.size());
		isSwordLoaded = true;
		return documents;
	}

	/** return all supported documents - bibles and commentaries for now
	 */
	public List<Book> getDocuments() {
		log.debug("Getting books");
		// currently only bibles and commentaries are supported
		List<Book> allDocuments = Books.installed().getBooks(SUPPORTED_DOCUMENT_TYPES);
		
		log.debug("Got books, Num="+allDocuments.size());
		isSwordLoaded = true;
		return allDocuments;
	}

	/** prefer the Real alternatives to the default versions because they contain the native Greek Hebrew words
	 */
	public Book getDefaultStrongsGreekDictionary() {
		// default to StrongsRealGreek or StrongsGreek
		String[] preferredBooks = {"StrongsRealGreek", "StrongsGreek"};
		for (String prefBook : preferredBooks) {
			Book strongs = Books.installed().getBook(prefBook);
			if (strongs!=null) {
				return strongs;
			}
		}

		return Defaults.getGreekDefinitions();
	}

	public Book getDefaultStrongsHebrewDictionary() {
		// default to StrongsRealHebrew or StrongsHebrew
		String[] preferredBooks = {"StrongsRealHebrew", "StrongsHebrew"};
		for (String prefBook : preferredBooks) {
			Book strongs = Books.installed().getBook(prefBook);
			if (strongs!=null) {
				return strongs;
			}
		}

		return Defaults.getHebrewDefinitions();
	}

	public Book getDefaultBibleWithStrongs() {
		List<Book> bibles = getBibles();
		for (Book book : bibles) {
			if (book.hasFeature(FeatureType.STRONGS_NUMBERS)) {
				if (book.getIndexStatus().equals(IndexStatus.DONE)) {
					return book;
				}
			}
		}
		return null;
	}
	
	public Book getDocumentByInitials(String initials) {
		return Books.installed().getBook(initials);
	}
	
	public List<Book> getDownloadableDocuments(boolean refresh) throws InstallException {
		log.debug("Getting downloadable documents.  Refresh:"+refresh);
		try {
			// there are so many sbmd's to load that we can only load what is required for the display list.  
			// If About is selected or a document is downloaded the sbmd is then loaded fully.
			SwordBookMetaData.setPartialLoading(true);
			
			RepoFactory repoFactory = RepoFactory.getInstance();
	
			RepoBookDeduplicator repoBookDeduplicator = new RepoBookDeduplicator();
	
			repoBookDeduplicator.addAll(repoFactory.getAndBibleRepo().getRepoBooks(refresh));
	        
			repoBookDeduplicator.addAll(repoFactory.getIBTRepo().getRepoBooks(refresh));
	
			repoBookDeduplicator.addAll(repoFactory.getCrosswireRepo().getRepoBooks(refresh));
	
			repoBookDeduplicator.addAll(repoFactory.getXiphosRepo().getRepoBooks(refresh));
	
			repoBookDeduplicator.addAll(repoFactory.getEBibleRepo().getRepoBooks(refresh));
	        
			// beta repo must never override live books especially if later version so use addIfNotExists
			repoBookDeduplicator.addIfNotExists(repoFactory.getBetaRepo().getRepoBooks(refresh));
	
	        List<Book> bookList = repoBookDeduplicator.getBooks();
	
	        // get them in the correct order
	        Collections.sort(bookList);
	
			return bookList;
		} finally {
			SwordBookMetaData.setPartialLoading(false);
		}
	}

	public boolean isIndexDownloadAvailable(Book document) throws InstallException, BookException {
		// not sure how to integrate reuse this in JSword index download
		String version = document.getBookMetaData().getProperty("Version");

        String versionSuffix = version!=null ? "-" + new Version(version).toString() : "";

		String url = "http://www.crosswire.org/and-bible/indices/v1/"+document.getInitials()+versionSuffix+".zip";
		return CommonUtils.isHttpUrlAvailable(url);
	}

	public void downloadIndex(Book document) throws InstallException, BookException {
		DownloadManager downloadManager = new DownloadManager();
		downloadManager.installIndexInNewThread(RepoFactory.getInstance().getAndBibleRepo().getRepoName(), document);
	}
	
	public void deleteDocument(Book document) throws BookException {
		// make sure we have the correct Book and not just a copy e.g. one from a Download Manager
		Book realDocument = getDocumentByInitials(document.getInitials());
		
		// delete index first if it exists but wrap in try to ensure an attempt is made to delete the document
		try {
	        IndexManager imanager = IndexManagerFactory.getIndexManager();
	        if (imanager.isIndexed(realDocument)) {
	            imanager.deleteIndex(realDocument);
	        }
		} catch (Exception e) {
			// just log index delete error, deleting doc is the important thing
			log.error("Error deleting document index", e);
		}

        document.getDriver().delete(realDocument);
	}
	
	public void deleteDocumentIndex(Book document) throws BookException {
		// make sure we have the correct Book and not just a copy e.g. one from a Download Manager
		Book realDocument = getDocumentByInitials(document.getInitials());

		IndexManager imanager = IndexManagerFactory.getIndexManager();
        if (imanager.isIndexed(realDocument)) {
            imanager.deleteIndex(realDocument);
        }
	}
	

	/** this custom index creation has been optimised for slow, low memory devices
	 * If an index is in progress then nothing will happen
	 */
	public void ensureIndexCreation(Book book) throws BookException {
    	log.debug("ensureIndexCreation");

    	// ensure this isn't just the user re-clicking the Index button
		if (!book.getIndexStatus().equals(IndexStatus.CREATING) && !book.getIndexStatus().equals(IndexStatus.SCHEDULED)) {

			IndexCreator ic = new IndexCreator();
	        ic.scheduleIndexCreation(book);
		}
	}
	
	private String getPaths() {
		String text = "Paths:";
		try {
			// SwordBookPath.setAugmentPath(new File[] {new
			// File("/data/bible")});
			File[] swordBookPaths = SwordBookPath.getSwordPath();
			for (File file : swordBookPaths) {
				text += file.getAbsolutePath();
			}
			text += "Augmented paths:";
			File[] augBookPaths = SwordBookPath.getAugmentPath();
			for (File file : augBookPaths) {
				text += file.getAbsolutePath();
			}
		} catch (Exception e) {
			text += e.getMessage();
		}
		return text;
	}

	public static void setAndroid(boolean isAndroid) {
		SwordDocumentFacade.isAndroid = isAndroid;
	}

	/** needs to be static because otherwise the constructor triggers initialisation
	 */
	static public boolean isSwordLoaded() {
		return isSwordLoaded;
	}
}
