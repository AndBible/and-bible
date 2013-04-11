package net.bible.android.control.versification;

import java.util.ArrayList;
import java.util.List;

import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

public class Scripture {

	private static final Versification KJV = Versifications.instance().getVersification("KJV");
	
	private static List<BibleBook> INTROS = new ArrayList<BibleBook>();
	static {
		INTROS.add(BibleBook.INTRO_BIBLE);
		INTROS.add(BibleBook.INTRO_OT);
		INTROS.add(BibleBook.INTRO_NT);
	}
	
	static public boolean isScripture(BibleBook bibleBook) {
		return KJV.containsBook(bibleBook) && !INTROS.contains(bibleBook);
	}

}
