package net.bible.service.sword;

import java.util.List;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilters;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.bridge.BookInstaller;

import junit.framework.TestCase;

public class PersonalCommentaryTest extends TestCase {

	public void testDownloadPersonalCommentary() throws Exception {
		SwordDocumentFacade.setAndroid(false);
		
    	List<Book> bibles = new BookInstaller().getRepositoryBooks("CrossWire", BookFilters.getCustom("Initials=Personal"));
    	assertTrue("Could not get bible list", bibles.size()>0);
		System.out.println("Count:"+bibles.size());
		for (Book book : bibles) {
			System.out.println(book.getLanguage()+" init:"+book.getInitials()+" "+book.getBookMetaData().getProperty("SourceType")+book.getBookMetaData().getProperty("ModDrv")+" "+book.getDriverName()+" "+book.getBookMetaData().getKeyType()+" "+book.getName());
		}
		
		Book pc = bibles.get(0);
//		
//		SwordDocumentFacade.getInstance().downloadDocument(pc);
	}
	
	public void testWrite() {
		Book pc = Books.installed().getBook("Personal");
		assertTrue("PC is not writable", pc.isWritable());
	}
}
