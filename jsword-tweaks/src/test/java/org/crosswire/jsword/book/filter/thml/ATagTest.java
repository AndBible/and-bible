package org.crosswire.jsword.book.filter.thml;

import java.util.List;

import junit.framework.TestCase;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

public class ATagTest extends TestCase {

    private ATag atag = new ATag();
    
    public void testReference() {
        try {
            THMLFilter thmlFilter = new THMLFilter();
            Book dummyBook = Books.installed().getBook("KJV");
            List<Content> out = thmlFilter.toOSIS(dummyBook, dummyBook.getKey("Gen.1.1"), "<a href=\"sword://StrongsRealGreek/01909\">1909</a>");
            assertEquals("<reference osisRef=\"sword://StrongsRealGreek/01909\">1909</reference>", new XMLOutputter().outputString((Element)out.get(0)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
