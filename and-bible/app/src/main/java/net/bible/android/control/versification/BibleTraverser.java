package net.bible.android.control.versification;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.navigation.DocumentBibleBooks;
import net.bible.android.control.navigation.DocumentBibleBooksFactory;

import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.Versification;

import javax.inject.Inject;

/** 
 * Enable separation of Scripture books 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class BibleTraverser {
	
	private final DocumentBibleBooksFactory documentBibleBooksFactory;

	@Inject
	public BibleTraverser(DocumentBibleBooksFactory documentBibleBooksFactory) {
		this.documentBibleBooksFactory = documentBibleBooksFactory;
	}

	/** Get next Scriptural Verse with same scriptural status
	 */
	public Verse getNextVerse(AbstractPassageBook document, Verse verse) {
		Versification v11n = verse.getVersification();
		BibleBook book = verse.getBook();
		int chapter = verse.getChapter();
		int verseNo = verse.getVerse();
		// if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
		if (verseNo<v11n.getLastVerse(book, chapter)) {
			return new Verse(v11n, book, chapter, verseNo+1);
		} else {
			return getNextChapter(document, verse);
		}
	}

	/** Get previous Verse with same scriptural status
	 */
	public Verse getPrevVerse(AbstractPassageBook document, Verse verse) {
		Versification v11n = verse.getVersification();
		BibleBook book = verse.getBook();
		int chapter = verse.getChapter();
		int verseNo = verse.getVerse();
		if (verseNo>1) {
			verseNo -= 1;
		} else {
			Verse prevChap = getPrevChapter(document, verse);
			if (!v11n.isSameChapter(verse,  prevChap)) {
				book = prevChap.getBook();
				chapter = prevChap.getChapter();
				verseNo = v11n.getLastVerse(book, chapter);
			}
		}
		return new Verse(v11n, book, chapter, verseNo);
	}

	public VerseRange getNextVerseRange(AbstractPassageBook document, VerseRange verseRange) {
		return getNextVerseRange(document, verseRange, true);
	}

	public VerseRange getNextVerseRange(AbstractPassageBook document, VerseRange verseRange, boolean continueToNextChapter) {
		Versification v11n = verseRange.getVersification();
		int verseCount = verseRange.getCardinality();

		// shuffle forward
		Verse start = verseRange.getStart();
		Verse end = verseRange.getEnd();
		int i=0;
		while (i++<verseCount && (continueToNextChapter || !v11n.isEndOfChapter(end))) {
			start = getNextVerse(document, start);
			end = getNextVerse(document, end);
		}

		return new VerseRange(v11n, start, end);
	}

	public VerseRange getPreviousVerseRange(AbstractPassageBook document, VerseRange verseRange) {
		return getPreviousVerseRange(document, verseRange, true);
	}

	public VerseRange getPreviousVerseRange(AbstractPassageBook document, VerseRange verseRange, boolean continueToPreviousChapter) {
		Versification v11n = verseRange.getVersification();
		int verseCount = verseRange.getCardinality();

		// shuffle backward
		Verse start = verseRange.getStart();
		Verse end = verseRange.getEnd();
		int i=0;
		while (i++<verseCount && (continueToPreviousChapter || !v11n.isStartOfChapter(start))) {
			start = getPrevVerse(document, start);
			end = getPrevVerse(document, end);
		}

		return new VerseRange(v11n, start, end);
	}

	/** Get next chapter consistent with current verses scriptural status ie don't hop between book with different scriptural states
	 */
	public Verse getNextChapter(AbstractPassageBook document, Verse verse) {
		Versification v11n = verse.getVersification();
		BibleBook book = verse.getBook();
		int chapter = verse.getChapter();
		// if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
		if (chapter<v11n.getLastChapter(book)) {
			chapter += 1;
		} else {
			BibleBook nextBook = getNextBook(document, v11n, book);
			// if there was a next book then go to it's first chapter
			if (nextBook!=null) {
				book = nextBook;
			}
			else {
				book = BibleBook.GEN;
			}
			chapter=1;

		}
		return new Verse(v11n, book, chapter, 1);
	}
	
	/** Get previous chapter consistent with current verses scriptural status ie don't hop between book with different scriptural states
	 */
	public Verse getPrevChapter(AbstractPassageBook document, Verse verse) {
		Versification v11n = verse.getVersification();
		BibleBook book = verse.getBook();
		int chapter = verse.getChapter();
		// if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
		if (chapter>1) {
			chapter -= 1;
		} else {
			BibleBook prevBook = getPrevBook(document, v11n, book);
			// if there was a next book then go to it's first chapter
			if (prevBook!=null) {
				book = prevBook;
				chapter=v11n.getLastChapter(book);
			}
		}
		return new Verse(v11n, book, chapter, 1);
	}

	/** 
	 * Get next book but separate scripture from other books to prevent unintentional jumping between Scripture and other
	 */
	private BibleBook getNextBook(AbstractPassageBook document, Versification v11n, BibleBook book) {
		boolean isCurrentlyScripture = Scripture.isScripture(book);
		DocumentBibleBooks documentBibleBooks = documentBibleBooksFactory.getDocumentBibleBooksFor(document);   
		BibleBook nextBook = book;
		do {
			nextBook = v11n.getNextBook(nextBook);
		} while (nextBook!=null && 
					(	Scripture.isScripture(nextBook)!=isCurrentlyScripture ||
							Scripture.isIntro(nextBook) ||
						!documentBibleBooks.contains(nextBook)
					)
				);
		return nextBook;
	}
	private BibleBook getPrevBook(AbstractPassageBook document, Versification v11n, BibleBook book) {
		boolean isCurrentlyScripture = Scripture.isScripture(book);
		DocumentBibleBooks documentBibleBooks = documentBibleBooksFactory.getDocumentBibleBooksFor(document);   
		BibleBook prevBook = book;
		do {
			prevBook = v11n.getPreviousBook(prevBook);
		} while (prevBook!=null &&
				(	Scripture.isScripture(prevBook)!=isCurrentlyScripture ||
						Scripture.isIntro(prevBook) ||
					!documentBibleBooks.contains(prevBook)
				)
			);
		return prevBook;
	}
}
