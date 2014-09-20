package net.bible.android.view.activity.base;

import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent;
import net.bible.service.history.HistoryManager;
import android.util.Log;
import de.greenrobot.event.EventBus;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class HistoryTraversal {

	private boolean integrateWithHistoryManager;
	
	private long lastBackNavTime;
	
	private static long MIN_BACK_SEPERATION_MILLIS = 500;

	private static String TAG = "HistoryTraversal";
	
    /**
     * about to change activity so tell the HistoryManager so it can register the old activity in its list
     */
	protected void beforeStartActivity() {
		if (integrateWithHistoryManager) {
			EventBus.getDefault().post(new BeforeCurrentPageChangeEvent());
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
