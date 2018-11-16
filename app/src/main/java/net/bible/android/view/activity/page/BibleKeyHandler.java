package net.bible.android.view.activity.page;

import android.util.Log;
import android.view.KeyEvent;

import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;

import javax.inject.Inject;

/** KeyEvent.KEYCODE_DPAD_LEFT was being swallowed by the BibleView after scrolling down (it gained focus)
 * so this class implements common key handling both for BibleView and MainBibleActivity
 *   
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class BibleKeyHandler {
    
    // prevent too may scroll events causing multi-page changes
    private long lastHandledDpadEventTime = 0;

    private final ActiveWindowPageManagerProvider activeWindowPageManagerProvider;

    private static final String TAG = "BibleKeyHandler";

    @Inject
    public BibleKeyHandler(ActiveWindowPageManagerProvider activeWindowPageManagerProvider) {
        this.activeWindowPageManagerProvider = activeWindowPageManagerProvider;
    }
    
    /** handle DPAD keys
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            Log.d(TAG, "D-Pad");
            // prevent too may scroll events causing multi-page changes
            if (event.getEventTime()-lastHandledDpadEventTime>1000) {
                if (keyCode==KeyEvent.KEYCODE_DPAD_RIGHT) {
                    activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().next();
                } else {
                    activeWindowPageManagerProvider.getActiveWindowPageManager().getCurrentPage().previous();
                }
                lastHandledDpadEventTime = event.getEventTime();
                return true;
            }
        }
        return false;
    }


}
