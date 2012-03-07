package net.bible.service.download;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bible.service.common.Logger;
import net.bible.service.sword.AcceptableBookTypeFilter;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.BookMetaData;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.book.BooksEvent;
import org.crosswire.jsword.book.BooksListener;
import org.crosswire.jsword.book.install.InstallException;
import org.crosswire.jsword.book.sword.ConfigEntryType;
import org.crosswire.jsword.book.sword.SwordBookMetaData;

/** some books need renaming after download due to problems with Xiphos module case
 * 
 * @author denha1m
 */
public class XiphosRepo extends RepoBase implements BooksListener {

	// see here for info ftp://ftp.xiphos.org/mods.d/
	private static final String XIPHOS_REPOSITORY = "Xiphos";

	static final String lineSeparator = System.getProperty ( "line.separator" );
	
	private static final String REAL_INITIALS = "RealInitials";

	private static final Logger log = new Logger(XiphosRepo.class.getName()); 

	private static BookFilter SUPPORTED_DOCUMENTS = new XiphosBookFilter();
	
	private static Map<String, String> nameToZipMap = new HashMap<String, String>();
	static {
		nameToZipMap.put("eBibleTeacherMaps", "ebibleteacher");
		nameToZipMap.put("EpiphanyMaps", "epiphany-maps");
		nameToZipMap.put("SmithBibleAtlas", "smithatlas");
	}
	
	private static class XiphosBookFilter extends AcceptableBookTypeFilter {
		private static Set<String> acceptableInitials = new HashSet<String>();
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
			// maps
			acceptableInitials.add("ABSMaps");
			acceptableInitials.add("eBibleTeacherMaps");
			acceptableInitials.add("EpiphanyMaps");
			acceptableInitials.add("HistMidEast");
			acceptableInitials.add("SmithBibleAtlas");
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
	public List<Book> getXiphosRepoBooks(boolean refresh) throws InstallException {
		List<Book> booksInRepo = getBookList(XIPHOS_REPOSITORY, SUPPORTED_DOCUMENTS, refresh);

		List<Book> bookList = new ArrayList<Book>();
		for (Book repoBook : booksInRepo) {
			try {
				// all the zip files incorrectly have lower case names so set initials to lowercase until after download
				String conf = getConfString(repoBook, getZipFileName(repoBook.getInitials()));
				System.out.println(conf);
		        Book alteredBook = FakeSwordBookFactory.createFakeRepoBook(repoBook.getInitials(), conf, XIPHOS_REPOSITORY);
	        	alteredBook.getBookMetaData().putProperty(REAL_INITIALS, repoBook.getInitials());
		        bookList.add(alteredBook);
			} catch(Exception e) {
				log.error(e.getMessage());
			}
		}
		
		storeRepoNameInMetaData(bookList, XIPHOS_REPOSITORY);
		
		return bookList;		
	}
	
	private String getZipFileName(String initials) {
		String zipName = nameToZipMap.get(initials);
		if (zipName==null) {
			zipName = initials.toLowerCase();
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
		StringBuffer buff = new StringBuffer();
		SwordBookMetaData metaData = (SwordBookMetaData)book.getBookMetaData();
		Map<String, Object> props = metaData.getProperties();

		buff.append("[").append(initials).append("]\n");
		
		buff.append(getConfEntry(props, ConfigEntryType.DATA_PATH));
		buff.append(getConfEntry(props, ConfigEntryType.MOD_DRV));
		buff.append(getConfEntry(ConfigEntryType.LANG.toString(), book.getLanguage().getCode()));
		buff.append(getConfEntry(props, ConfigEntryType.ENCODING));
		buff.append(getConfEntry(props, ConfigEntryType.SOURCE_TYPE));
		buff.append(getConfEntry(props, ConfigEntryType.DESCRIPTION));
		buff.append(getConfEntry(props, ConfigEntryType.ABOUT));
		buff.append(getConfEntry(props, ConfigEntryType.GLOBAL_OPTION_FILTER));
		buff.append(getConfEntry(props, ConfigEntryType.VERSION));
		buff.append(getConfEntry(props, ConfigEntryType.CATEGORY));
		buff.append(getConfEntry(props, ConfigEntryType.COMPRESS_TYPE));
		buff.append(getConfEntry(props, ConfigEntryType.COPYRIGHT));
		buff.append(getConfEntry(props, ConfigEntryType.VERSIFICATION));

		return buff.toString();
	}
	
	/** reverse engineer one .conf file property
	 */
	private String getConfEntry(Map<String, Object> props, Enum<ConfigEntryType> entryType) {
		return getConfEntry(entryType.toString(), props.get(entryType.toString()));
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
}
