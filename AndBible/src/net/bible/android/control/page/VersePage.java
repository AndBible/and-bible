package net.bible.android.control.page;

import net.bible.android.control.versification.BibleTraverser;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

import android.util.Log;


/** Common functionality for Bible and commentary document page types
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class VersePage extends CurrentPageBase {

	private CurrentBibleVerse currentBibleVerse;
	
	private BibleTraverser bibleTraverser;

	private static final String TAG = "CurrentPageBase";
	
	protected VersePage(boolean shareKeyBetweenDocs, CurrentBibleVerse currentVerse) {
		super(shareKeyBetweenDocs);
		// share the verse holder between the CurrentBiblePage & CurrentCommentaryPage
		this.currentBibleVerse = currentVerse;
	}

	public Versification getVersification() {
		try {
			// Bibles must be a PassageBook
			return ((AbstractPassageBook)getCurrentDocument()).getVersification();
		} catch (Exception e) {
			Log.e(TAG, "Error getting versification for Book", e);
			return Versifications.instance().getVersification("KJV");
		}
	}

	public AbstractPassageBook getCurrentPassageBook() {
		return (AbstractPassageBook)getCurrentDocument();
	}
	
	protected CurrentBibleVerse getCurrentBibleVerse() {
		return currentBibleVerse;
	}
	
	public void setBibleTraverser(BibleTraverser bibleTraverser) {
		this.bibleTraverser = bibleTraverser;
	}

	protected BibleTraverser getBibleTraverser() {
		return bibleTraverser;
	}

	@Override
	protected void localSetCurrentDocument(Book doc) {
		// update current verse possibly remapped to v11n of new bible 
		Versification newDocVersification = ((AbstractPassageBook)getCurrentDocument()).getVersification();
		Verse newVerse = getCurrentBibleVerse().getVerseSelected(newDocVersification);

		super.localSetCurrentDocument(doc);

		doSetKey(newVerse);
	}
}
