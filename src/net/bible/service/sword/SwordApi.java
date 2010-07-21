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

import net.bible.android.activity.R;
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
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.NoSuchKeyException;
import org.crosswire.jsword.passage.Passage;
import org.xml.sax.HandlerBase;
import org.xml.sax.SAXException;

import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

public class SwordApi {
	private static final String TAG = "SwordApi";
	private static SwordApi singleton;
	private String xslFilePath;
	private List<Book> allDocuments;
	
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
	public String readHtmlText(Book book, Key key, int maxKeyCount) throws NoSuchKeyException, BookException, IOException, SAXException, URISyntaxException, ParserConfigurationException
	{
		String html = "";
		if (!book.contains(key)) {
			html = "";
		} else {
			if ("OSIS".equals(book.getBookMetaData().getProperty("SourceType")) &&
				"zText".equals(book.getBookMetaData().getProperty("ModDrv"))) {
				html = readHtmlTextOptimizedZTextOsis(book, key, maxKeyCount);
			} else {
				html = readHtmlTextStandardJSwordMethod(book, key, maxKeyCount);
			}
		}
		return html;
	}

	private String readHtmlTextOptimizedZTextOsis(Book book, Key key, int maxKeyCount) throws NoSuchKeyException, BookException, IOException, SAXException, URISyntaxException, ParserConfigurationException
	{
		log.debug("Using fast method to fetch document data");
		InputStream is = new OSISInputStream(book, key);

		OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book);
	
		SAXParserFactory spf = SAXParserFactory.newInstance();
		spf.setValidating(false);
		SAXParser parser = spf.newSAXParser();
		parser.parse(is, osisToHtml);
		
		log.debug("notes:"+osisToHtml.getNotes());
		
        return osisToHtml.toString();
	}

	private String readHtmlTextStandardJSwordMethod(Book book, Key key, int maxKeyCount) throws NoSuchKeyException, BookException, IOException, SAXException, URISyntaxException
	{
		log.debug("Using standard JSword to fetch document data");
		BookData data = new BookData(book, key);		
		SAXEventProvider osissep = data.getSAXEventProvider();
		if (osissep == null) {
			Log.e(TAG, "No osis SEP returned");
			return "Error fetching osis"; //$NON-NLS-1$
		}

		OsisToHtmlSaxHandler osisToHtml = getSaxHandler(book);

		osissep.provideSAXEvents(osisToHtml);

		String htmlText = osisToHtml.toString();
        return htmlText;
	}

	private OsisToHtmlSaxHandler getSaxHandler(Book book) {
		OsisToHtmlSaxHandler osisToHtml = new OsisToHtmlSaxHandler();
		BookMetaData bmd = book.getBookMetaData();
		osisToHtml.setLeftToRight(bmd.isLeftToRight());
		
		osisToHtml.setShowVerseNumbers(preferences.getBoolean("show_verseno_pref", true));
		osisToHtml.setShowNotes(preferences.getBoolean("show_notes_pref", true));

		return osisToHtml;
	}
	
// the folowing won't work on anything less than Android 2.2 because it requires xml.transform thro TransformingSAXEventProvider 
//	/**
//	 * Obtain styled text (in this case HTML) for a book reference.
//	 * 
//	 * @param bookInitials
//	 *            the book to use
//	 * @param reference
//	 *            a reference, appropriate for the book, of one or more entries
//	 * @return the styled text
//	 * @see Book
//	 * @see SAXEventProvider
//	 */
//	public String readHtmlText(Book book, Key reference, int maxKeyCount)
//			throws NoSuchKeyException, BookException, TransformerException,
//			IOException, SAXException, URISyntaxException {
//		SAXEventProvider osissep = getOSIS(book, reference.toString(), maxKeyCount);
//		if (osissep == null) {
//			Log.e(TAG, "No osis SEP returned");
//			return ""; //$NON-NLS-1$
//		}
//
//		// AssetManager assetManager = getAssets();
//		// assetManager.list("mart.xsl");
//
//		URI uri = new URI("file://"+xslFilePath);
//		Converter styler = new TransformingSAXEventProviderConverter(uri);
//
//		TransformingSAXEventProvider htmlsep = (TransformingSAXEventProvider) styler
//				.convert(osissep);
//
//		// You can also pass parameters to the XSLT. What you pass depends upon
//		// what the XSLT can use.
//		BookMetaData bmd = book.getBookMetaData();
//		boolean direction = bmd.isLeftToRight();
//		htmlsep.setParameter("direction", direction ? "ltr" : "rtl"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
//
//		// Finally you can get the styled text.
//		return XMLUtil.writeToString(htmlsep);
//	}

//	public String readHtmlText2(Book book, String reference, int maxKeyCount) throws NoSuchKeyException, BookException, IOException, SAXException, URISyntaxException, ParserConfigurationException
//	{
//        Key key = book.getKey(reference);
//
//        ZVerseBackendLite rdr = new ZVerseBackendLite((SwordBookMetaData)book.getBookMetaData());
//        String osisText = rdr.getTextDoc(key);
//		
//		Log.d(TAG, osisText);
//		InputSource is = new InputSource(new StringReader(osisText));
//	
//		OsisToHtmlSaxHandler html = new OsisToHtmlSaxHandler();
//		BookMetaData bmd = book.getBookMetaData();
//		html.setLeftToRight(bmd.isLeftToRight());
//	
//		SAXParserFactory spf = SAXParserFactory.newInstance();
//		spf.setValidating(false);
//		SAXParser parser = spf.newSAXParser();
//		parser.parse(is, html);
//		
//        return html.toString();
//	}
//
//	public String readHtmlText3(Book book, String reference, int maxKeyCount) throws NoSuchKeyException, BookException, IOException, SAXException, URISyntaxException, ParserConfigurationException
//	{
//        Key key = book.getKey(reference);
//
//        ZVerseBackendLite rdr = new ZVerseBackendLite((SwordBookMetaData)book.getBookMetaData());
//        String osisText = rdr.getTextDoc(key);
//		
//		Log.d(TAG, osisText);
//		InputSource is = new InputSource(new StringReader(osisText));
//	
//		OsisToHtmlSaxHandler html = new OsisToHtmlSaxHandler();
//		BookMetaData bmd = book.getBookMetaData();
//		html.setLeftToRight(bmd.isLeftToRight());
//	
//		SAXParserFactory spf = SAXParserFactory.newInstance();
//		spf.setValidating(false);
//		SAXParser parser = spf.newSAXParser();
//		parser.parse(is, html);
//		
//        return html.toString();
//	}

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
//
//	public Document getOSISDom(Book book, String reference, int maxKeyCount)
//			throws BookException, NoSuchKeyException {
//		Key key = null;
//		if (BookCategory.BIBLE.equals(book.getBookCategory())) {
//			key = book.getKey(reference);
//			((Passage) key).trimVerses(maxKeyCount);
//		} else {
//			key = book.createEmptyKeyList();
//
//			Iterator iter = book.getKey(reference).iterator();
//			int count = 0;
//			while (iter.hasNext()) {
//				if (++count >= maxKeyCount) {
//					break;
//				}
//				key.addAll((Key) iter.next());
//			}
//		}
//
//		BookData data = new BookData(book, key);
//		return data.getDOM();
//	}

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

	public Key search(Book bible, String searchText) {
		try {
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
		} catch (Exception e) {
			Log.e(TAG, "search error", e);
			return null;
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
		SwordApi.isAndroid = isAndroid;
	}

	public void setPreferences(SharedPreferences preferences) {
		this.preferences = preferences;
		Log.d(TAG, "Contains versenopref:"+preferences.contains("show_verseno_pref")+" notes pref:"+preferences.contains("show_notes_pref"));
	}
}
