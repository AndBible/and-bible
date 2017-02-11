package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.control.ApplicationScope;

import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/** Quick change bible toolbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class ReadingPlanBibleActionBarButton extends ReadingPlanQuickDocumentChangeButton {

	@Inject
	public ReadingPlanBibleActionBarButton() {
	}

	@Override
	protected Book getSuggestedDocument() {
		return getCurrentPageManager().getCurrentBible().getCurrentDocument();
	}
}
