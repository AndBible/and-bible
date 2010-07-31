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

import net.bible.service.format.FormattedDocument;
import net.bible.service.format.OsisToCanonicalTextSaxHandler;
import net.bible.service.format.OsisToHtmlSaxHandler;

import org.crosswire.common.util.CWProject;
import org.crosswire.common.xml.SAXEventProvider;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookData;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.OSISUtil;
import org.crosswire.jsword.book.sword.SwordBookPath;
import org.crosswire.jsword.index.IndexStatus;
import org.crosswire.jsword.index.lucene.PdaLuceneIndexManager;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.Passage;
import org.xml.sax.SAXException;

import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

public class SwordApi {
	private static final String TAG = "SwordApi";
	private static SwordApi singleton;
	private String xslFilePath;
	private List<Book> allDocuments;

	// just keep one of these because it is called in the tight document indexing loop and isn't very complex
	OsisToCanonicalTextSaxHandler osisToCanonicalTextSaxHandler = new OsisToCanonicalTextSaxHandler();

	private SharedPreferences preferences;
	
	private static boolean isAndroid = true;
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
				File sdcard = Environment.getExternalStorageDirectory();
		        CWProject.setHome("jsword.home", sdcard.getPath()+"/jsword", "JSword"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	
				SwordBookPath.setAugmentPath(new File[] {
						new File(sdcard, "jsword")});
				
				log.debug("Getting books");
				allDocuments = Books.installed().getBooks(BookFilters.getAll());
				log.debug("Got books, Num="+allDocuments.size());
				
//				xslFilePath = sdcard+"/jsword/bible.xsl";
//				Log.i(TAG, "Bible xsl:"+xslFilePath);
			}
		} catch (Exception e) {
			log.error("Error initialising", e);
		}
	}

	public List<Book> getDocuments() {
		return allDocuments;
	}

	public Book getDocumentByInitials(String initials) {
		log.debug("Getting book:"+initials);

		return Books.installed().getBook(initials);
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
	public FormattedDocument readHtmlText(Book book, Key key, int maxKeyCount) throws NoSuchKeyException, BookException, IOException, SAXException, URISyntaxException, ParserConfigurationException
	{
		FormattedDocument retVal = new FormattedDocument();
		if (!book.contains(key)) {
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

	private synchronized FormattedDocument readHtmlTextOptimizedZTextOsis(Book book, Key key, int maxKeyCount) throws NoSuchKeyException, BookException, IOException, SAXException, URISyntaxException, ParserConfigurationException
	{
		log.debug("Using fast method to fetch document data");
		InputStream is = new OSISInputStream(book, key);

		OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book);
	
		SAXParserFactory spf = SAXParserFactory.newInstance();
		spf.setValidating(false);
		SAXParser parser = spf.newSAXParser();
		parser.parse(is, osisToHtml);
		
		FormattedDocument retVal = new FormattedDocument();
		retVal.setHtmlPassage(osisToHtml.toString());
		retVal.setNotesList(osisToHtml.getNotesList());
		
        return retVal;
	}

	private FormattedDocument readHtmlTextStandardJSwordMethod(Book book, Key key, int maxKeyCount) throws NoSuchKeyException, BookException, IOException, SAXException, URISyntaxException
	{
		log.debug("Using standard JSword to fetch document data");
		FormattedDocument retVal = new FormattedDocument();

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
    public String getCanonicalText(Book book, Key key, int maxKeyCount) throws NoSuchKeyException, BookException {
		InputStream is = new OSISInputStream(book, key);

		OsisToCanonicalTextSaxHandler osisToCanonical = getCanonicalTextSaxHandler(book);

		try {
			getSAXParser().parse(is, osisToCanonical);
		} catch (Exception e) {
			log.error("SAX parser error", e);
//todo sort MsgBase			throw new BookException("SAX parser error", e);
		}
		
		return osisToCanonical.toString();
    }

    private SAXParser saxParser;
    private SAXParser getSAXParser() {
    	try {
	    	if (saxParser==null) {
	    		SAXParserFactory spf = SAXParserFactory.newInstance();
	    		spf.setValidating(false);
	   			saxParser = spf.newSAXParser();
	    	}
		} catch (Exception e) {
			log.error("SAX parser error", e);
//todo sort MsgBase			throw new BookException("SAX parser error", e);
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
        if (book == null) {
            return ""; //$NON-NLS-1$
        }

        Key key = book.getKey(reference);
        BookData data = new BookData(book, key);
        return OSISUtil.getCanonicalText(data.getOsisFragment());
    }

	public Key search(Book bible, String searchText) throws BookException {
		// This does a standard operator search. See the search
		// documentation
		// for more examples of how to search
		Key key = bible.find(searchText); //$NON-NLS-1$

		Log.i(TAG,	"The following verses contain " + searchText + ": " + key.getName()); //$NON-NLS-1$
		//
		// // You can also trim the result to a more manageable quantity.
		// // The test here is not necessary since we are working with a
		// bible. It
		// // is necessary if we don't know what it
		// // is.
		// if (key instanceof Passage) {
		// Passage remaining = ((Passage) key).trimVerses(5);
		//            System.out.println("The first 5 verses containing both moses and aaron: " + key.getName()); //$NON-NLS-1$
		//            System.out.println("The rest of the verses are: " + remaining.getName()); //$NON-NLS-1$
		// }

		return key;

	}

	private OsisToHtmlSaxHandler getSaxHandler(Book book) {
		OsisToHtmlSaxHandler osisToHtml = new OsisToHtmlSaxHandler();
		BookMetaData bmd = book.getBookMetaData();
		osisToHtml.setLeftToRight(bmd.isLeftToRight());
		
		if (preferences!=null) {
			osisToHtml.setShowVerseNumbers(preferences.getBoolean("show_verseno_pref", true));
			osisToHtml.setShowNotes(preferences.getBoolean("show_notes_pref", true));
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
}
