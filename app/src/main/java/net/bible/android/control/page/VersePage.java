package net.bible.android.control.page;

import android.util.Log;

import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.versification.BibleTraverser;
import net.bible.service.sword.SwordContentFacade;
import net.bible.service.sword.SwordDocumentFacade;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.basic.AbstractPassageBook;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;


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
    
    protected VersePage(boolean shareKeyBetweenDocs, CurrentBibleVerse currentVerse, BibleTraverser bibleTraverser, SwordContentFacade swordContentFacade, SwordDocumentFacade swordDocumentFacade) {
        super(shareKeyBetweenDocs, swordContentFacade, swordDocumentFacade);
        // share the verse holder between the CurrentBiblePage & CurrentCommentaryPage
        this.currentBibleVerse = currentVerse;
        this.bibleTraverser = bibleTraverser;
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
    
    /** notify mediator that a detail - normally just verse no - has changed and the title need to update itself
     */
    protected void onVerseChange() {
        if (!isInhibitChangeNotifications()) {
            PassageChangeMediator.getInstance().onCurrentVerseChanged();
        }
    }
}
