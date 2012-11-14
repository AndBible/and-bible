package net.bible.android.view.activity.readingplan.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonHelper;
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CurrentReadingPlanToolbarButton implements ToolbarButton {

	private Button mButton;
	
	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public CurrentReadingPlanToolbarButton(View parent) {
        mButton = (Button)parent.findViewById(R.id.titleDocument);

        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	onButtonPress();
            }
        });
	}

	/** 
	 * load Reading Plan selector
	 */
	private void onButtonPress() {
		Activity readingPlanActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		Intent docHandlerIntent = new Intent(readingPlanActivity, ReadingPlanSelectorList.class);
    	readingPlanActivity.startActivityForResult(docHandlerIntent, 1);
    	readingPlanActivity.finish();
	}

	public void update() {
        String title = ControlFactory.getInstance().getReadingPlanControl().getShortTitle();
        helper.updateButtonText(title, mButton);
	}

	@Override
	public boolean canShow() {
		return true;
	}

	@Override
	public int getPriority() {
		return 1;
	}

	@Override
	public void setEnoughRoomInToolbar(boolean isRoom) {
		// always enough room for this button		
	}
}
