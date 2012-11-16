package net.bible.android.view.activity.readingplan.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;

import org.crosswire.jsword.book.Book;

import android.view.View;

public class ShowBibleToolbarButton extends ShowDocumentToolbarButton implements ToolbarButton {

	public ShowBibleToolbarButton(View parent) {
        super(parent, R.id.quickBibleChange);
	}

	@Override
	public Book getDocument() {
		return ControlFactory.getInstance().getCurrentPageControl().getCurrentBible().getCurrentDocument();
	}
}
