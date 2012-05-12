package net.bible.service.sword;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bible.android.SharedConstants;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;
import net.bible.service.download.DownloadManager;
import net.bible.service.download.RepoBase;
import net.bible.service.download.RepoFactory;

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
import org.crosswire.jsword.book.sword.SwordBookPath;
import org.crosswire.jsword.book.sword.SwordConstants;
import org.crosswire.jsword.index.IndexManager;
import org.crosswire.jsword.index.IndexManagerFactory;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.lucene.PdaLuceneIndexManager;

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

				// the second value below is the one which is used in effectively all circumstances
		        CWProject.setHome("jsword.home", moduleDir.getAbsolutePath(), SharedConstants.MANUAL_INSTALL_DIR.getAbsolutePath()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		        // the following causes Sword to initialise itself and can take quite a few seconds
				SwordBookPath.setAugmentPath(new File[] {SharedConstants.MANUAL_INSTALL_DIR});  // add manual install dir to this list
				
				// 10 sec is too low, 15 may do but put it at 20 secs
				WebResource.setTimeout(20000);
				
				// because the above line causes initialisation set the is initialised flag here
				isSwordLoaded = true;
				
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
		log.debug("Getting commentaries");
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

	public List<Book> getDictionaries() {
		log.debug("Getting dictionaries");
		List<Book> documents = Books.installed().getBooks(BookFilters.getDictionaries());
		log.debug("Got dictionaries, Num="+documents.size());
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
		// default to StrongsRealGreek
		Book strongs = Books.installed().getBook("StrongsRealGreek");
		if (strongs!=null) {
			return strongs;
		}

		strongs = Defaults.getGreekDefinitions();
		return strongs; 
	}

	public Book getDefaultStrongsHebrewDictionary() {
		// default to StrongsRealHebrew
		Book strongs = Books.installed().getBook("StrongsRealHebrew");
		if (strongs!=null) {
			return strongs;
		}

		// Should Defaults prefer BDB to StrongsHebrew - well it does
		strongs = Defaults.getHebrewDefinitions();
		return strongs; 
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
		log.debug("Getting book:"+initials);

		return Books.installed().getBook(initials);
	}
	
	public List<Book> getDownloadableDocuments(boolean refresh) throws InstallException {
		log.debug("Getting downloadable documents");
		RepoFactory repoFactory = RepoFactory.getInstance();
		
		//store books in a Set to ensure only one of each type and allow override from AndBible repo if necessary
        Set<Book> allBooks = new HashSet<Book>();

        allBooks.addAll(repoFactory.getAndBibleRepo().getRepoBooks(refresh));
        
        allBooks.addAll(repoFactory.getCrosswireRepo().getRepoBooks(refresh));

        allBooks.addAll(repoFactory.getXiphosRepo().getRepoBooks(refresh));

        allBooks.addAll(repoFactory.getBetaRepo().getRepoBooks(refresh));

        // get them in the correct order
        List<Book> bookList = new ArrayList<Book>(allBooks);
        Collections.sort(bookList);

		return bookList;	
	}

	public void downloadDocument(Book document) throws InstallException, BookException {
		RepoBase repo = RepoFactory.getInstance().getRepoForBook(document);

		repo.downloadDocument(document);
	}

	public boolean isIndexDownloadAvailable(Book document) throws InstallException, BookException {
		// not sure how to integrate reuse this in JSword index download
		Version versionObj = (Version)document.getBookMetaData().getProperty("Version");
        String version = versionObj==null ? null : versionObj.toString();
        String versionSuffix = version!=null ? "-"+version : "";

		String url = "http://www.crosswire.org/and-bible/indices/v1/"+document.getInitials()+versionSuffix+".zip";
		return CommonUtils.isHttpUrlAvailable(url);
	}

	public void downloadIndex(Book document) throws InstallException, BookException {
		DownloadManager downloadManager = new DownloadManager();
		downloadManager.installIndex(RepoFactory.getInstance().getAndBibleRepo().getRepoName(), document);
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
	 * 
	 * @param book
	 * @throws BookException
	 */
	public void ensureIndexCreation(Book book) throws BookException {
    	log.debug("ensureIndexCreation");

    	// ensure this isn't just the user re-clicking the Index button
		if (!book.getIndexStatus().equals(IndexStatus.CREATING) && !book.getIndexStatus().equals(IndexStatus.SCHEDULED)) {

			PdaLuceneIndexManager lim = new PdaLuceneIndexManager();
	        lim.scheduleIndexCreation(book);
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
	 * 
	 * @return
	 */
	static public boolean isSwordLoaded() {
		return isSwordLoaded;
	}
}
