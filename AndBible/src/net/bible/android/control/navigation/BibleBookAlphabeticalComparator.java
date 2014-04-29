package net.bible.android.control.navigation;

import java.util.Comparator;
import java.util.regex.Pattern;

import org.crosswire.jsword.internationalisation.LocaleProviderManager;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

/** Compare Bible book names alphabetically 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleBookAlphabeticalComparator implements Comparator<BibleBook> {
	
	private Versification versification;
	
	private static final Pattern NUMBERS_PATTERN = Pattern.compile("[0-9]");
	private static final Pattern NOT_NUMBERS_PATTERN = Pattern.compile("[^0-9]");

	public BibleBookAlphabeticalComparator(Versification versification) {
		this.versification = versification;
	}

	public int compare(BibleBook bibleBook1, BibleBook bibleBook2) {
		return getSortableBoookName(bibleBook1).compareTo(getSortableBoookName(bibleBook2));
	}
	
	private String getSortableBoookName(BibleBook bibleBook) {
		String name = versification.getShortName(bibleBook).toLowerCase(LocaleProviderManager.getLocale());
		// get the character name at the start eg '1 cor' -> 'cor1' so that books with a number at the start do not float to the top
		String bookName = NUMBERS_PATTERN.matcher(name).replaceAll("");
		String bookNumbers = NOT_NUMBERS_PATTERN.matcher(name).replaceAll("");
		return bookName+bookNumbers;
	}
};
