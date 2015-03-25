package net.bible.android.view.activity.page;

import net.bible.android.control.ControlFactory;
import android.util.Log;
import android.view.KeyEvent;

/** KeyEvent.KEYCODE_DPAD_LEFT was being swallowed by the BibleView after scrolling down (it gained focus)
 * so this class implements common key handling both for BibleView and MainBibleActivity
 *   
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
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
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			Log.d(TAG, "D-Pad");
			// prevent too may scroll events causing multi-page changes
			if (event.getEventTime()-lastHandledDpadEventTime>1000) {
				if (keyCode==KeyEvent.KEYCODE_DPAD_RIGHT) {
					ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().next();
				} else {
					ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().previous();
				}
				lastHandledDpadEventTime = event.getEventTime();
				return true;
			}
		}
		return false;
	}


}
