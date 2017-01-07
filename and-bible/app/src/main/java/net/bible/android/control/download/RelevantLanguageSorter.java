package net.bible.android.control.download;

import org.apache.commons.lang3.ObjectUtils;
import org.crosswire.common.util.Language;
import org.crosswire.jsword.book.Book;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RelevantLanguageSorter implements Comparator<Language> {
	
	private Set<String> relevantLanguages;

	private static final String[] MAJOR_LANGUAGE_CODES = {"en", "de", "fr", "grc", "he", "ru", "ar", "zh", "pt" };

	public RelevantLanguageSorter(List<Book> installedDocuments) {
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

	/**
	 * Compare languages, most popular first.
	 * 
	 * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second.
	 */
	@Override
	public int compare(Language lhs, Language rhs) {
		boolean lhsRelevant = isRelevant(lhs);
		boolean rhsRelevant = isRelevant(rhs);
		if (lhsRelevant != rhsRelevant) {
			return lhsRelevant? -1 : 1;
		} else {
			return ObjectUtils.compare(lhs, rhs);
		}
	}
	
	private boolean isRelevant(Language lang) {
		if (lang==null) {
			return false;
		}
		
		return relevantLanguages.contains(lang.getCode());  
	}
}
