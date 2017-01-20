package net.bible.android.view.activity.readingplan.actionbar;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.ActionBar;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.view.activity.base.actionbar.Title;
import net.bible.android.view.activity.readingplan.DailyReadingList;
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList;

import javax.inject.Inject;

/** 
 * Show current verse/key and document on left of actionBar
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class ReadingPlanTitle extends Title {

	private final ReadingPlanControl readingPlanControl;

	@Inject
	public ReadingPlanTitle(ReadingPlanControl readingPlanControl) {
		this.readingPlanControl = readingPlanControl;
	}

	public void addToBar(ActionBar actionBar, final Activity activity) {
		super.addToBar(actionBar, activity);

	}

	@Override
	protected String[] getDocumentTitleParts() {
		String title = readingPlanControl.getShortTitle();
		return getTwoTitleParts(title, false);
	}

	@Override
	protected String[] getPageTitleParts() {
		String planDayDesc = readingPlanControl.getCurrentDayDescription();
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
