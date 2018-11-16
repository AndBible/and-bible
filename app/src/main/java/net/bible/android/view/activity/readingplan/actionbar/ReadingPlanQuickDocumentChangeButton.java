package net.bible.android.view.activity.readingplan.actionbar;

import android.view.MenuItem;

import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class ReadingPlanQuickDocumentChangeButton extends QuickDocumentChangeToolbarButton {

	@Override
	public boolean onMenuItemClick(MenuItem arg0) {
		boolean isHandled = super.onMenuItemClick(arg0);
    	// exit the Daily Reading page, returning up to the Document page display to see the bible
    	CurrentActivityHolder.getInstance().getCurrentActivity().finish();
    	
    	return isHandled;
    }
}
