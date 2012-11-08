package net.bible.android.view.activity.readingplan.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonHelper;
import net.bible.android.view.activity.readingplan.DailyReadingList;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CurrentDayToolbarButton implements ToolbarButton {

	private Button mButton;
	private String mCurrentPageTitle;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public CurrentDayToolbarButton(View parent) {
        mButton = (Button)parent.findViewById(R.id.titlePassage);

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	onButtonPress();
            }
        });
	}

	private void onButtonPress() {
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		Intent pageHandlerIntent = new Intent(currentActivity, DailyReadingList.class);
		currentActivity.startActivityForResult(pageHandlerIntent, 1);
		currentActivity.finish();
	}

	public void update() {
        final String title = ControlFactory.getInstance().getReadingPlanControl().getCurrentDayDescription();
        // must do ui update in ui thread
		mButton.post(new Runnable() {
			@Override
			public void run() {
		        helper.updateButtonText(title, mButton);
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
