package net.bible.android.view.activity.navigation.genbookmap;

import java.util.List;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentMapPage;

import org.crosswire.jsword.passage.Key;

import android.util.Log;

/** show a key list and allow to select item
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ChooseMapKey extends ChooseKeyBase {

	private static final String TAG = "ChooseMapKey";
	
	@Override
	protected Key getCurrentKey() {
		
		return getCurrentMapPage().getKey();
	}

	@Override
	protected List<Key> getKeyList() {
		return getCurrentMapPage().getCachedGlobalKeyList();
	}

	@Override
    protected void itemSelected(Key key) {
    	try {
    		getCurrentMapPage().setKey(key);
    	} catch (Exception e) {
    		Log.e(TAG, "error on select of gen book key", e);
    	}
    }

	private CurrentMapPage getCurrentMapPage() {
    	return ControlFactory.getInstance().getCurrentPageControl().getCurrentMap();
    }
}
