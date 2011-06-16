package net.bible.service.sword;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.bible.android.BibleApplication;
import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Constants;
import net.bible.service.common.ParseException;
import net.bible.service.download.DownloadManager;
import net.bible.service.download.XiphosRepo;
import net.bible.service.format.FormattedDocument;
import net.bible.service.format.OsisToCanonicalTextSaxHandler;
import net.bible.service.format.OsisToHtmlSaxHandler;

import org.crosswire.common.util.CWProject;
import org.crosswire.common.util.Version;
import org.crosswire.common.util.WebResource;
import org.crosswire.common.xml.SAXEventProvider;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.FeatureType;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.book.install.InstallException;
import org.crosswire.jsword.book.sword.SwordBookPath;
import org.crosswire.jsword.book.sword.SwordConstants;
import org.crosswire.jsword.index.IndexManager;
import org.crosswire.jsword.index.IndexManagerFactory;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.lucene.PdaLuceneIndexManager;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.Passage;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import android.content.SharedPreferences;
import android.util.Log;

/** JSword facade
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SwordApi {
	private static final String TAG = "SwordApi";
	private static SwordApi singleton;
	private static String NIGHT_MODE_STYLESHEET = "night_mode.css";

	// just keep one of these because it is called in the tight document indexing loop and isn't very complex
	OsisToCanonicalTextSaxHandler osisToCanonicalTextSaxHandler = new OsisToCanonicalTextSaxHandler();

	private static final String LUCENE_DIR = "lucene";
	
	private static final String CROSSWIRE_REPOSITORY = "CrossWire";
	
	private static BookFilter SUPPORTED_DOCUMENT_TYPES = new AcceptableBookTypeFilter();

	private static boolean isSwordLoaded;
	
	// set to false for testing
	public static boolean isAndroid = true; //CommonUtils.isAndroid();
	
    private static final Logger log = new Logger(SwordApi.class.getName()); 

	public static SwordApi getInstance() {
		if (singleton==null) {
			synchronized(SwordApi.class)  {
				if (singleton==null) {
					SwordApi instance = new SwordApi();
					instance.initialise();
					singleton = instance;
				}
			}
		}
		return singleton;
	}

	private SwordApi() {
	}
	
	private void initialise() {
		try {
			if (isAndroid) {
				// ensure required module directories exist and register them with jsword
				File moduleDir = SharedConstants.MODULE_DIR;

				// main module dir
				ensureDirExists(moduleDir);
				// mods.d
				ensureDirExists(new File(moduleDir, SwordConstants.DIR_CONF));
				// modules
				ensureDirExists(new File(moduleDir, SwordConstants.DIR_DATA));
				// indexes
				ensureDirExists(new File(moduleDir, LUCENE_DIR));

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
	 * 
	 * @return
	 */
	public List<Book> getDocuments() {
		log.debug("Getting books");
		// currently only bibles and commentaries are supported
		List<Book> allDocuments = Books.installed().getBooks(SUPPORTED_DOCUMENT_TYPES);
		
		log.debug("Got books, Num="+allDocuments.size());
		isSwordLoaded = true;
		return allDocuments;
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

		//CrossWire
		DownloadManager crossWireDownloadManager = new DownloadManager();
        List<Book> crosswireBookList = crossWireDownloadManager.getDownloadableBooks(SUPPORTED_DOCUMENT_TYPES, CROSSWIRE_REPOSITORY, refresh);

        List<Book> allBooks = new ArrayList<Book>(crosswireBookList);
        
		XiphosRepo xiphosRepo = new XiphosRepo();
        allBooks.addAll(xiphosRepo.getXiphosRepoBooks());

        // get them in the coorect order
        Collections.sort(allBooks);

		return allBooks;	
	}

	public void downloadDocument(Book document) throws InstallException, BookException {
		DownloadManager downloadManager = new DownloadManager();
		String repo = (String)document.getProperty(DownloadManager.REPOSITORY_KEY);
		if (repo==null) {
			repo = CROSSWIRE_REPOSITORY;
		}
		downloadManager.installBook(repo, document);
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
		downloadManager.installIndex(CROSSWIRE_REPOSITORY, document);
	}
	
	public void deleteDocument(Book document) throws BookException {
		// delete index first if it exists but wrap in try to ensure an attempt is made to delete the document
		try {
	        IndexManager imanager = IndexManagerFactory.getIndexManager();
	        if (imanager.isIndexed(document)) {
	            imanager.deleteIndex(document);
	        }
		} catch (Exception e) {
			// just log index delete error, deleting doc is the important thing
			Log.e(TAG, "Error deleting document index", e);
		}

        document.getDriver().delete(document);
	}
	
	public void deleteDocumentIndex(Book document) throws BookException {
        IndexManager imanager = IndexManagerFactory.getIndexManager();
        if (imanager.isIndexed(document)) {
            imanager.deleteIndex(document);
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

	/** top level method to fetch html from the raw document data
	 * 
	 * @param book
	 * @param key
	 * @param maxKeyCount
	 * @return
	 * @throws NoSuchKeyException
	 * @throws BookException
	 * @throws IOException
	 * @throws SAXException
	 * @throws URISyntaxException
	 * @throws ParserConfigurationException
	 */
	public synchronized FormattedDocument readHtmlText(Book book, Key key, int maxKeyCount) throws ParseException
	{
		FormattedDocument retVal = new FormattedDocument();
		if (book==null || key==null) {
			//TODO this should include css to change to night mode if necessary
			retVal.setHtmlPassage("");
		} else if (!book.contains(key)) {
			//TODO this should include css to change to night mode if necessary
			Log.w(TAG, "KEY:"+key+" not found in doc:"+book);
			retVal.setHtmlPassage("Not found in document");
		} else {
			// we have a fast way of handling OSIS zText docs but WEB/HNV needs the superior JSword error recovery for mismatching tags 
			if ("OSIS".equals(book.getBookMetaData().getProperty("SourceType")) &&
				"zText".equals(book.getBookMetaData().getProperty("ModDrv")) &&
				!"FreCrampon".equals(book.getInitials()) &&
				!"AB".equals(book.getInitials()) &&
				!"WEB".equals(book.getInitials()) &&
				!"HNV".equals(book.getInitials())) {
				retVal = readHtmlTextOptimizedZTextOsis(book, key, maxKeyCount);
			} else {
				retVal = readHtmlTextStandardJSwordMethod(book, key, maxKeyCount);
			}
		}
		return retVal;
	}

	private FormattedDocument readHtmlTextOptimizedZTextOsis(Book book, Key key, int maxKeyCount) throws ParseException
	{
		log.debug("Using fast method to fetch document data");
		InputStream is = new OSISInputStream(book, key);

		OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book, key);
	
		SAXParser parser = getSAXParser();
		try {
			parser.parse(is, osisToHtml);
		} catch (Exception e) {
			log.error("Parsing error", e);
			throw new ParseException("Parsing error", e);
		}
		
		FormattedDocument retVal = new FormattedDocument();
		retVal.setHtmlPassage(osisToHtml.toString());
		retVal.setNotesList(osisToHtml.getNotesList());
		
        return retVal;
	}

	private FormattedDocument readHtmlTextStandardJSwordMethod(Book book, Key key, int maxKeyCount) throws ParseException
	{
		log.debug("Using standard JSword to fetch document data");
		FormattedDocument retVal = new FormattedDocument();

		try {
			BookData data = new BookData(book, key);		
			SAXEventProvider osissep = data.getSAXEventProvider();
			if (osissep == null) {
				Log.e(TAG, "No osis SEP returned");
				retVal.setHtmlPassage("Error fetching osis SEP"); //$NON-NLS-1$
			} else {
				OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book, key);
		
				osissep.provideSAXEvents(osisToHtml);
		
				retVal.setHtmlPassage(osisToHtml.toString());
				retVal.setNotesList(osisToHtml.getNotesList());
			}		
	        return retVal;
		} catch (Exception e) {
			log.error("Parsing error", e);
			throw new ParseException("Parsing error", e);
		}
	}

	/**
	 * Obtain a SAX event provider for the OSIS document representation of one
	 * or more book entries.
	 * 
	 * @param bookInitials
	 *            the book to use
	 * @param reference
	 *            a reference, appropriate for the book, of one or more entries
	 */
	public SAXEventProvider getOSIS(Book book, String reference, int maxKeyCount)
			throws BookException, NoSuchKeyException {
		Key key = null;
		if (BookCategory.BIBLE.equals(book.getBookCategory())) {
			key = book.getKey(reference);
			((Passage) key).trimVerses(maxKeyCount);
		} else {
			key = book.createEmptyKeyList();

			Iterator<Key> iter = book.getKey(reference).iterator();
			int count = 0;
			while (iter.hasNext()) {
				if (++count >= maxKeyCount) {
					break;
				}
				key.addAll(iter.next());
			}
		}

		BookData data = new BookData(book, key);
		return data.getSAXEventProvider();
	}

    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     * 
     * @param bookInitials
     *            the book to use
     * @param reference
     *            a reference, appropriate for the book, of one or more entries
     */
    public String getCanonicalText(Book book, Key key) throws NoSuchKeyException, BookException, ParseException {
    	try {
			BookData data = new BookData(book, key);
			SAXEventProvider osissep = data.getSAXEventProvider();
		
			ContentHandler osisHandler = new OsisToCanonicalTextSaxHandler();
		
			osissep.provideSAXEvents(osisHandler);
		
			return osisHandler.toString();
    	} catch (Exception e) {
    		Log.e(TAG, "Error getting text from book" , e);
    		return BibleApplication.getApplication().getString(R.string.error_occurred);
    	}
    }

    private SAXParser saxParser;
    private SAXParser getSAXParser() throws ParseException {
    	try {
	    	if (saxParser==null) {
	    		SAXParserFactory spf = SAXParserFactory.newInstance();
	    		spf.setValidating(false);
	   			saxParser = spf.newSAXParser();
	    	}
		} catch (Exception e) {
			log.error("SAX parser error", e);
			throw new ParseException("SAX parser error", e);
		}
		return saxParser;
    }
    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     * 
     * @param bookInitials
     *            the book to use
     * @param reference
     *            a reference, appropriate for the book, of one or more entries
     */
    public String getPlainText(Book book, String reference, int maxKeyCount) throws BookException, NoSuchKeyException {
    	String plainText = "";
    	try {
    		if (book != null) {
		        Key key = book.getKey(reference);
		        BookData data = new BookData(book, key);
		        plainText = OSISUtil.getCanonicalText(data.getOsisFragment());
    		}
    	} catch (Exception e) {
    		Log.e(TAG, "Error getting plain text", e);
    	}
    	return plainText;
    }

	public Key search(Book bible, String searchText) throws BookException {
// 		  example of fetching Strongs ref - only works with downloaded indexes!
//        Book book = getDocumentByInitials("KJV");
//        Key key1 = book.find("strong:h3068");
//        System.out.println("*** h3068 result count:"+key1.getCardinality());

		Log.d(TAG,	"Searching:"+bible+" Search term:" + searchText);
		
		// This does a standard operator search. See the search
		// documentation for more examples of how to search
		Key key = bible.find(searchText); //$NON-NLS-1$

		Log.d(TAG,	"There are "+key.getCardinality()+" verses containing " + searchText);

		return key;

	}

	private OsisToHtmlSaxHandler getSaxHandler(Book book, Key key) {
		OsisToHtmlSaxHandler osisToHtml = new OsisToHtmlSaxHandler();
		BookMetaData bmd = book.getBookMetaData();
		osisToHtml.setLeftToRight(bmd.isLeftToRight());
		osisToHtml.setLanguageCode(book.getLanguage().getCode());
		
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			// show verse numbers if user has selected to show verse numbers AND teh book is a bible (so don't even try to show verses in a Dictionary)
			if (BookCategory.BIBLE.equals(book.getBookCategory())) {
				osisToHtml.setShowVerseNumbers(preferences.getBoolean("show_verseno_pref", true) && book.getBookCategory().equals(BookCategory.BIBLE));
				osisToHtml.setVersePerline(preferences.getBoolean("verse_per_line_pref", false));
				osisToHtml.setShowNotes(preferences.getBoolean("show_notes_pref", true));
				osisToHtml.setShowStrongs(preferences.getBoolean("show_strongs_pref", true));
				osisToHtml.setShowMorphology(preferences.getBoolean("show_morphology_pref", false));
			}
			if (preferences.getBoolean("night_mode_pref", false)) {
				osisToHtml.setExtraStylesheet(NIGHT_MODE_STYLESHEET);
			}
			if (book.getBookCategory().equals(BookCategory.DICTIONARY)) {
				if (book.hasFeature(FeatureType.HEBREW_DEFINITIONS)) {
					//add allHebrew refs link
					String prompt = BibleApplication.getApplication().getString(R.string.all_hebrew_occurrences);
					osisToHtml.setExtraFooter("<br /><a href='"+Constants.ALL_HEBREW_OCCURRENCES_PROTOCOL+":"+key.getName()+"' class='allStrongsRefsLink'>"+prompt+"</a>");
				} else if (book.hasFeature(FeatureType.GREEK_DEFINITIONS)) {
					//add allGreek refs link
					String prompt = BibleApplication.getApplication().getString(R.string.all_greek_occurrences);
					osisToHtml.setExtraFooter("<br /><a href='"+Constants.ALL_GREEK_OCCURRENCES_PROTOCOL+":"+key.getName()+"' class='allStrongsRefsLink'>"+prompt+"</a>");
				}
			}

		}
		
		return osisToHtml;
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
		SwordApi.isAndroid = isAndroid;
	}

	private void ensureDirExists(File dir) {
		if (!dir.exists() || !dir.isDirectory()) {
			dir.mkdirs();
		}
	}

	/** needs to be static because otherwise the constructor triggers initialisation
	 * 
	 * @return
	 */
	static public boolean isSwordLoaded() {
		return isSwordLoaded;
	}
}
