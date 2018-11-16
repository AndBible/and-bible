package net.bible.service.download;

import java.util.List;

import net.bible.service.sword.AcceptableBookTypeFilter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;
import org.crosswire.jsword.book.install.InstallException;

/** Only specific books are taken from the Beta repo
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public class BetaRepo extends RepoBase {

	private static final String BETA_REPOSITORY = "Crosswire Beta";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new BetaBookFilter();
	
	/** get a list of good books that are available in Beta repo and seem to work in And Bible
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {

		List<Book> books = getBookList(SUPPORTED_DOCUMENTS, refresh);
		storeRepoNameInMetaData(books);
		
		return books;
	}
	
	private static class BetaBookFilter extends AcceptableBookTypeFilter {

		@Override
		public boolean test(Book book) {
			// just Calvin Commentaries for now to see how we go
			//
			// Cannot include Jasher, Jub, EEnochCharles because they are displayed as page per verse for some reason which looks awful.
			return super.test(book) && 
					(book.getInitials().equals("CalvinCommentaries"));
		}
	}

	@Override
	public String getRepoName() {
		return BETA_REPOSITORY;
	}
}
