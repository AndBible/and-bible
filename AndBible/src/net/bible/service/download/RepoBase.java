package net.bible.service.download;

import java.util.List;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class RepoBase {

	public abstract List<Book> getRepoBooks(boolean refresh) throws InstallException;
	public abstract String getRepoName();
	
	/** get a list of books that are available in Xiphos repo and seem to work in And Bible
	 */
	public List<Book> getBookList(BookFilter bookFilter, boolean refresh) throws InstallException {
		
		DownloadManager crossWireDownloadManager = new DownloadManager();
        List<Book> bookList = crossWireDownloadManager.getDownloadableBooks(bookFilter, getRepoName(), refresh);

		return bookList;		
	}

	public void storeRepoNameInMetaData(List<Book> bookList) {
		for (Book book : bookList) {
        	book.getBookMetaData().putProperty(DownloadManager.REPOSITORY_KEY, getRepoName());
        }
	}
	
	public void downloadDocument(Book document) throws InstallException, BookException  {
		DownloadManager downloadManager = new DownloadManager();
		downloadManager.installBook(getRepoName(), document);
	}

}
