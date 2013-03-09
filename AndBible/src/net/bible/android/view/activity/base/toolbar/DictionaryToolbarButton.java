package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.book.Book;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class DictionaryToolbarButton extends ToolbarButtonBase implements ToolbarButton {

	private Button mButton;
	private Book mSuggestedDocument;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public DictionaryToolbarButton(View parent) {
        mButton = (Button)parent.findViewById(R.id.quickDictionaryChange);

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	onButtonPress();
            }
        });
	}

	private void onButtonPress() {
    	CurrentPageManager.getInstance().setCurrentDocument(mSuggestedDocument);
	}

	public void update() {
        mSuggestedDocument = ControlFactory.getInstance().getDocumentControl().getSuggestedDictionary();
        helper.updateQuickButton(mSuggestedDocument, mButton, canShow());
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
