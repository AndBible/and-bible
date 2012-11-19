package net.bible.android.view.activity.readingplan.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonHelper;

import org.crosswire.jsword.book.Book;

import android.view.View;

public class ShowDictionaryToolbarButton extends ShowDocumentToolbarButton implements ToolbarButton {

	public ShowDictionaryToolbarButton(View parent) {
        super(parent, R.id.quickDictionaryChange);
	}

	@Override
	public Book getDocument() {
		return ControlFactory.getInstance().getCurrentPageControl().getCurrentDictionary().getCurrentDocument();
	}

	@Override
	public boolean canShow() {
		return super.canShow() && !isNarrow();
	}
	
	
}
