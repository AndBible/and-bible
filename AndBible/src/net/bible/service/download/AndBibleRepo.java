package net.bible.service.download;

import java.util.List;

import net.bible.service.sword.AcceptableBookTypeFilter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

/** some books need renaming after download due to problems with Xiphos module case
 * 
 * @author denha1m
 */
public class AndBibleRepo {

	// see here for info ftp://ftp.xiphos.org/mods.d/
	private static final String REPOSITORY = "AndBible";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new AcceptableBookTypeFilter();
	
	@SuppressWarnings("unused")
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
}
