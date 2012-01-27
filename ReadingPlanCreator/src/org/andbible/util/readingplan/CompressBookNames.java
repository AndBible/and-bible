package org.andbible.util.readingplan;

import java.util.EnumSet;

import org.crosswire.jsword.versification.BibleBook;

public class CompressBookNames {

	public String filter(String in) {
		
		in = in.replace("Psalm ", "Psa ");
		in = in.replace("Revelation", "Rev");

		for (BibleBook book: EnumSet.range(BibleBook.GEN, BibleBook.REV)) {
    		try {
				String longName = book.getLongName();
				String shortName = book.getShortName();
				in = in.replace(longName, shortName);
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
    	}

		return in;
	}
}
