package net.bible.service.download;

import java.util.List;

import net.bible.service.sword.AcceptableBookTypeFilter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

/** Modules from the Institute of Bible Translation
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public class IBTRepo extends RepoBase {

	private static final String REPOSITORY = "IBT";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new AcceptableBookTypeFilter();
	
	/** get a list of books that are available in default repo and seem to work in And Bible
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {

		List<Book> books = getBookList(SUPPORTED_DOCUMENTS, refresh);
		storeRepoNameInMetaData(books);
		
		return books;
	}
	
	@Override
	public String getRepoName() {
		return REPOSITORY;
	}
}
