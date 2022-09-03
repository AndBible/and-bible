/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.control.navigation;

import java.util.Comparator;
import java.util.regex.Pattern;

import org.crosswire.jsword.internationalisation.LocaleProviderManager;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

/** Compare Bible book names alphabetically 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
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
