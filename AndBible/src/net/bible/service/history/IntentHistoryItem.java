package net.bible.service.history;

import net.bible.android.view.activity.base.CurrentActivityHolder;
import android.content.Intent;


public class IntentHistoryItem implements HistoryItem {

	private String description;
	private Intent intent;
	
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
		CurrentActivityHolder.getInstance().getCurrentActivity().startActivity(intent);
	}
}
