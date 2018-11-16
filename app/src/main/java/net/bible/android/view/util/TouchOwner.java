package net.bible.android.view.util;

import android.view.View;

/** primarily to prevent long-touch being handled while dragging a separator on v slow mobiles
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class TouchOwner {

    private long ownershipTime;
    private View currentOwner;
    
    private static final long MAX_OWNERSHIP_TIME = 20*1000; // 20 secs

    private static final TouchOwner singleton = new TouchOwner();
    
    public static TouchOwner getInstance() {
        return singleton;
    }
    
    public void setTouchOwner(View owner) {
        currentOwner = owner;
        ownershipTime = System.currentTimeMillis();
    }
    public void releaseOwnership(View owner) {
        currentOwner = null;
    }
    
    public boolean isTouchOwned() {
        if (currentOwner==null) {
            // Not owned
            return false;
        } else if (System.currentTimeMillis()-ownershipTime>MAX_OWNERSHIP_TIME) {
            // Ownership timed out
            currentOwner = null;
            return false;
        } else {
            // is owned
            return true;
        }
    }

    
}
