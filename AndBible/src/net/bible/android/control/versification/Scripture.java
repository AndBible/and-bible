package net.bible.android.control.versification;

import java.util.ArrayList;
import java.util.List;

import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

/** 
 * Enable separation of Scripture books 
 * Not complete because dc fragments are sometimes embedded within books like Esther and Daniel 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Scripture {

	private static final Versification SCRIPTURAL_V11N = Versifications.instance().getVersification("KJV");
	
	private final static List<BibleBook> INTROS = new ArrayList<BibleBook>();
	static {
		INTROS.add(BibleBook.INTRO_BIBLE);
		INTROS.add(BibleBook.INTRO_OT);
		INTROS.add(BibleBook.INTRO_NT);
	}
	
	/** TODO: needs to be improved because some books contain extra chapters which are non-scriptural
	 */
	public static boolean isScripture(BibleBook bibleBook) {
		return SCRIPTURAL_V11N.containsBook(bibleBook) && !INTROS.contains(bibleBook);
	}

	public static boolean isIntro(BibleBook bibleBook) {
		return INTROS.contains(bibleBook);
	}
}
