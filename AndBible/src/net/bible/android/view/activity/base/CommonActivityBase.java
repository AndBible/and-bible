package net.bible.android.view.activity.base;

import android.util.Log;
import net.bible.service.history.HistoryManager;

public class CommonActivityBase {

	private boolean integrateWithHistoryManager;
	
	private long lastBackNavTime;
	
	private static long MIN_BACK_SEPERATION_MILLIS = 500;

	private static String TAG = "CommonActivityBase";
	
    /**
     * about to change activity so tell the HistoryManager so it can register the old activity in its list
     */
	protected void beforeStartActivity() {
		if (integrateWithHistoryManager) {
			HistoryManager.getInstance().beforePageChange();
		}
	}

	public boolean goBack() {
		long prevBackNavTime = lastBackNavTime;
		lastBackNavTime = System.currentTimeMillis();
		if (lastBackNavTime-prevBackNavTime<MIN_BACK_SEPERATION_MILLIS) {
			// swallow back key if it seems like a phantom repeat to prevent history item jumping
			return true;
		} else if (integrateWithHistoryManager && HistoryManager.getInstance().canGoBack()) {
			Log.d(TAG, "Go back");
			HistoryManager.getInstance().goBack();
			return true;
		} else {
			return false;
		}
	}

	public boolean isIntegrateWithHistoryManager() {
		return integrateWithHistoryManager;
	}

	public void setIntegrateWithHistoryManager(boolean integrateWithHistoryManager) {
		this.integrateWithHistoryManager = integrateWithHistoryManager;
	}
}
