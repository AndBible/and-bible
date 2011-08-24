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
public class BetaRepo {

	// see here for info ftp://ftp.xiphos.org/mods.d/
	private static final String BETA_REPOSITORY = "Crosswire Beta";
	
	private static BookFilter SUPPORTED_DOCUMENTS = new BetaBookFilter();
	
	/** get a list of books that are available in Xiphos repo and seem to work in And Bible
	 */
	public List<Book> getRepoBooks(boolean refresh) throws InstallException {
		
		DownloadManager crossWireDownloadManager = new DownloadManager();
        List<Book> bookList = crossWireDownloadManager.getDownloadableBooks(SUPPORTED_DOCUMENTS, BETA_REPOSITORY, refresh);

        for (Book book : bookList) {
        	book.getBookMetaData().putProperty(DownloadManager.REPOSITORY_KEY, BETA_REPOSITORY);
        }
        
		return bookList;		
	}
	
	private static class BetaBookFilter extends AcceptableBookTypeFilter {

		@Override
		public boolean test(Book book) {
			// just Calvin Commentaries for now to see how we go
			return super.test(book) && book.getInitials().contains("CalvinCommentaries");
		}
		
		
	}
}
