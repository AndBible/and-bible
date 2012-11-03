package net.bible.android.view.activity.base.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.passage.PassageEvent;
import net.bible.android.control.event.passage.PassageEventListener;
import net.bible.android.control.event.passage.PassageEventManager;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CurrentPageToolbarButton implements ToolbarButton {

	private Button mButton;
	private String mCurrentPageTitle;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public CurrentPageToolbarButton(View parent) {
        mButton = (Button)parent.findViewById(R.id.titlePassage);

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	onButtonPress();
            }
        });

        // listen for verse change events
        PassageEventManager.getInstance().addPassageEventListener(new PassageEventListener() {
			@Override
			public void pageDetailChange(PassageEvent event) {
				update();
			}
		});
	}

	private void onButtonPress() {
		// load Document selector
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
    	Intent pageHandlerIntent = new Intent(currentActivity, CurrentPageManager.getInstance().getCurrentPage().getKeyChooserActivity());
    	currentActivity.startActivityForResult(pageHandlerIntent, 1);
	}

	public void update() {
        mCurrentPageTitle = ControlFactory.getInstance().getPageControl().getCurrentPageTitle();

        // must do ui update in ui thread
		mButton.post(new Runnable() {
			@Override
			public void run() {
		        helper.updateButtonText(mCurrentPageTitle, mButton);
			}
		});
	}

	@Override
	public boolean canShow() {
		return mCurrentPageTitle!=null;
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
