package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.control.ControlFactory;

import org.crosswire.jsword.book.Book;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DictionaryActionBarButton extends ReadingPlanQuickDocumentChangeButton {

	@Override
	protected
	Book getSuggestedDocument() {
		return ControlFactory.getInstance().getCurrentPageControl().getCurrentDictionary().getCurrentDocument();
	}
	
	/** return true if Strongs are relevant to this doc & screen */
	@Override
	protected boolean canShow() {
		return super.canShow() && 
				isWide(); 
	}
}
