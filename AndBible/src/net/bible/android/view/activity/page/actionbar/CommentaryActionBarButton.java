package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton;

import org.crosswire.jsword.book.Book;

// does not inherit from button - see: http://stackoverflow.com/questions/8369504/why-so-complex-to-set-style-from-code-in-android
public class CommentaryActionBarButton extends QuickDocumentChangeToolbarButton {

	@Override
	protected
	Book getSuggestedDocument() {
		return ControlFactory.getInstance().getDocumentControl().getSuggestedCommentary();
	}
}
