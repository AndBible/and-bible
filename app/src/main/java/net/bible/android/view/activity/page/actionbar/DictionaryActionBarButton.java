package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton;

import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class DictionaryActionBarButton extends QuickDocumentChangeToolbarButton {

    private final DocumentControl documentControl;

    @Inject
    public DictionaryActionBarButton(DocumentControl documentControl) {
        this.documentControl = documentControl;
    }

    @Override
    protected
    Book getSuggestedDocument() {
        return documentControl.getSuggestedDictionary();
    }

    /**
     * Not important enough to show if limited space
     * (non-Javadoc)
     * @see net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton#canShow()
     */
    @Override
    protected boolean canShow() {
        return super.canShow() &&
                isWide();
    }
}
