package net.bible.android.view.activity.readingplan.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;

import org.crosswire.jsword.book.Book;

import android.view.View;

public class ShowCommentaryToolbarButton extends ShowDocumentToolbarButton implements ToolbarButton {

	public ShowCommentaryToolbarButton(View parent) {
        super(parent, R.id.quickCommentaryChange);
	}

	@Override
	public Book getDocument() {
		return ControlFactory.getInstance().getCurrentPageControl().getCurrentCommentary().getCurrentDocument();
	}
}
