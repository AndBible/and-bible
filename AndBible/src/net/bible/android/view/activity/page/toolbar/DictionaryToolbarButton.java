package net.bible.android.view.activity.page.toolbar;

import net.bible.android.control.ControlFactory;

import org.crosswire.jsword.book.Book;

import android.support.v4.view.MenuItemCompat;

// does not inherit from button - see: http://stackoverflow.com/questions/8369504/why-so-complex-to-set-style-from-code-in-android
public class DictionaryToolbarButton extends QuickDocumentChangeToolbarButton {

	public DictionaryToolbarButton() {
		super(MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
	}

	@Override
	Book getSuggestedDocument() {
		return ControlFactory.getInstance().getDocumentControl().getSuggestedDictionary();
	}
}
