package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.control.ControlFactory;

import org.crosswire.jsword.book.Book;

public class CommentaryActionBarButton extends ReadingPlanQuickDocumentChangeButton {

	@Override
	protected Book getSuggestedDocument() {
		return ControlFactory.getInstance().getCurrentPageControl().getCurrentCommentary().getCurrentDocument();
	}
}
