package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.Book;

public class DictionaryActionBarButton extends ReadingPlanQuickDocumentChangeButton {

	@Override
	protected
	Book getSuggestedDocument() {
		return ControlFactory.getInstance().getCurrentPageControl().getCurrentDictionary().getCurrentDocument();
	}
	
	/** return true if Strongs are relevant to this doc & screen */
	@Override
	protected boolean canShow() {
		return super.canShow() && 
				isWide(); 
	}
}
