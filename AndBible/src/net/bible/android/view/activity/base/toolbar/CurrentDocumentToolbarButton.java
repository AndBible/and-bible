package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.navigation.ChooseDocument;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CurrentDocumentToolbarButton implements ToolbarButton {

	private Button mButton;
	private String mCurrentDocumentTitle;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public CurrentDocumentToolbarButton(View parent) {
        mButton = (Button)parent.findViewById(R.id.titleDocument);

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	onButtonPress();
            }
        });
	}

	private void onButtonPress() {
		// load Document selector
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
    	Intent docHandlerIntent = new Intent(currentActivity, ChooseDocument.class);
    	currentActivity.startActivityForResult(docHandlerIntent, 1);
	}

	public void update() {
        mCurrentDocumentTitle = ControlFactory.getInstance().getPageControl().getCurrentDocumentTitle();
        helper.updateButtonText(mCurrentDocumentTitle, mButton);
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
