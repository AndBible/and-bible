package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;

import org.crosswire.jsword.book.Book;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

//TODO do not inherit from button - see: http://stackoverflow.com/questions/8369504/why-so-complex-to-set-style-from-code-in-android
public class DictionaryToolbarButton implements ToolbarButton {

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
		return mSuggestedDocument!=null && ToolbarButtonHelper.numButtonsToShow()>=3;
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
