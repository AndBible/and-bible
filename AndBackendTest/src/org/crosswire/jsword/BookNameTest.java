package org.crosswire.jsword;

import java.util.Locale;

import org.crosswire.jsword.versification.BibleBook;

import junit.framework.TestCase;

public class BookNameTest extends TestCase {

    public void testMatch() {
        Locale.setDefault(Locale.GERMAN);
        System.out.println(BibleBook.getBook("Mr"));
    }
}
