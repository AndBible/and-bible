package net.andbible.util;

import java.util.Locale;

import junit.framework.Assert;

import org.crosswire.jsword.JSMsg;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookException;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Key;

public class Test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new Test().testInvalidKey();
		
		Locale.setDefault(Locale.FRENCH);
		String jobName = JSMsg.gettext("Creating index. Processing {0}", "ABC");
		System.out.println(jobName);
		
		Locale.setDefault(Locale.GERMAN);
		String msg = JSMsg.gettext("No entry for '{0}' in {1}.", "ONE", "TWO");
		System.out.println(msg);
		

	}

    public void testInvalidKey() {
        Book book = Books.installed().getBook("Pilgrim");
        if (book != null) {
            Key key = book.getGlobalKeyList();
            try {
                book.getRawText(key);
            } catch (NullPointerException e) {
                Assert.fail("test for bad key should not have thrown an NPE.");
            } catch (BookException e) {
            	System.out.println(e.getMessage());
                Assert.assertEquals("testing for a bad key", "No entry for '' in Pilgrim.", e.getMessage());
            }
        }
    }

}
