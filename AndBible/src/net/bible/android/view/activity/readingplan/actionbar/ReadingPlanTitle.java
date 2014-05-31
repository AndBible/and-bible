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
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ReadingPlanTitle extends Title {

	public void addToBar(ActionBar actionBar, final Activity activity) {
		super.addToBar(actionBar, activity);

	}

	@Override
	protected String[] getDocumentTitleParts() {
		String title = ControlFactory.getInstance().getReadingPlanControl().getShortTitle();
		return getTwoTitleParts(title, false);
	}

	@Override
	protected String[] getPageTitleParts() {
		String planDayDesc = ControlFactory.getInstance().getReadingPlanControl().getCurrentDayDescription();
		return getTwoTitleParts(planDayDesc, true);
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
