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
public class IBTRepo extends RepoBase {

	// see here for info ftp://ftp.xiphos.org/mods.d/
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
