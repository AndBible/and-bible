package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.navigation.ChooseDocument;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

public class CurrentDocumentToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private String mCurrentDocumentTitle;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public CurrentDocumentToolbarButton(View parent) {
        super(parent, R.id.titleDocument);
	}

	@Override
	protected void onButtonPress() {
		// load Document selector
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
    	Intent docHandlerIntent = new Intent(currentActivity, ChooseDocument.class);
    	currentActivity.startActivityForResult(docHandlerIntent, 1);
	}

	public void update() {
        mCurrentDocumentTitle = ControlFactory.getInstance().getPageControl().getCurrentDocumentTitle();
        
        final String title = mCurrentDocumentTitle;
		getButton().post(new Runnable() {
			@Override
			public void run() {
		        helper.updateButtonText(title, getButton());
			}
		});
	}

	@Override
	public boolean canShow() {
		return mCurrentDocumentTitle!=null;
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
