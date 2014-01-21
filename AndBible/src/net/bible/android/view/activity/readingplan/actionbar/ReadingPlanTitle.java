package net.bible.android.view.activity.readingplan.actionbar;

import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.actionbar.Title;
import net.bible.android.view.activity.readingplan.DailyReadingList;
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList;
import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.ActionBar;

/** 
 * Show current verse/key and document on left of actionBar
 */
public class ReadingPlanTitle extends Title {

	
	public void addToBar(ActionBar actionBar, final Activity activity) {
		super.addToBar(actionBar, activity);

	}

	@Override
	protected String getDocumentTitle() {
		return ControlFactory.getInstance().getReadingPlanControl().getShortTitle();
	}

	@Override
	protected String getPageTitle() {
		return ControlFactory.getInstance().getReadingPlanControl().getCurrentDayDescription();
	}

	@Override
	protected void onDocumentTitleClick() {
		Activity readingPlanActivity = getActivity();
		Intent docHandlerIntent = new Intent(readingPlanActivity, ReadingPlanSelectorList.class);
    	readingPlanActivity.startActivityForResult(docHandlerIntent, 1);
    	readingPlanActivity.finish();
	}

	@Override
	protected void onPageTitleClick() {
		Activity currentActivity = getActivity();
		Intent pageHandlerIntent = new Intent(currentActivity, DailyReadingList.class);
		currentActivity.startActivityForResult(pageHandlerIntent, 1);
		currentActivity.finish();
	}
	
}
