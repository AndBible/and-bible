package org.crosswire.jsword.index.lucene;

import java.io.File;
import java.net.URI;

import junit.framework.TestCase;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.sword.SwordBookDriver;
import org.crosswire.jsword.index.IndexStatus;

public class PdaLuceneIndexManagerTest extends TestCase {

	private Book[] books;
	private SwordApi swordApi;

	protected void setUp() throws Exception {
		super.setUp();
		SwordApi.setAndroid(false);
		swordApi = SwordApi.getInstance();
		
        SwordBookDriver swordBookDriver = new SwordBookDriver();
        books = swordBookDriver.getBooks();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testScheduleIndexCreation() {
		try {
			Book book = getBook("ESV");
			PdaLuceneIndexManager fim = new PdaLuceneIndexManager();
			
			// delete existing index
			IndexStatus indexStatus = book.getIndexStatus();
			System.out.println("Index status:"+indexStatus);
			if (indexStatus.equals(IndexStatus.DONE)) {
				System.out.println("must delete index");
				URI storage = new URI("file:/C:/Documents%20and%20Settings/denha1m/Application%20Data/JSword/lucene/Sword/ESV");
				File dir = new File(storage);
				dir.setWritable(true);
				for (File file:dir.listFiles()) {
					file.delete();
				}
				boolean deleted = dir.delete();
				if (!deleted) {
					System.out.println("Failed to delete index");
					
				}
			}

			fim.scheduleIndexCreation(book);

			// must give worker thread time to finish
	        try {
	        	Thread.sleep(60000);
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Book getBook(String initials) {
		for (Book book : books) {
			if (book.getInitials().equals(initials)) {
				System.out.print("Found:"+book.getName());
				return book;
			}
		}
		return null;
	}
}
