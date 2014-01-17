package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.ControlFactory;

import org.crosswire.jsword.book.Book;

import android.support.v4.view.MenuItemCompat;

/** Quick change bible toolbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleActionBarButton extends QuickDocumentChangeToolbarButton {

	public BibleActionBarButton() {
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
	}

	@Override
	Book getSuggestedDocument() {
		return ControlFactory.getInstance().getDocumentControl().getSuggestedBible();
	}
}
