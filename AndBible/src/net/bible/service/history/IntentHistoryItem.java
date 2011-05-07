package net.bible.service.history;

import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.page.MainBibleActivity;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;


public class IntentHistoryItem implements HistoryItem {

	private String description;
	private Intent intent;
	
	private static final String TAG = "IntentHistoryItem"; 
	
	public IntentHistoryItem(String description, Intent intent) {
		this.description = description;
		this.intent = intent;
		
		// prevent re-add of intent to history if reverted to
//		intent.putExtra(HISTORY_INTENT, true);
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
	public String getDescription() {
		return description;
	}

	@Override
	public void revertTo() {
		Log.d(TAG, "Revert to history item:"+description);
		Activity currentActivity = CurrentActivityHolder.getInstance().getCurrentActivity();

		// start activity chosen from activity
		currentActivity.startActivity(intent);
		
		// finish current activity
		if (!(currentActivity instanceof MainBibleActivity)) {
			currentActivity.finish();
		}

	}
}
