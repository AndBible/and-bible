package net.bible.service.download;

import java.util.List;

import net.bible.service.sword.AcceptableBookTypeFilter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

/** some books need renaming after download due to problems with Xiphos module case
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class AndBibleRepo extends RepoBase {

	// see here for info ftp://ftp.xiphos.org/mods.d/
	private static final String REPOSITORY = "AndBible";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new AcceptableBookTypeFilter();
	
	@SuppressWarnings("unused")
	private static final String TAG = "AndBibleRepo";
	
	
	/** get a list of books that are available in AndBible repo
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {
		
        List<Book> bookList = getBookList(SUPPORTED_DOCUMENTS, refresh);

        storeRepoNameInMetaData(bookList);
        
		return bookList;		
	}

	@Override
	public String getRepoName() {
		return REPOSITORY;
	}
}
