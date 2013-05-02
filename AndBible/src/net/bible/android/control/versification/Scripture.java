package net.bible.android.control.versification;

import java.util.ArrayList;
import java.util.List;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

/** Enable separation of Scripture books 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Scripture {
	
	/** define Scripture */
	private static final Versification SCRIPTURAL_V11N = Versifications.instance().getVersification("KJV");
	
	private static List<BibleBook> INTROS = new ArrayList<BibleBook>();
	static {
		INTROS.add(BibleBook.INTRO_BIBLE);
		INTROS.add(BibleBook.INTRO_OT);
		INTROS.add(BibleBook.INTRO_NT);
	}
	
	/** TODO: needs to be improved because some books contain extra chapters which are non-scriptural
	 */
	static public boolean isScripture(BibleBook bibleBook) {
		return SCRIPTURAL_V11N.containsBook(bibleBook) && !INTROS.contains(bibleBook);
	}

	/** Get next Scriptural Verse
	 */
	static public Verse getNextVerse(Verse verse) {
		Versification v11n = verse.getVersification();
		BibleBook book = verse.getBook();
		int chapter = verse.getChapter();
		int verseNo = verse.getVerse();
		// if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
		if (verseNo<v11n.getLastVerse(book, chapter)) {
			return new Verse(v11n, book, chapter, verseNo+1);
		} else {
			return getNextChapter(verse);
		}
	}

	/** Get previous Scriptural Verse
	 */
	static public Verse getPrevVerse(Verse verse) {
		Versification v11n = verse.getVersification();
		BibleBook book = verse.getBook();
		int chapter = verse.getChapter();
		int verseNo = verse.getVerse();
		// if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
		if (verseNo>1) {
			verseNo -= 1;
		} else {
			Verse prevChap = getPrevChapter(verse);
			if (!v11n.isSameChapter(verse,  prevChap)) {
				book = prevChap.getBook();
				chapter = prevChap.getChapter();
				verseNo = v11n.getLastVerse(book, chapter);
			}
		}
		return new Verse(v11n, book, chapter, verseNo);
	}

	/** Get next Scriptural chapter
	 */
	static public Verse getNextChapter(Verse verse) {
		Versification v11n = verse.getVersification();
		BibleBook book = verse.getBook();
		int chapter = verse.getChapter();
		// if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
		if (chapter<v11n.getLastChapter(book)) {
			chapter += 1;
		} else {
			BibleBook nextBook = getNextScriptureBook(v11n, book);
			// if there was a next book then go to it's first chapter
			if (nextBook!=null) {
				book = nextBook;
				chapter=1;
			}
		}
		return new Verse(v11n, book, chapter, 1);
	}
	
	/** Get previous Scriptural chapter
	 */
	static public Verse getPrevChapter(Verse verse) {
		Versification v11n = verse.getVersification();
		BibleBook book = verse.getBook();
		int chapter = verse.getChapter();
		// if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
		if (chapter>1) {
			chapter -= 1;
		} else {
			BibleBook prevBook = getPrevScriptureBook(v11n, book);
			// if there was a next book then go to it's first chapter
			if (prevBook!=null) {
				book = prevBook;
				chapter=v11n.getLastChapter(book);
			}
		}
		return new Verse(v11n, book, chapter, 1);
	}

	private static BibleBook getNextScriptureBook(Versification v11n, BibleBook book) {
		BibleBook nextBook = book;
		do {
			nextBook = v11n.getNextBook(nextBook);
		} while (nextBook!=null && !isScripture(nextBook));
		return nextBook;
	}
	private static BibleBook getPrevScriptureBook(Versification v11n, BibleBook book) {
		BibleBook prevBook = book;
		do {
			prevBook = v11n.getPreviousBook(prevBook);
		} while (prevBook!=null && !isScripture(prevBook));
		return prevBook;
	}
}
