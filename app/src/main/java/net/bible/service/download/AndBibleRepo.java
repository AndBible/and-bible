package net.bible.service.download;

import net.bible.service.common.Logger;
import net.bible.service.sword.AcceptableBookTypeFilter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

import java.util.List;

/** AndBible repo is mainly used to override modules with temporary issues.  It also contains a few tweaked modules from the Xiphos repo.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class AndBibleRepo extends RepoBase {

	private static final String REPOSITORY = "AndBible";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new AcceptableBookTypeFilter();

	private Logger log = new Logger(this.getClass().getName());

	/** get a list of books that are available in AndBible repo
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {
		
        List<Book> bookList = getBookList(SUPPORTED_DOCUMENTS, refresh);

        storeRepoNameInMetaData(bookList);
        
		return bookList;		
	}

	/**
	 * Download the index of the specified document
	 */
	public void downloadIndex(Book document) throws InstallException, BookException {
		DownloadManager downloadManager = new DownloadManager();
		downloadManager.installIndex(getRepoName(), document);
	}

	@Override
	public String getRepoName() {
		return REPOSITORY;
	}
}
