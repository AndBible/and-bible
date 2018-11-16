package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;

import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class ReadingPlanDictionaryActionBarButton extends ReadingPlanQuickDocumentChangeButton {

	@Inject
	public ReadingPlanDictionaryActionBarButton(ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
	}

	@Override
	protected
	Book getSuggestedDocument() {
		return getCurrentPageManager().getCurrentDictionary().getCurrentDocument();
	}
	
	/** return true if Strongs are relevant to this doc & screen */
	@Override
	protected boolean canShow() {
		return super.canShow() && 
				isWide(); 
	}
}
