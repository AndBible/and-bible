package net.bible.android.view.activity.readingplan.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonBase;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonHelper;
import net.bible.android.view.activity.readingplan.DailyReadingList;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

/**
 * Show current day number in toolbar

 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentDayToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public CurrentDayToolbarButton(View parent) {
        super(parent, R.id.titlePassage);
	}

	@Override
	protected void onButtonPress() {
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		Intent pageHandlerIntent = new Intent(currentActivity, DailyReadingList.class);
		currentActivity.startActivityForResult(pageHandlerIntent, 1);
		currentActivity.finish();
	}

	public void update() {
		super.update();
		
        final String title = ControlFactory.getInstance().getReadingPlanControl().getCurrentDayDescription();
        // must do ui update in ui thread
		getButton().post(new Runnable() {
			@Override
			public void run() {
		        helper.updateButtonText(title, getButton());
			}
		});
	}

	@Override
	public boolean canShow() {
		return true;
	}

	@Override
	public int getPriority() {
		return 1;
	}
}
