package net.bible.android.view.activity.navigation.genbookmap;

import android.util.Log;

import net.bible.android.control.page.CurrentGeneralBookPage;

import org.crosswire.jsword.passage.Key;

import java.util.List;

/** show a list of keys and allow to select an item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChooseGeneralBookKey extends ChooseKeyBase {

    private static final String TAG = "ChooseGeneralBookKey";
    
    @Override
    protected Key getCurrentKey() {
        
        return getCurrentGeneralBookPage().getKey();
    }

    @Override
    protected List<Key> getKeyList() {
        return getCurrentGeneralBookPage().getCachedGlobalKeyList();
    }

    @Override
    protected void itemSelected(Key key) {
        try {
            getCurrentGeneralBookPage().setKey(key);
        } catch (Exception e) {
            Log.e(TAG, "error on select of gen book key", e);
        }
    }

    private CurrentGeneralBookPage getCurrentGeneralBookPage() {
        return getActiveWindowPageManagerProvider().getActiveWindowPageManager().getCurrentGeneralBook();
    }
}
