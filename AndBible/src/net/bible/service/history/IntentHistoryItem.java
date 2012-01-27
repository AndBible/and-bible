package net.bible.service.history;

import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.service.common.CommonUtils;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;

/**
 * Any item in the History list that is not related to the main bible activity view e.g. search results etc
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class IntentHistoryItem implements HistoryItem {

	private CharSequence description;
	private Intent intent;
	
	private static final String TAG = "IntentHistoryItem"; 
	
	public IntentHistoryItem(CharSequence description, Intent intent) {
		this.description = description;
		this.intent = intent;
		
		// prevent re-add of intent to history if reverted to
//		intent.putExtra(HISTORY_INTENT, true);
	}

	public IntentHistoryItem(int descriptionId, Intent intent) {
		this(CommonUtils.getResourceString(descriptionId), intent);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o==null || !(o instanceof IntentHistoryItem)) {
			return false;
		}
		if (o==this) {
			return true;
		}
		
		IntentHistoryItem oihs = (IntentHistoryItem)o;
		// assumes intent exists
		return intent.equals(oihs.intent);
	}

	@Override
	public CharSequence getDescription() {
		return description;
	}

	@Override
	public void revertTo() {
		Log.d(TAG, "Revert to history item:"+description);
		// need to get current activity and call startActivity on that 
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();

		// start activity chosen from activity
		currentActivity.startActivity(intent);
	}
}
