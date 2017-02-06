package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton;

import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/** Quick change bible toolbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class BibleActionBarButton extends QuickDocumentChangeToolbarButton {

	private final DocumentControl documentControl;

	@Inject
	public BibleActionBarButton(DocumentControl documentControl) {
		this.documentControl = documentControl;
	}

	@Override
	protected Book getSuggestedDocument() {
		return documentControl.getSuggestedBible();
	}
}
