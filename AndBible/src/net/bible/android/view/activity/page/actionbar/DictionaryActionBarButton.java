package net.bible.android.view.activity.page.actionbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton;

import org.crosswire.jsword.book.Book;

public class DictionaryActionBarButton extends QuickDocumentChangeToolbarButton {

	@Override
	protected
	Book getSuggestedDocument() {
		return ControlFactory.getInstance().getDocumentControl().getSuggestedDictionary();
	}

	/**
	 * Not important enough to show if limited space
	 * (non-Javadoc)
	 * @see net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton#canShow()
	 */
	@Override
	protected boolean canShow() {
		return isWide();
	}
}
