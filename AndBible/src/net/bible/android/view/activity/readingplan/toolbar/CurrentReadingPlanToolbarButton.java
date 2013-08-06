package net.bible.android.view.activity.readingplan.toolbar;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonBase;
import net.bible.android.view.activity.base.toolbar.ToolbarButtonHelper;
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

/**
 * Show reading plan code in toolbar e.g. y1ot1nt1
 *  
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class CurrentReadingPlanToolbarButton extends ToolbarButtonBase<Button> implements ToolbarButton {

	private ToolbarButtonHelper helper = new ToolbarButtonHelper();
	
	public CurrentReadingPlanToolbarButton(View parent) {
        super(parent, R.id.titleDocument);
	}

	/** 
	 * load Reading Plan selector
	 */
	@Override
	protected void onButtonPress() {
		Activity readingPlanActivity = CurrentActivityHolder.getInstance().getCurrentActivity();
		Intent docHandlerIntent = new Intent(readingPlanActivity, ReadingPlanSelectorList.class);
    	readingPlanActivity.startActivityForResult(docHandlerIntent, 1);
    	readingPlanActivity.finish();
	}

	public void update() {
        final String title = ControlFactory.getInstance().getReadingPlanControl().getShortTitle();
        
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
