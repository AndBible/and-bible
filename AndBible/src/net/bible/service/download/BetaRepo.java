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
public class BetaRepo extends RepoBase {

	// see here for info ftp://ftp.xiphos.org/mods.d/
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
			//	Japanese module "JapKougo" works fine and looks complete. 
			//	"JapBungo" has the complete New Testament and looks fine character-wise. 
			//	The "JapDenmo" module only displayed the new testament and Acts, but after a while I got "Error getting bible text: Parsing error"
			// http://code.google.com/p/and-bible/issues/detail?id=62
			// Cannot include Jasher, Jub, EEnochCharles because they are displayed as page per verse for some reason which looks awful.
			return super.test(book) && 
					(	book.getInitials().equals("CalvinCommentaries") ||
						book.getInitials().equals("RecVer") ||
						( book.getInitials().startsWith("Jap") && !book.getInitials().equals("JapDenmo") ) 
//						|| 	book.getInitials().startsWith("Strongs")
					);
		}
	}

	@Override
	public String getRepoName() {
		return BETA_REPOSITORY;
	}
}
