package net.bible.service.download;

import net.bible.service.common.CommonUtils;
import net.bible.service.common.Logger;
import net.bible.service.sword.AcceptableBookTypeFilter;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.BooksEvent;
import org.crosswire.jsword.book.BooksListener;
import org.crosswire.jsword.book.install.InstallException;
import org.crosswire.jsword.book.sword.SwordBook;
import org.crosswire.jsword.book.sword.SwordBookMetaData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** some books need renaming after download due to problems with Xiphos module case
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class XiphosRepo extends RepoBase implements BooksListener {

	// see here for info ftp://ftp.xiphos.org/mods.d/
	private static final String XIPHOS_REPOSITORY = "Xiphos";
	
	private static final String TEST_URL = "http://ftp.xiphos.org/sword/";
	private boolean testedOkay = false;

	private static final String lineSeparator = System.getProperty ( "line.separator" );
	
	private static final String REAL_INITIALS = "RealInitials";

	private static final Logger log = new Logger(XiphosRepo.class.getName()); 

	private static BookFilter SUPPORTED_DOCUMENTS = new XiphosBookFilter();
	
	private static Map<String, String> nameToZipMap = new HashMap<>();
	static {
// gave up with this approach because of problems with deletion... moved and renamed the following 3 archives to AndBible repo
// 1. copy zip from xiphos 2. rename zip to same as Initials 3. open zip and rename module conf to same as initials but lowercase
//		nameToZipMap.put("eBibleTeacherMaps", "ebibleteacher");
//		nameToZipMap.put("EpiphanyMaps", "epiphany-maps");
//		nameToZipMap.put("SmithBibleAtlas", "smithatlas");
	}
	
	private static class XiphosBookFilter extends AcceptableBookTypeFilter {
		private static Set<String> acceptableInitials = new HashSet<>();
		static {
			acceptableInitials.add("Gill");
			acceptableInitials.add("Augustine");
			acceptableInitials.add("FinneySysTheo");
			acceptableInitials.add("HodgeSysTheo");
			acceptableInitials.add("LifeTimes");
			acceptableInitials.add("TrainTwelve");
			acceptableInitials.add("PolBibTysia");
			acceptableInitials.add("ChiPinyin");
			acceptableInitials.add("LuthersWorks");
			acceptableInitials.add("Shaw");
			acceptableInitials.add("StrongsRealGreek");
			acceptableInitials.add("StrongsRealHebrew");
			
//			acceptableInitials.add("Augustin");
//			acceptableInitials.add("Chrysostom");
			acceptableInitials.add("LutherBondageOfTheWill");
			acceptableInitials.add("LutherShortClassics");
			
			// maps
			acceptableInitials.add("ABSMaps");
//			acceptableInitials.add("eBibleTeacherMaps"); // moved to AB repo because of naming inconsistency
//			acceptableInitials.add("EpiphanyMaps"); // moved to AB repo because of naming inconsistency
			acceptableInitials.add("HistMidEast");
//			acceptableInitials.add("SmithBibleAtlas"); // moved to AB repo because of naming inconsistency
			acceptableInitials.add("SonLightFreeMaps");
			acceptableInitials.add("TextbookAtlas");
			acceptableInitials.add("KretzmannMaps");
			
			// acceptableInitials.add("Lineage"); booktype is Images and zip error opening
		}

		@Override
		public boolean test(Book book) {
			return 	super.test(book) && 
					acceptableInitials.contains(book.getInitials());
		}
	}
	
// This shows the greek word in addition to the content of the default Strongs dictionary but some of the Greek characters don't display correctly.  
//		xiphosRepoBookList.add(new XiphosRepoBook("strongsrealgreek", "StrongsRealGreek", "DataPath=./modules/lexdict/rawld4/strongsrealgreek/strongsrealgreek\nModDrv=RawLD4\nLang=en\nFeature=GreekDef\nVersion=1.4-100511\nEncoding=UTF-8\nSourceType=ThML\nDescription=Strongs Real Greek Bible Dictionary\nAbout=Text pulled from Ulrik Petersen's content at http://morphgnt.org/projects/strongs-dictionary. In 1996, Michael Grier produced an e-text of Strong's dictionary. He entered every single letter, and did some proof-reading, but transliterated the Greek. In 2006, Ulrik Petersen took Michael Grier's e-text in the version published by the SWORD project and added Greek in UTF-8 where applicable, while transforming the text to XML."));

	// must only register book listener once or books get given null names
	private static int booksToListenForCount = 0;
	private static boolean isBookListenerAdded = false; 
	
	/** get a list of books that are available in Xiphos repo and seem to work in And Bible
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {
		List<Book> booksInRepo = new ArrayList<>();

		// Xiphos has gone off line a couple of times so specifically test if it is available to avoid hang
		if (testedOkay || CommonUtils.isHttpUrlAvailable(TEST_URL)) {
			// tested access to Xiphos repo so don't need to do it again
			testedOkay = true;
			
			booksInRepo = getBookList(SUPPORTED_DOCUMENTS, refresh);
			storeRepoNameInMetaData(booksInRepo);
		}
		
		return booksInRepo;
	}
	
	@Override
	public void downloadDocument(Book book) throws InstallException, BookException {
		try {
			// all the zip files incorrectly have lower case names and other alterations e.g. 'added '-' so change initials until after download
			String alteredConf = getConfString(book, getZipFileName(book.getInitials()));
			
	        SwordBook alteredBook = FakeSwordBookFactory.createFakeRepoBook(book.getInitials(), alteredConf, XIPHOS_REPOSITORY);
	    	((SwordBookMetaData)alteredBook.getBookMetaData()).setProperty(REAL_INITIALS, book.getInitials());
	
			super.downloadDocument(alteredBook);
		} catch (IOException ioe) {
			throw new InstallException(ioe.getMessage());
		}
	}

	private String getZipFileName(String initials) {
		String zipName = nameToZipMap.get(initials);
		if (zipName==null) {
			zipName = initials.toLowerCase(Locale.ENGLISH);
		}
		return zipName;
	}

	public static String getRealInitials(Book book) {
		String realInitials = (String)book.getProperty(REAL_INITIALS);
		if (realInitials==null) {
			realInitials = book.getInitials();
		}
		return realInitials;
	}

	/** reverse engineer the .conf file properties from a Book
	 */
	private String getConfString(Book book, String initials) {
		StringBuilder bldr = new StringBuilder();
		SwordBookMetaData metaData = (SwordBookMetaData)book.getBookMetaData();

		bldr.append("[").append(initials).append("]\n");
		
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_DATA_PATH));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_MOD_DRV));
		bldr.append(getConfEntry(SwordBookMetaData.KEY_LANG, book.getLanguage().getCode()));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_ENCODING));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_SOURCE_TYPE));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_DESCRIPTION));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_ABOUT));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_GLOBAL_OPTION_FILTER));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_VERSION));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_CATEGORY));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_COMPRESS_TYPE));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_COPYRIGHT));
		bldr.append(getConfEntry(metaData, SwordBookMetaData.KEY_VERSIFICATION));

		return bldr.toString();
	}
	
	/** reverse engineer one .conf file property
	 */
	private String getConfEntry(SwordBookMetaData metaData, String key) {
		return getConfEntry(key, metaData.getProperty(key));
	}
	private String getConfEntry(String property, Object value) {
		StringBuilder buff = new StringBuilder();
		if (value!=null && StringUtils.isNotBlank(value.toString())) {
			String propertyValue = value.toString().replace("\n", "\\"+lineSeparator);
			buff.append(property).append("=").append(propertyValue).append(lineSeparator);
		}
		return buff.toString();
	}
	
	/** true if book is in Xiphos repo
	 */
	public boolean needsPostDownloadAction(Book book) {
		return XIPHOS_REPOSITORY.equals(book.getProperty(DownloadManager.REPOSITORY_KEY));
	}

	/** add a listener to handle module rename after download
	 */
	public void addHandler(Book book) {
		// If you want to know about new books as they arrive:
		if (needsPostDownloadAction(book)) {
			if (!isBookListenerAdded) {
		        isBookListenerAdded = true;
		        Books.installed().addBooksListener(this);
			}
	        booksToListenForCount++;
		}
	}

	/** called after download of book from Xiphos repo completes to rename Module name to be camel case
	 */
	@Override
	public void bookAdded(BooksEvent ev) {
		Book book = ev.getBook();
		log.debug("Book added "+book);
		String realInitials = getRealInitials(book);
		try {
			String conf = getConfString(book, realInitials);
	        BookMetaData bmd = FakeSwordBookFactory.createRepoSBMD(realInitials, conf);
	        // library is set during download, ensure it is maintained in recreated sbmd
	        bmd.setLibrary(book.getBookMetaData().getLibrary());
	        
	        book.setBookMetaData(bmd);
		} catch (Exception e) {
			log.error(e.getMessage());
		}
		
		booksToListenForCount--;
		if (booksToListenForCount==0) {
			isBookListenerAdded = false;
	        Books.installed().removeBooksListener(this);
		}
	}
	
	@Override
	public void bookRemoved(BooksEvent ev) {
		//ignore
	}

	@Override
	public String getRepoName() {
		return XIPHOS_REPOSITORY;
	}
}
