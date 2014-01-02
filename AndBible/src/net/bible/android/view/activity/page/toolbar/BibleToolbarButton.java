package net.bible.android.view.activity.page.toolbar;

import net.bible.android.control.ControlFactory;

import org.crosswire.jsword.book.Book;

/** Quick change bible toolbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleToolbarButton extends QuickDocumentChangeToolbarButton {

	@Override
	Book getSuggestedDocument() {
		return ControlFactory.getInstance().getDocumentControl().getSuggestedBible();
	}
}
