package net.bible.service.download;

import java.util.ArrayList;
import java.util.List;

import net.bible.service.sword.AcceptableBookTypeFilter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

import android.util.Log;

/** some books need renaming after download due to problems with Xiphos module case
 * 
 * @author denha1m
 */
public class AndBibleRepo {

	// see here for info ftp://ftp.xiphos.org/mods.d/
	private static final String REPOSITORY = "AndBible";
	
	private static final String BOOK_CONF = "[JESermons]\nDataPath=./modules/genbook/rawgenbook/jesermons/jesermons\nModDrv=RawGenBook\nSourceType=OSIS\nLang=en\nVersion=1.0\nDescription=Jonathan Edwards Sermons\nAbout=Jonathan Edwards Sermons\nDistributionLicense=Public Domain\nTextSource=CCEL\nEncoding=UTF-8";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new AcceptableBookTypeFilter();
	
	private static final String TAG = "AndBibleRepo";
	
	
	/** get a list of books that are available in AndBible repo
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {
		
		DownloadManager downloadManager = new DownloadManager();
        List<Book> bookList = downloadManager.getDownloadableBooks(SUPPORTED_DOCUMENTS, REPOSITORY, refresh);

        for (Book book : bookList) {
        	book.getBookMetaData().putProperty(DownloadManager.REPOSITORY_KEY, REPOSITORY);
        }
        
		return bookList;		
	}

//	/** get a list of books that are available in Xiphos repo and seem to work in And Bible
//	 */
//	public List<Book> getRepoBooks(boolean refresh) throws InstallException {
//	
//        List<Book> bookList = new ArrayList<Book>(); 
//
//		try {
//	        Book repoBook = FakeSwordBookFactory.createFakeRepoBook("JESermons", BOOK_CONF, REPOSITORY);
//	        bookList.add(repoBook);
//		} catch (Exception e) {
//			Log.e(TAG, e.getMessage());
//		}
//        
//		return bookList;		
//	}
}
