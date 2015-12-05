package net.bible.android.control.download;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.crosswire.common.util.Language;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookFilter;

public class RelevantDocumentLanguageFilter implements BookFilter {
	
	private Set<String> relevantLanguages;

	private static final String[] MAJOR_LANGUAGE_CODES = {"en", "de", "fr", "grc", "he" };

	public RelevantDocumentLanguageFilter(List<Book> installedDocuments) {
		relevantLanguages = new HashSet<>();

		String defaultLanguageCode = Locale.getDefault().getLanguage();
		relevantLanguages.add(defaultLanguageCode);
		relevantLanguages.addAll(Arrays.asList(MAJOR_LANGUAGE_CODES));
		
		for  (Book doc : installedDocuments) {
			Language lang = doc.getLanguage();
			if (lang!=null) {
				relevantLanguages.add(lang.getCode());
			}
		}
	}

	public boolean test(Book document) {
		if (document==null || document.getLanguage()==null) {
			return false;
		}
		
		return relevantLanguages.contains(document.getLanguage().getCode());  
	}
}
