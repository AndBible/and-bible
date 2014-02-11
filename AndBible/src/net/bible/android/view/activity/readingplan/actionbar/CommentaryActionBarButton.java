package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.control.ControlFactory;

import org.crosswire.jsword.book.Book;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CommentaryActionBarButton extends ReadingPlanQuickDocumentChangeButton {

	@Override
	protected Book getSuggestedDocument() {
		return ControlFactory.getInstance().getCurrentPageControl().getCurrentCommentary().getCurrentDocument();
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
