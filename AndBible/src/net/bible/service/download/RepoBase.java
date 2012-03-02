package net.bible.service.download;

import java.util.List;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

public class RepoBase {

	/** get a list of books that are available in Xiphos repo and seem to work in And Bible
	 */
	public List<Book> getBookList(String repoName, BookFilter bookFilter, boolean refresh) throws InstallException {
		
		DownloadManager crossWireDownloadManager = new DownloadManager();
        List<Book> bookList = crossWireDownloadManager.getDownloadableBooks(bookFilter, repoName, refresh);

		return bookList;		
	}

	public void storeRepoNameInMetaData(List<Book> bookList, String repoName) {
		for (Book book : bookList) {
        	book.getBookMetaData().putProperty(DownloadManager.REPOSITORY_KEY, repoName);
        }
	}
}
