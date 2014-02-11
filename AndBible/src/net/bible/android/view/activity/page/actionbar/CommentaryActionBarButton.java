package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton;

import org.crosswire.jsword.book.Book;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CommentaryActionBarButton extends QuickDocumentChangeToolbarButton {

	@Override
	protected
	Book getSuggestedDocument() {
		return ControlFactory.getInstance().getDocumentControl().getSuggestedCommentary();
	}

	/**
	 * Not important enough to show if limited space
	 * (non-Javadoc)
	 * @see net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton#canShow()
	 */
	@Override
	protected boolean canShow() {
		return super.canShow() &&
				(isWide() || !isSpeakMode());
	}
}
