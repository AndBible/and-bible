package net.bible.service.sword;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.bible.android.SharedConstants;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.ParseException;
import net.bible.service.download.DownloadManager;
import net.bible.service.format.FormattedDocument;
import net.bible.service.format.OsisToCanonicalTextSaxHandler;
import net.bible.service.format.OsisToHtmlSaxHandler;

import org.crosswire.common.util.CWProject;
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
	
	private static BookFilter SUPPORTED_DOCUMENT_TYPES = BookFilters.either(BookFilters.either(BookFilters.getBibles(), BookFilters.getCommentaries()), BookFilters.getDictionaries());
	private SharedPreferences preferences;
	
	private static boolean isSwordLoaded;
	
	private static boolean isAndroid = CommonUtils.isAndroid();
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

	public Book getDocumentByInitials(String initials) {
		log.debug("Getting book:"+initials);

		return Books.installed().getBook(initials);
	}
	
	public List<Book> getDownloadableDocuments(boolean refresh) throws InstallException {
		log.debug("Getting downloadable documents");
		
		DownloadManager downloadManager = new DownloadManager();
		
		// currently we just handle bibles, commentaries, or dictionaries
        return downloadManager.getDownloadableBooks(SUPPORTED_DOCUMENT_TYPES, CROSSWIRE_REPOSITORY, refresh);
	}

	public void downloadDocument(Book document) throws InstallException, BookException {
		DownloadManager downloadManager = new DownloadManager();
		downloadManager.installBook(CROSSWIRE_REPOSITORY, document);
	}

	public boolean isIndexDownloadAvailable(Book document) throws InstallException, BookException {
		// not sure how to integrate reuse this in JSword index download
        String version = (String)document.getBookMetaData().getProperty("Version");
        String versionSuffix = version!=null ? "-"+version : "";

		String url = "http://www.crosswire.org/and-bible/indices/v1/"+document.getInitials()+versionSuffix+".zip";
		return CommonUtils.isHttpUrlAvailable(url);
	}
	public void downloadIndex(Book document) throws InstallException, BookException {
		DownloadManager downloadManager = new DownloadManager();
		downloadManager.installIndex(CROSSWIRE_REPOSITORY, document);
	}
	
	public void deleteDocument(Book document) throws BookException {
        document.getDriver().delete(document);

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
	public FormattedDocument readHtmlText(Book book, Key key, int maxKeyCount) throws ParseException
	{
		FormattedDocument retVal = new FormattedDocument();
		if (book==null || key==null) {
			//TODO this should include css to change to night mode if necessary
			retVal.setHtmlPassage("");
		} else if (!book.contains(key)) {
			//TODO this should include css to change to night mode if necessary
			retVal.setHtmlPassage("Not found in document");
		} else {
			if ("OSIS".equals(book.getBookMetaData().getProperty("SourceType")) &&
				"zText".equals(book.getBookMetaData().getProperty("ModDrv"))) {
				retVal = readHtmlTextOptimizedZTextOsis(book, key, maxKeyCount);
			} else {
				retVal = readHtmlTextStandardJSwordMethod(book, key, maxKeyCount);
			}
		}
		return retVal;
	}

	private synchronized FormattedDocument readHtmlTextOptimizedZTextOsis(Book book, Key key, int maxKeyCount) throws ParseException
	{
		log.debug("Using fast method to fetch document data");
		InputStream is = new OSISInputStream(book, key);

		OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book);
	
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

	private synchronized FormattedDocument readHtmlTextStandardJSwordMethod(Book book, Key key, int maxKeyCount) throws ParseException
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
				OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book);
		
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

			Iterator iter = book.getKey(reference).iterator();
			int count = 0;
			while (iter.hasNext()) {
				if (++count >= maxKeyCount) {
					break;
				}
				key.addAll((Key) iter.next());
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
		InputStream is = new OSISInputStream(book, key);

		//TODO can we use same technique as SwordApi.getPlainText()
		OsisToCanonicalTextSaxHandler osisToCanonical = getCanonicalTextSaxHandler(book);

		try {
			getSAXParser().parse(is, osisToCanonical);
		} catch (Exception e) {
			log.error("SAX parser error", e);
			throw new ParseException("SAX parser error", e);
		}
		
		return osisToCanonical.toString();
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

		
		// This does a standard operator search. See the search
		// documentation for more examples of how to search
		Key key = bible.find(searchText); //$NON-NLS-1$

		Log.d(TAG,	"There are "+key.getCardinality()+" verses containing " + searchText); //$NON-NLS-1$

		return key;

	}

	private OsisToHtmlSaxHandler getSaxHandler(Book book) {
		OsisToHtmlSaxHandler osisToHtml = new OsisToHtmlSaxHandler();
		BookMetaData bmd = book.getBookMetaData();
		osisToHtml.setLeftToRight(bmd.isLeftToRight());
		osisToHtml.setLanguageCode(book.getLanguage().getCode());
		
		if (preferences!=null) {
			// show verse numbers if user has selected to show verse numbers AND teh book is a bible (so don't even try to show verses in a Dictionary)
			osisToHtml.setShowVerseNumbers(preferences.getBoolean("show_verseno_pref", true) && book.getBookCategory().equals(BookCategory.BIBLE));
			osisToHtml.setShowNotes(preferences.getBoolean("show_notes_pref", true));
			osisToHtml.setShowStrongs(preferences.getBoolean("show_strongs_pref", true));
			if (preferences.getBoolean("night_mode_pref", false)) {
				osisToHtml.setExtraStylesheet(NIGHT_MODE_STYLESHEET);
			}
		}
		
		return osisToHtml;
	}
	
	private OsisToCanonicalTextSaxHandler getCanonicalTextSaxHandler(Book book) {
		
		return osisToCanonicalTextSaxHandler;
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

	public void setPreferences(SharedPreferences preferences) {
		this.preferences = preferences;
		Log.d(TAG, "Contains versenopref:"+preferences.contains("show_verseno_pref")+" notes pref:"+preferences.contains("show_notes_pref"));
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
