package net.bible.android.view.activity.page.actionbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.actionbar.QuickDocumentChangeToolbarButton;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.Book;

// does not inherit from button - see: http://stackoverflow.com/questions/8369504/why-so-complex-to-set-style-from-code-in-android
public class DictionaryActionBarButton extends QuickDocumentChangeToolbarButton {

	@Override
	protected
	Book getSuggestedDocument() {
		return ControlFactory.getInstance().getDocumentControl().getSuggestedDictionary();
	}
	
	/** return true if Strongs are relevant to this doc & screen */
	@Override
	protected boolean canShow() {
		return 4<CommonUtils.getResourceInteger(R.integer.number_of_quick_buttons); 
	}
}
