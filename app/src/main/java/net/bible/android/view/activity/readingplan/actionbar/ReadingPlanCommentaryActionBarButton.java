package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.control.ApplicationScope;

import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class ReadingPlanCommentaryActionBarButton extends ReadingPlanQuickDocumentChangeButton {

	@Inject
	public ReadingPlanCommentaryActionBarButton() {
	}


	@Override
	protected Book getSuggestedDocument() {
		return getCurrentPageManager().getCurrentCommentary().getCurrentDocument();
	}

	/**
	 * Portrait actionbar is a bit squashed if speak controls are displayed so hide commentary 
	 */
	@Override
	protected boolean canShow() {
		return super.canShow() &&
				(isWide() || !isSpeakMode());
	}
	
	
}
