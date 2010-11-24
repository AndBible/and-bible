package net.bible.android.view.activity.page;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.history.HistoryManager;
import android.util.Log;
import android.view.KeyEvent;

/** KeyEvent.KEYCODE_DPAD_LEFT was being swallowed by the BibleView after scrolling down (it gained focus)
 * so this class implements common key handling both for BibleView and MainBibleActivity
 *   
 * @author denha1m
 *
 */
public class BibleKeyHandler {
	
	// prevent too may scroll events causing multi-page changes
	private long lastHandledDpadEventTime = 0;

	private static final BibleKeyHandler singleton = new BibleKeyHandler();
	
	private static final String TAG = "BibleKeyHandler";
	
	public static BibleKeyHandler getInstance() {
		return singleton;
	}
	private BibleKeyHandler() {}
	
	/** handle DPAD keys
	 */
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.d(TAG, "Keycode:"+keyCode);
		if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			Log.d(TAG, "D-Pad");
			// prevent too may scroll events causing multi-page changes
			if (event.getEventTime()-lastHandledDpadEventTime>1000) {
				if (keyCode==KeyEvent.KEYCODE_DPAD_RIGHT) {
					CurrentPageManager.getInstance().getCurrentPage().next();
				} else {
					CurrentPageManager.getInstance().getCurrentPage().previous();
				}
				lastHandledDpadEventTime = event.getEventTime();
				return true;
			}
		}
		return false;
	}


}
