package net.bible.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.bible.service.format.FormattedDocument;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Key;

public class TestUtil {

	private static SwordDocumentFacade swordApi;
	static {
		SwordDocumentFacade.isAndroid = false;
		SwordDocumentFacade.setAndroid(false);
		swordApi = SwordDocumentFacade.getInstance();
	}
	
	public static Book getBook(String initials) {
		return Books.installed().getBook(initials);
	}

    public static String convertStreamToString(InputStream is) throws IOException {
        /*
         * To convert the InputStream to String we use the BufferedReader.readLine()
         * method. We iterate until the BufferedReader return null which means
         * there's no more data to read. Each line will appended to a StringBuilder
         * and returned as String.
         */
        if (is != null) {
            StringBuilder sb = new StringBuilder();
            String line;

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } finally {
                is.close();
            }
            return sb.toString();
        } else {        
            return "";
        }
    }
    
	public static String getHtml(Book book, Key key, int maxVerses) throws Exception {
		FormattedDocument formattedDocument = SwordContentFacade.getInstance().readHtmlText(book, key, 100);
		String html = formattedDocument.getHtmlPassage();
		return html;		
	}
}
