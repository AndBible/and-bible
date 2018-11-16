package net.bible.service.sword;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.crosswire.jsword.book.BookFilter;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class AcceptableBookTypeFilter implements BookFilter {

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.crosswire.jsword.book.BookFilter#test(org.crosswire.jsword.book
     * .Book)
     */
    public boolean test(Book book) {
        BookCategory bookCategory = book.getBookCategory();
        if (book.isLocked()) {
            return false;
        } else {
            return bookCategory.equals(BookCategory.BIBLE) ||
            bookCategory.equals(BookCategory.COMMENTARY) ||
            bookCategory.equals(BookCategory.DICTIONARY) ||
            bookCategory.equals(BookCategory.GENERAL_BOOK) || 
            bookCategory.equals(BookCategory.MAPS);
        }
    }
}
