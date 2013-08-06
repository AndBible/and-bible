package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.book.Book;

import android.view.View;
import android.widget.Button;

public class DictionaryToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private Book mSuggestedDocument;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public DictionaryToolbarButton(View parent) {
        super(parent, R.id.quickDictionaryChange);
	}

	@Override
	protected void onButtonPress() {
    	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedDocument);
	}

	@Override
	public void update() {
        mSuggestedDocument = ControlFactory.getInstance().getDocumentControl().getSuggestedDictionary();
        helper.updateQuickButton(mSuggestedDocument, getButton(), canShow());
	}

	@Override
	public boolean canShow() {
		return isEnoughRoomInToolbar() && mSuggestedDocument!=null &&!isNarrow();
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
